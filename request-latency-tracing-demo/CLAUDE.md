# request-latency-tracing-demo

Demo: slow request (~480ms) but no way to see which step. `buggy/BuggyOrderService`: validate (~5ms), DB (~10ms), pricing (~450ms), assemble (~5ms) in one flat span. Fix: `fixed/` splits steps (OrderValidator, OrderDatabaseClient, PricingClient, OrderResponseAssembler) into child spans/observations -> Zipkin waterfall + per-step metrics. Details: `bug-fix-notes-request-tracing.md`.

## Stack
Spring Boot 4.1.1, Java 17, webmvc, actuator, aspectj, zipkin starter, micrometer-registry-prometheus, springdoc 3.1.1.

## Commands
- Run: `./mvnw spring-boot:run`
- Test: `./mvnw test` (LatencyObservabilityReproductionTest)
- Observability stack: `./start-observability.sh` (not plain `docker compose up`)

## Observability (Docker, optional, for manual exploration)
- docker-compose: Zipkin :9411, Prometheus :9090, Grafana :3000 (anonymous admin). Ask user before Docker.
- start-observability.sh detects host IP via `ip route` and generates observability/prometheus/prometheus.yml from .template (WSL2 host.docker.internal workaround); Prometheus scrapes app at HOST_IP:8080.
- App exports spans to http://localhost:9411/api/v2/spans; sampling probability 1.0 (demo only). Actuator exposes health,prometheus.
- App port default 8080. No env vars.

## Layout
`com.example.requestlatencytracingdemo`: `buggy`, `fixed`, `config` (ObservationConfig).
