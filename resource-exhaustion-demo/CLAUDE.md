# resource-exhaustion-demo

Demo: two "healthy but wrong" bugs that only fail at volume.
1. Memory/OOM: `memory/buggy/MemoryHogService.buildReport` materializes whole result set into one List. Fix: `memory/fixed/StreamingReport*`.
2. CPU: `cpu/buggy/SlowDuplicateFinder` (duplicate detection). Fix: `cpu/fixed/FastDuplicateFinder`. `cpu/shared/OrderIdGenerator` = data.
Details: `bug-fix-notes-resource-exhaustion.md`.

## Stack
Spring Boot 4.1.1, Java 17, webmvc, actuator, micrometer-prometheus, springdoc 3.1.1. No DB/Kafka/Docker.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (excludes `OomReproductionTest`); CpuIntensiveReproductionTest included
- OOM repro: `./mvnw test -Poom-demo` (surefire argLine -Xmx64m)

## Config
Actuator exposes health,prometheus,metrics. Port default 8080. No env vars. `.vscode/settings.json` present.

## Layout
`com.example.resourceexhaustiondemo`: `memory.{buggy,fixed}`, `cpu.{buggy,fixed,shared}`.
