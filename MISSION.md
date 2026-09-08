# Mission: Spring Boot Microservices (via fully-completed-microservices-Java-Springboot)

## Why
Understand microservices architecture patterns deeply enough to design/build a similar system at work or on a side project — not just read about it, but see each pattern (config server, discovery, gateway, async messaging, tracing) actually running and talking to real services. Learning by tracing a working, professionally-built reference app against a known curriculum, section by section.

## Success looks like
- Can explain what each of the 8 services does and why it's a separate service (config-server, discovery, gateway, customer, product, order, payment, notification)
- Has run the full stack locally (docker-compose infra + all 8 Spring Boot services) and hit real endpoints for each curriculum section as it's covered
- Can trace one request end-to-end through the system: gateway → discovery lookup → service → any downstream service calls (REST/Feign) → async event (Kafka) → notification email
- Can explain, in their own words, each Spring Cloud pattern covered (centralized config, service discovery, API gateway routing, async messaging, distributed tracing) well enough to reuse the pattern elsewhere
- Can point to the specific code in this repo that implements each curriculum section

## Constraints
- Source repo: `../reference_repos/fully-completed-microservices-Java-Springboot` — already fully built (single "completed" commit, no incremental history to replay). We learn by *reading and running* the finished code, matching it against `resources/curriculum.txt` section by section — not by rebuilding it from scratch.
- The reference repo must stay untouched (it's a clone of someone else's project) — this workspace lives alongside it, never inside it.
- Docker is used for infra only (Postgres, MongoDB, Kafka, Zookeeper, Zipkin, MailDev) — approved for this workspace. Ask again if this expands beyond docker-compose infra.
- Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1, Maven.

## Out of scope
- Rewriting the app from scratch (the course teaches that; here we're reverse-engineering a finished build)
- Deep dives into unrelated topics (Kubernetes, cloud deployment, CI/CD) unless the mission shifts there later
- RabbitMQ (curriculum text mentions it, but this repo actually uses Kafka — we follow the code, not the curriculum doc, where they diverge)
