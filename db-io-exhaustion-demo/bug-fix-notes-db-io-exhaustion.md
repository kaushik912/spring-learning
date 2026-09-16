# DB connection-pool exhaustion from an index-defeating query

A fourth "healthy but wrong" bug, in the same family as
[`resource-exhaustion-demo`](../resource-exhaustion-demo/bug-fix-notes-resource-exhaustion.md)'s
CPU/memory bugs and
[`io-exhaustion-demo`](../io-exhaustion-demo/bug-fix-notes-io-exhaustion.md)'s
thread-pool bug: nothing throws on a single call, nothing looks wrong in a
code review, and it only falls over under concurrent load. This time the
exhausted resource is the **DB connection pool**, and the root cause is a
query that quietly can't use its own index.

## The bug: a function-wrapped predicate defeats the index

`customer_orders` has an index on `customer_email`
(`schema.sql`). `buggy/BuggyOrderRepository` looks up orders by email, but
via a scalar function instead of a plain equality check:

```java
@Query(value = "SELECT * FROM customer_orders WHERE SLOW_EMAIL_MATCH(customer_email, :email)",
       nativeQuery = true)
List<CustomerOrder> findByEmailIgnoreCaseSlow(@Param("email") String email);
```

`SLOW_EMAIL_MATCH` is registered as an H2 `ALIAS` for
`buggy/SlowEmailMatcher.matches(...)`. Because the predicate calls a function
on `customer_email` rather than comparing it directly, H2 can't use
`idx_customer_orders_email` at all — it has to full-scan every row and
invoke the function on each one. This is the same class of bug as
`WHERE UPPER(email) = UPPER(?)` or `WHERE email || '' = ?`: syntactically
harmless, semantically index-breaking.

One honest wrinkle: H2's in-memory storage has no disk I/O to pay for, so a
plain full-scan-and-compare over a few hundred thousand rows is sub-20ms
once the JIT warms up — nowhere near slow enough to exhaust a pool.
`SlowEmailMatcher` adds a small, calibrated, non-eliminable amount of CPU
work per row (`ROUNDS = 1_750` rotate-multiply iterations) to stand in for
the per-row cost a real full scan usually does pay for — decrypting a
column, normalizing data, or reading pages that aren't in cache. It's a
deliberate simplification so the bug reproduces reliably on modest hardware
without needing a multi-hundred-million-row table.

`fixed/FixedOrderRepository` runs the equivalent lookup as a plain
derived-query equality check:

```java
List<CustomerOrder> findByCustomerEmail(String customerEmail);
```

which H2 can (and does) satisfy with an index seek.

## The fix

The fix isn't "make the pool bigger" — it's fixing the query so each
request holds a connection for milliseconds instead of seconds. Both
endpoints share the exact same `HikariDataSource`
(`spring.datasource.hikari.maximum-pool-size=3`,
`connection-timeout=2000ms` — kept small on purpose, same trick as
`io-exhaustion-demo`'s small Tomcat pool, so this reproduces with modest
curl concurrency). The buggy endpoint exhausts that shared pool under load;
the fixed endpoint, hitting the same pool, doesn't come close.

## Reproducing it for real

```bash
./mvnw spring-boot:run &
# waits on startup, then seeds 200,000 rows (~6s) — watch for "Seeded ..." in the log
```

Single calls, warmed up:

```bash
curl -s -o /dev/null -w "%{time_total}s\n" \
  "http://localhost:8080/api/buggy/orders?email=target.customer@example.com"
# ~1.8s

curl -s -o /dev/null -w "%{time_total}s\n" \
  "http://localhost:8080/api/fixed/orders?email=target.customer@example.com"
# ~0.01s
```

Now push concurrency with `xargs -P` (20 requests, pool size 3):

```bash
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
  "http://localhost:8080/api/buggy/orders?email=target.customer@example.com"
```

