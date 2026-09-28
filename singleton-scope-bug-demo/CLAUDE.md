# singleton-scope-bug-demo

Demo: shared mutable field in singleton bean races under concurrent requests. `buggy/CurrentRequestContext` (@Service, plain `currentUserId` field) set by `BuggyOrderController`, then `Thread.sleep(50)`, then read back -> another request's user id leaks. Fixes: `fixed/SafeLocalVarController` (local var), `fixed/SafeThreadLocalController` + `ThreadLocalRequestContext` (ThreadLocal). Details: `bug-fix-notes.md`.

## Stack
Spring Boot 4.1.1, Java 17, webmvc, springdoc 3.1.1. No DB/Kafka/Docker.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (RaceConditionReproductionTest = concurrent repro)

## Config
Only spring.application.name set. Port default 8080. No env vars.

## Layout
`com.example.singletonscopebugdemo`: `buggy`, `fixed`.
