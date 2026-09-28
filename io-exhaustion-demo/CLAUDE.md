# io-exhaustion-demo

Demo: thread-pool exhaustion from blocking downstream call. `buggy/SlowDownstreamService` sync HTTP call ties up a Tomcat request thread per caller; under concurrency requests queue/starve. Fix: `fixed/` (`DownstreamExecutorConfig`, `AsyncDownstreamService`). `shared/DownstreamAvailabilityServer` = fake slow downstream in-process. Details: `bug-fix-notes-io-exhaustion.md`.

## Stack
Spring Boot 4.1.1 parent, Java 17, webmvc, actuator, micrometer-prometheus, springdoc 3.1.1. No DB/Kafka/Docker.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (excludes `ThreadStarvationReproductionTest`; timing-sensitive, real Tomcat + sockets)
- Run starvation test: `./mvnw test -Pthread-starvation-demo`

## Config
- Tomcat deliberately small: threads.max=10, accept-count=10.
- Actuator exposes health,prometheus,metrics. Port default 8080. No env vars.

## Layout
`com.example.ioexhaustiondemo`: `buggy`, `fixed`, `shared`.