Actual output from a local run: 3 requests succeed around ~1.9s (the first
batch to grab the pool's 3 connections), 3 more succeed around ~3.7-3.8s
(queued behind the first batch), and the remaining **14 fail with HTTP 500**
after ~2.1s — the pool's `connection-timeout`. The app log shows exactly
why:

```
HikariPool-1 - Connection is not available, request timed out after 2000ms (total=3, active=3, idle=0, waiting=10)
```

Same 20-way concurrency against the fixed endpoint: all 20 succeed, each in
10-60ms — the shared pool of 3 connections is never a bottleneck because
each request returns its connection almost immediately.

```bash
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
  "http://localhost:8080/api/fixed/orders?email=target.customer@example.com"
```

## Telling it apart from CPU/memory/thread exhaustion in the wild

Extends the [signal table](../io-exhaustion-demo/bug-fix-notes-io-exhaustion.md#telling-it-apart-from-cpumemory-exhaustion-in-the-wild)
from the other two demos:

| Signal | DB pool exhaustion (this bug) |
|---|---|
| `top -H -p <pid>` | a few threads pegged doing real work (the scan), the rest idle/waiting for a connection |
| `process_cpu_usage` (Actuator/Prometheus) | elevated, but only on `maximum-pool-size` threads at a time — bounded, not a runaway like the O(n²) demo |
| `jvm_memory_used_bytes` / GC pause count | flat |
| HikariCP metrics (`hikaricp_connections_pending`, `_timeout_total`) | pending count rises, timeout count climbs under load — the most direct signal, if you're scraping it |
| Profiler (JFR/flame graph) | **not** clean — unlike a network wait, this *is* real CPU time in-process; `jdk.ExecutionSample` shows it directly |
| What actually fails | HTTP 500s once `waiting > (pool size)` for longer than `connection-timeout` — not a crash, but a hard client-visible error, not just latency |

The interesting contrast with `io-exhaustion-demo`: that bug was invisible
to a CPU profiler because the thread was genuinely blocked on a socket.
This one *looks* like an I/O wait (a connection-pool timeout) but the root
cause is real, sample-visible CPU work happening inside the JVM (H2 is
in-memory — there's no actual disk or network I/O here). Both symptoms —
threads piling up waiting for a scarce resource — need a different query
against the same recording to diagnose.

## Diagnosing it locally with JFR

### 1. Attach and generate load

```bash
./mvnw spring-boot:run &
jps -l   # find the pid

jcmd <pid> JFR.start name=buggy settings=profile filename=/tmp/db-io-buggy.jfr duration=30s
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null "http://localhost:8080/api/buggy/orders?email=target.customer@example.com"
jcmd <pid> JFR.stop name=buggy

jcmd <pid> JFR.start name=fixed settings=profile filename=/tmp/db-io-fixed.jfr duration=30s
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null "http://localhost:8080/api/fixed/orders?email=target.customer@example.com"
jcmd <pid> JFR.stop name=fixed
```

### 2. Find the slow query: `jdk.ExecutionSample`

```bash
jfr print --events jdk.ExecutionSample --stack-depth 8 /tmp/db-io-buggy.jfr | grep -A8 "SlowEmailMatcher"
```

```
sampledThread = "http-nio-8080-exec-18" (javaThreadId = 73)
state = "STATE_RUNNABLE"
stackTrace = [
  com.example.dbioexhaustiondemo.buggy.SlowEmailMatcher.simulatePerRowCost(String) line: 29
  com.example.dbioexhaustiondemo.buggy.SlowEmailMatcher.matches(String, String) line: 23
  jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Object, Object[]) line: 103
  java.lang.reflect.Method.invoke(Object, Object[]) line: 580
  org.h2.schema.FunctionAlias$JavaMethod.execute(SessionLocal, Expression[], boolean) line: 495
  org.h2.schema.FunctionAlias$JavaMethod.getValue(SessionLocal, Expression[], boolean) line: 345
  org.h2.expression.function.JavaFunction.getValue(SessionLocal) line: 40
  org.h2.expression.Expression.getBooleanValue(SessionLocal) line: 344
]
```

This is the direct proof: most CPU samples on the buggy recording land
inside `SlowEmailMatcher.simulatePerRowCost`, invoked via H2's
`FunctionAlias` machinery — one call per row scanned. The fixed recording
has **zero** samples anywhere near `SlowEmailMatcher`, because
`findByCustomerEmail` never calls it.

### 3. Find the pool exhaustion: `jdk.ThreadPark`

```bash
jfr print --events jdk.ThreadPark --stack-depth 10 /tmp/db-io-buggy.jfr | grep -B4 -A14 "HikariPool.getConnection"
```

```
timeout = 2.00 s
eventThread = "http-nio-8080-exec-19" (javaThreadId = 74)
stackTrace = [
  jdk.internal.misc.Unsafe.park(boolean, long)
  java.util.concurrent.locks.LockSupport.parkNanos(long) line: 410
  java.util.concurrent.LinkedTransferQueue$DualNode.await(Object, long, Object, boolean) line: 452
  java.util.concurrent.LinkedTransferQueue.xfer(Object, long) line: 613
  java.util.concurrent.SynchronousQueue.xfer(Object, long) line: 235
  java.util.concurrent.SynchronousQueue.poll(long, TimeUnit) line: 338
  com.zaxxer.hikari.util.ConcurrentBag.borrow(long, TimeUnit) line: 163
  com.zaxxer.hikari.pool.HikariPool.getConnection(long) line: 160
  com.zaxxer.hikari.pool.HikariPool.getConnection() line: 142
  com.zaxxer.hikari.HikariDataSource.getConnection() line: 127
]
```

`timeout = 2.00 s` lines up exactly with `connection-timeout=2000ms` — this
is a Tomcat worker thread parked inside `HikariPool.getConnection`, waiting
on the pool's internal `SynchronousQueue` for a connection that never frees
up in time. The fixed recording has no `ThreadPark` events with this stack
at all — every request gets a connection immediately.

Two signals, one recording: `jdk.ExecutionSample` names the slow query,
`jdk.ThreadPark` names the resource it's starving.

## Rule of thumb

Like the other three bugs in this family, this isn't a logic error — the
query returns the right rows, just slowly. The catch isn't in the query's
correctness, it's in what an index-defeating predicate costs *other*
requests sharing the same pool. When a DB call under load starts producing
timeouts instead of slow responses, don't reach for a bigger pool first —
profile the query. A bigger pool just lets more slow queries run at once;
it doesn't make any one of them faster.

## Bonus profile: the same wait, but over a real socket (MySQL)

H2 runs in-process, so there's no real network hop to observe — the
`ExecutionSample` story above is the whole picture. Activate the `mysql`
profile (`mysql/MySqlBuggyOrderController` + `MySqlBuggyOrderRepository`,
`schema-mysql.sql`, `application-mysql.properties`) to point the exact same
small-pool-exhaustion setup at a real, already-installed MySQL server
instead, reached over a genuine TCP socket (`localhost:3306` — WSL doesn't
make this "not a socket"; JDBC still goes through the OS network stack).

```bash
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS db_io_exhaustion_demo;"

SPRING_PROFILES_ACTIVE=mysql \
SPRING_DATASOURCE_USERNAME=root \
SPRING_DATASOURCE_PASSWORD=mysqltest@2026 \
./mvnw spring-boot:run &

## In case you want to capture more frames:
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:FlightRecorderOptions=stackdepth=128".

```

`MySqlBuggyOrderRepository.findByEmailWithArtificialDelay` uses MySQL's own
`SLEEP()` as a deterministic stand-in for a slow query — the same role
`SlowEmailMatcher` plays for H2, just running server-side instead of
in-process:

```java
@Query(value = "SELECT * FROM customer_orders WHERE customer_email = :email AND SLEEP(:delaySeconds) = 0",
       nativeQuery = true)
List<CustomerOrder> findByEmailWithArtificialDelay(@Param("email") String email,
                                                     @Param("delaySeconds") double delaySeconds);
```

`/api/fixed/orders` needs no MySQL-specific variant — it's a plain Spring
Data derived query, so it runs unchanged against whichever datasource the
active profile wires up.

Same 20-way concurrent load, same shared pool of 3, same failure mode:

```bash
seq 1 20 | xargs -P20 -I{} curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" \
  "http://localhost:8080/api/mysql/buggy/orders?email=target.customer@example.com"
# 3 succeed ~1.5s, 3 more ~3.0s, 14 fail 500 ~2.0s (same
# "HikariPool-1 - Connection is not available, request timed out after 2000ms" as before)
```

The diagnosis step is where this profile earns its keep. Same JFR workflow
(`jcmd <pid> JFR.start settings=profile ...`), but this time
`jdk.SocketRead` — not `jdk.ExecutionSample` — is what fires:

```bash
jfr print --events jdk.SocketRead --stack-depth 10 /tmp/mysql-buggy.jfr
```

```
jdk.SocketRead {
  duration = 1.50 s
  host = "localhost"
  address = "127.0.0.1"
  port = 3306
  bytesRead = 4 bytes
  eventThread = "http-nio-8080-exec-13" (javaThreadId = 68)
  stackTrace = [
    java.net.Socket$SocketInputStream.read(byte[], int, int) line: 67
    com.mysql.cj.protocol.ReadAheadInputStream.fill(int) line: 91
    com.mysql.cj.protocol.ReadAheadInputStream.readFromUnderlyingStreamIfNecessary(byte[], int, int) line: 130
    com.mysql.cj.protocol.ReadAheadInputStream.read(byte[], int, int) line: 157
    java.io.FilterInputStream.read(byte[], int, int) line: 119
    com.mysql.cj.protocol.FullReadInputStream.readFully(byte[], int, int) line: 55
    com.mysql.cj.protocol.a.SimplePacketReader.readHeaderLocal() line: 72
    com.mysql.cj.protocol.a.SimplePacketReader.readHeader() line: 54
  ]
}
```

`duration = 1.50 s` lines up exactly with the `SLEEP(1.5)` call, `port =
3306` is unmistakably MySQL, and the stack bottoms out in a real
`java.net.Socket$SocketInputStream.read` — the calling thread is genuinely
blocked in a socket recv() waiting for mysqld to respond. This is the
cleanest of the three signals in this whole demo family: no `ExecutionSample`
guesswork (H2), no `CompletableFuture`-hidden park (`io-exhaustion-demo`'s
`HttpClient`) — a real socket read event, naming the exact host and port.
The `jdk.ThreadPark` signal from the H2 walkthrough still shows up
unchanged underneath it, on the same `ConcurrentBag.borrow` /
`HikariPool.getConnection` stack — the pool-exhaustion symptom doesn't care
which datasource caused it.

Note: the stack above bottoms out in `com.mysql.cj.protocol...`, never
reaching `MySqlBuggyOrderRepository` — Hikari/Hibernate/driver frames use up
JFR's default 64-frame capture cap before the call chain gets that far. To
capture enough frames to name the calling method, set a bigger depth
*before* recording:

```bash
jcmd <pid> JFR.configure stackdepth=128
# or, at JVM launch instead:
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:FlightRecorderOptions=stackdepth=128"
```

One deliberate simplification: `schema-mysql.sql` does `DROP TABLE IF
EXISTS` + recreate on every startup (`spring.sql.init.mode=always`) so the
demo always starts from a clean, known state — MySQL data persists across
restarts, unlike H2's in-memory database which resets for free. And
`spring.sql.init.schema-locations` is pinned explicitly to
`schema-mysql.sql`; leaving it to auto-resolve by platform would *also*
still pick up the generic `schema.sql` (H2's, with its H2-only `CREATE
ALIAS`) and fail on the second `CREATE TABLE`.
