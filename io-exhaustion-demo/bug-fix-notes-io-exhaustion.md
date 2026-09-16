# Thread-pool exhaustion from a blocking downstream call

A third "healthy but wrong" bug, in the same family as
[`resource-exhaustion-demo`](../resource-exhaustion-demo/bug-fix-notes-resource-exhaustion.md)'s
memory and CPU ones: nothing throws, nothing looks wrong in a code review,
and the failure only shows up under concurrent load. This time it's neither
CPU nor memory that runs out — it's **threads**. A service makes a
synchronous call to a slow downstream dependency, and every concurrent
caller ties up one of the app's own request-handling threads for the full
duration of that call.

## The bug: blocking the request thread on a downstream call

`buggy/SlowDownstreamService.checkAvailability(long delayMs)` calls a
downstream "inventory" service to check availability, the way a service
layer usually does — build a request, send it, read the response:

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + downstream.getPort() + "/availability?delayMs=" + delayMs))
        .GET()
        .build();
httpClient.send(request, HttpResponse.BodyHandlers.ofString());
```

`httpClient.send(...)` is synchronous — the calling thread doesn't return
until the downstream response arrives. Call it once and it's fast and
harmless. Call it concurrently from enough requests at once, and every one
of those callers occupies a Tomcat request-handling thread for the entire
downstream round trip. Once every thread in the pool is stuck waiting, no
new request — to this endpoint or **any other endpoint in the app** — can be
accepted until one frees up.

(This demo hosts its own tiny "downstream" HTTP server in-process —
`shared/DownstreamAvailabilityServer` — so the bug is reproducible without
Docker or a real external dependency. It runs on its own port and its own
thread pool, so it's never itself the resource under test — only what the
*calling* app does while waiting matters here.)

## The fix: bulkhead — offload to a dedicated executor

`fixed/AsyncDownstreamService.checkAvailabilityAsync` makes the identical
blocking call, but on a dedicated, bounded executor instead of the Tomcat
thread:

```java
@Async("downstreamExecutor")
public CompletableFuture<Map<String, Object>> checkAvailabilityAsync(long delayMs) {
    ...
    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    ...
    return CompletableFuture.completedFuture(Map.of(...));
}
```

with the controller returning the future directly (Spring MVC's native
async support — no WebFlux, no new dependency):

```java
@GetMapping("/api/fixed/inventory/check")
public CompletableFuture<ResponseEntity<Map<String, Object>>> check(@RequestParam(defaultValue = "300") long delayMs) {
    return service.checkAvailabilityAsync(delayMs).thenApply(ResponseEntity::ok);
}
```

The Tomcat thread returns the instant the async task is submitted — it
never waits on the downstream call at all. The blocking wait still has to
happen *somewhere*, but now it happens on `downstreamExecutor`
(`fixed/DownstreamExecutorConfig`), a pool dedicated to this one purpose and
isolated from the general-purpose request-handling pool. This is the
bulkhead pattern: a slow dependency can degrade its own bulkhead, but it
can't take the rest of the app down with it.

The dedicated executor is also sized *larger* than Tomcat's own pool (20 vs
10 threads here) — on purpose. A thread sitting in `downstreamExecutor` is
blocked waiting on I/O, not spending CPU, so it's cheap to size an I/O-bound
pool well above a CPU-bound request-handling pool.

## Reproducing it for real

`server.tomcat.threads.max=10` (plus `server.tomcat.accept-count=10`) is set
in `application.properties` specifically so this is reproducible with modest
curl concurrency instead of needing hundreds of real clients — same idea as
the `-Xmx64m` constraint in the OOM demo.

```bash
./mvnw spring-boot:run &

# single calls — both fine in isolation
curl "http://localhost:8080/api/buggy/inventory/check?delayMs=300"
# {"delayMs":300,"elapsedMs":464,"servedByThread":"http-nio-8080-exec-4","available":true}

