# Resource exhaustion: memory (OOM) and CPU

Two "healthy but wrong" bugs, same shape: nothing throws an exception at
small scale, nothing looks wrong in a code review, and the failure only
shows up once volume grows. One exhausts **memory**, the other exhausts
**CPU** — and it's worth being able to tell which one you're looking at
before you reach for a fix.

## Bug 1: OutOfMemoryError from materializing a whole result set

`memory/buggy/MemoryHogService.buildReport(int rowCount)` is the classic
real-world case: a query (or, here, a stand-in loop) that maps every row
into a Java object and returns them all as one `List`. This is exactly what
`SELECT * FROM huge_table` mapped straight into `List<Entity>` looks like
in a repository method with no `LIMIT`/pagination.

```java
public List<byte[]> buildReport(int rowCount) {
    List<byte[]> rows = new ArrayList<>(rowCount);
    for (int i = 0; i < rowCount; i++) {
        rows.add(new byte[100_000]); // one "row"
    }
    return rows;
}
```

Call it with `rows=100` and it's instant and harmless. Call it with
`rows=2_000_000` (or just let production data grow) and every one of those
rows is alive in memory **at the same time**, for the entire life of the
request. There's no line of code here that's "wrong" in isolation — it's
wrong only in aggregate, which is exactly why it survives code review.

### The fix: stream, don't accumulate

`memory/fixed/StreamingReportService.streamReport(int rowCount, OutputStream out)`
does the same total amount of work — same number of rows, same bytes
written — but writes and discards each row instead of appending it to a
list:

```java
public long streamReport(int rowCount, OutputStream out) throws IOException {
    long totalBytes = 0;
    for (int i = 0; i < rowCount; i++) {
        byte[] row = new byte[100_000];
        out.write(row);
        totalBytes += row.length;
    }
    out.flush();
    return totalBytes;
}
```

Wired up via `StreamingResponseBody` so the servlet container streams the
response body directly instead of buffering it:

```java
@GetMapping(value = "/api/fixed/report", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public StreamingResponseBody report(@RequestParam int rows) {
    return out -> service.streamReport(rows, out);
}
```

No reference to a row survives past the `write()` call, so it's eligible
for GC immediately. Peak memory becomes roughly **one row's worth**,
regardless of how many rows you ask for — the whole point being that total
work and peak memory are two different numbers, and the bug conflates them.

The general fix pattern here also covers the DB case directly: page/batch
the query (`Pageable`, keyset pagination, or a `Stream<T>`/cursor-based
repository method) instead of loading the full result set, and process each
page before fetching the next.

### Reproducing it for real

`OomReproductionTest` is a plain unit test (no Spring context — a booted
`ApplicationContext` alone can need well over 64MB, which would make a
64MB heap limit meaningless as a test). It's excluded from the normal
`mvn test` run because under a normal-sized heap it wouldn't prove
anything — run it with the small-heap profile instead:

```bash
./mvnw test -Poom-demo
```

This runs *only* `OomReproductionTest`, forked into its own JVM with
`-Xmx64m`. At ~100KB/row, `MemoryHogService.buildReport(2_000)` throws a
real `java.lang.OutOfMemoryError: Java heap space` around row ~640 — it
never gets close to 2,000. `StreamingReportService.streamReport(2_000, ...)`
writes the same ~200MB of *total* data through the same 64MB heap without
issue, because it's never holding more than one row at a time.

## Bug 2: CPU exhaustion from an accidental O(n²) scan

The other common way "it works in dev, dies in prod" happens without any
exception at all: an algorithm that's quadratic instead of linear.
`cpu/buggy/SlowDuplicateFinder.findDuplicates` finds duplicate order IDs by
comparing every pair:

```java
for (int i = 0; i < orderIds.size(); i++) {
    for (int j = i + 1; j < orderIds.size(); j++) {
        if (orderIds.get(i).equals(orderIds.get(j)) && !duplicates.contains(orderIds.get(i))) {
            duplicates.add(orderIds.get(i));
        }
    }
}
```

It returns the *correct* answer every time — that's what makes it survive
review and small-scale testing. The cost is O(n²): double the input, and
the work is 4x, not 2x. There's no memory growth here (the working set
stays small), the CPU core doing the comparisons just spends longer and
longer doing it.

### The fix: one pass with a HashSet

`cpu/fixed/FastDuplicateFinder.findDuplicates` computes the identical
result in a single O(n) pass:

```java
Set<String> seen = new HashSet<>();
Set<String> duplicates = new LinkedHashSet<>();
for (String orderId : orderIds) {
    if (!seen.add(orderId)) {
        duplicates.add(orderId);
    }
}
```

### Reproducing it

`CpuIntensiveReproductionTest` runs both finders against the identical
8,000-order-ID input (same correctness assertion, `containsExactlyInAnyOrderElementsOf`)
and asserts the buggy version takes at least 5x longer. In practice it's
far more dramatic — hitting the endpoints directly:

```bash
curl "http://localhost:8080/api/buggy/orders/duplicates?count=8000"
# {"duplicatesFound":400,"elapsedMs":1830,"inputSize":8000}

curl "http://localhost:8080/api/fixed/orders/duplicates?count=8000"
# {"duplicatesFound":400,"elapsedMs":3,"inputSize":8000}
```

~600x, for the same 8,000-row input, same correct answer either way. No
exception, no error log — just one request that quietly takes longer as
the table grows, until one day it's the request that times out or pins a
CPU core under load.

## Telling the two apart in the wild

This ties back to the JVM/observability tooling discussed alongside
[`request-latency-tracing-demo`](../request-latency-tracing-demo/bug-fix-notes-request-tracing.md):

| Signal | CPU-bound (Bug 2) | Memory-bound (Bug 1) |
|---|---|---|
| `top -H -p <pid>` | one thread pegged near 100% | thread(s) not necessarily busy |
| `process_cpu_usage` (Actuator/Prometheus) | high, sustained | normal or low |
| `jvm_memory_used_bytes` | flat | climbing, or spiking and not returning |
| `jvm_gc_pause_seconds_count`/duration | flat | climbing — GC working harder to keep up |
| Profiler (JFR/flame graph) | wide bars in *application* methods | wide bars in *GC* threads |
| What actually fails | request gets slow / times out | `OutOfMemoryError`, or the process gets OOM-killed |

Concrete examples of each, beyond the two reproduced here:

- **CPU-intensive**: password hashing (bcrypt) at high cost factor, regex
  with catastrophic backtracking, image/video encoding, JSON parsing of
  huge payloads in a tight loop, sorting/searching a large in-memory
  collection.
- **Memory-intensive**: loading a whole file into a `String`/`byte[]`
  instead of streaming it, an unbounded in-memory cache with no eviction,
  JPA N+1 patterns that eagerly hydrate large object graphs, buffering a
  whole HTTP response before writing it.
- **Often both at once**: sorting a huge in-memory dataset is CPU work
  (comparisons) *and* memory work (holding the whole dataset resident
  while you do it) — check both metrics before assuming which one you're
  chasing.

## Rule of thumb

Neither bug is a logic error — both return the right answer, every time,
at small scale. The question that catches them in review isn't "is this
correct?" but "what happens to this line of code as `n` grows?" — does
peak memory stay flat, and does time stay roughly linear? If either answer
is "no, and there's no bound on `n`," that's the bug, whether or not
anything throws today.