curl "http://localhost:8080/api/fixed/inventory/check?delayMs=300"
# {"delayMs":300,"elapsedMs":309,"servedByThread":"downstream-exec-1","available":true}
```

Note `servedByThread` even on a single call — the buggy response was served
directly by a Tomcat worker thread, the fixed one by the dedicated executor.
That's the whole bug in one field.

Now push concurrency — plain sequential curl loops (as used in the CPU/memory
demos) won't reproduce this; you need `xargs -P` to fire requests in parallel:

```bash
# BUGGY: 20 concurrent, delayMs=1000, Tomcat pool max=10
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
  "http://localhost:8080/api/buggy/inventory/check?delayMs=1000"
# 10 requests finish around ~1.35-1.4s, the other 10 around ~2.3-2.4s
# total wall time ~2.5s for 20 requests that should each take ~1s

# FIXED: same 20 concurrent, delayMs=1000
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null -w "%{http_code} %{time_total}\n" \
  "http://localhost:8080/api/fixed/inventory/check?delayMs=1000"
# all 20 finish together around ~1.1-1.2s — one batch, not two
```

The buggy version serializes into two batches of 10 (exactly the Tomcat pool
size) — the second batch of callers is queued behind the first, waiting for
a thread to free up. The fixed version's 20-thread dedicated executor easily
absorbs all 20 at once. Pushing concurrency higher (e.g. 30) on the buggy
endpoint just adds more batches — three batches of 10, ~3s total, still no
exception, just growing latency, until eventually a caller's own timeout (or
this app's `accept-count` backlog) gives up first.

`ThreadStarvationReproductionTest` proves the same thing under a JUnit
assertion (smaller pool sizes, so it runs fast): run it with

```bash
./mvnw test -Pthread-starvation-demo
```

It's excluded from the default `mvn test` run because — like the JFR-based
checks below — it boots a real embedded Tomcat and asserts wall-clock timing
over real sockets under concurrency, which is more runner-jitter-sensitive
than the rest of the suite.

## Telling it apart from CPU/memory exhaustion in the wild

Same shape as the [CPU/memory signal table](../resource-exhaustion-demo/bug-fix-notes-resource-exhaustion.md#telling-the-two-apart-in-the-wild),
extended with this bug's column:

| Signal | I/O-bound (this bug) |
|---|---|
| `top -H -p <pid>` | several threads idle/waiting, none pegged near 100% |
| `process_cpu_usage` (Actuator/Prometheus) | normal or low |
| `jvm_memory_used_bytes` / GC pause count | flat |
| Profiler (JFR/flame graph) | nothing dominant — a blocked thread isn't sampled, so `hot-methods` looks clean even while requests are queued |
| What actually fails | latency balloons (batches of `requests / poolSize`), no exception, no crash — until something upstream (a client-side timeout, or this app's own accept-queue) gives up first |

**A thread blocked waiting is invisible to CPU sampling** — that's the same
point the resource-exhaustion demo's JFR notes make about I/O in general,
and it's exactly why this bug can hide from a profiler that only looks at
`hot-methods`.

## Diagnosing it locally with JFR

### 1. Attach and generate load

```bash
./mvnw spring-boot:run &
jps -l

jcmd <pid> JFR.start name=buggy duration=300s filename=/tmp/buggy-io.jfr settings=profile
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null "http://localhost:8080/api/buggy/inventory/check?delayMs=1000"
jcmd <pid> JFR.dump name=buggy filename=/tmp/buggy-io.jfr
jcmd <pid> JFR.stop name=buggy

jcmd <pid> JFR.start name=fixed duration=300s filename=/tmp/fixed-io.jfr settings=profile
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null "http://localhost:8080/api/fixed/inventory/check?delayMs=1000"
jcmd <pid> JFR.dump name=fixed filename=/tmp/fixed-io.jfr
jcmd <pid> JFR.stop name=fixed
```

(Two separate recordings, one per endpoint, so the two thread pools' events
don't mix.)

### 2. The surprise: this isn't `jdk.SocketRead`

The naive guess is to look for `jdk.SocketRead`/`jdk.SocketWrite` (the same
events `jfr-analyze`'s I/O section already covers for real file/socket
work). On this recording they're **empty**:

```bash
jfr summary /tmp/buggy-io.jfr | grep -iE "socket|park"
#  jdk.ThreadPark                            170          6400
#  jdk.SocketRead                              0             0
#  jdk.SocketWrite                             0             0
```

That's because `java.net.http.HttpClient`'s "synchronous" `send()` isn't
actually a classic blocking socket read under the hood — it's built on the
same async NIO internals as the rest of the client, and the calling thread
just blocks on a `CompletableFuture.get()` while the async machinery does
the real I/O. JFR sees that as a **park**, not a socket read:

```bash
JDK21_JFR=/usr/lib/jvm/java-21-openjdk-amd64/bin/jfr   # jfr view/print with stacks needs 21+
$JDK21_JFR print --events jdk.ThreadPark --stack-depth 8 /tmp/buggy-io.jfr | grep -A8 "http-nio"
```

```
eventThread = "http-nio-8080-exec-8" (javaThreadId = 60)
stackTrace = [
  jdk.internal.misc.Unsafe.park(boolean, long)
  java.util.concurrent.locks.LockSupport.park(Object) line: 221
  java.util.concurrent.CompletableFuture$Signaller.block() line: 1864
  java.util.concurrent.ForkJoinPool.unmanagedBlock(ForkJoinPool$ManagedBlocker) line: 3780
  java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool$ManagedBlocker) line: 3725
  java.util.concurrent.CompletableFuture.waitingGet(boolean) line: 1898
  java.util.concurrent.CompletableFuture.get() line: 2072
  jdk.internal.net.http.HttpClientImpl.send(HttpRequest, HttpResponse$BodyHandler) line: 934
```

Every one of the buggy recording's 10 Tomcat worker threads
(`http-nio-8080-exec-1` through `-10` — the full pool) shows this exact
stack. **That's the actual proof of the bug**: the whole pool is parked
inside `HttpClientImpl.send`, waiting on the downstream call.

### 3. Same query against the fixed recording — different thread names

```bash
$JDK21_JFR print --events jdk.ThreadPark --stack-depth 8 /tmp/fixed-io.jfr | grep -A8 "downstream-exec"
```

Identical stack shape, but the `eventThread` is `downstream-exec-N` instead
of `http-nio-8080-exec-N`. That thread-name contrast — same blocking call,
different pool — **is the proof the fix worked**: the Tomcat threads in this
recording show almost no park activity, because they returned immediately
after handing the work to the dedicated executor.

### 4. The downstream simulator's own `jdk.ThreadSleep`

The self-hosted downstream server (`DownstreamAvailabilityServer`) simulates
its own processing time with a plain `Thread.sleep(delayMs)` — that's a
genuinely different signal, `jdk.ThreadSleep` rather than `jdk.ThreadPark`,
on yet another thread pool (its own cached executor, named `pool-N-thread-M`):

```bash
$JDK21_JFR print --events jdk.ThreadSleep --stack-depth 4 /tmp/buggy-io.jfr | grep -A5 "eventThread"
```

```
eventThread = "pool-2-thread-17" (javaThreadId = 125)
stackTrace = [
  java.lang.Thread.afterSleep(ThreadSleepEvent) line: 474
  java.lang.Thread.sleep(long) line: 512
  com.example.ioexhaustiondemo.shared.DownstreamAvailabilityServer.handle(HttpExchange) line: 46
```

Three different signals, three different thread pools, one recording:
`jdk.ThreadPark` on the caller (Tomcat or the dedicated executor, depending
on buggy vs fixed), `jdk.ThreadSleep` on the downstream simulator, and
`hot-methods`/`thread-cpu-load` coming back unremarkable throughout, because
every thread involved is *waiting*, not computing.

## Rule of thumb

Like the CPU and memory bugs, this isn't a logic error — the code is
correct and fast in isolation. The question that catches it in review is
"what does this thread do while it waits for that call to come back?" A
synchronous call on a request thread is fine until concurrency exceeds the
thread pool; the fix isn't making the downstream call faster, it's making
sure the wait doesn't hold a request-handling thread hostage.
