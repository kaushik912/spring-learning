# Spring Boot Microservices Resources

## Knowledge

- [Source repo: fully-completed-microservices-Java-Springboot](../reference_repos/fully-completed-microservices-Java-Springboot)
  The actual code we're learning from. `resources/curriculum.txt` in that repo is the section-by-section course outline this workspace follows.
- [Spring Cloud Config reference docs](https://docs.spring.io/spring-cloud-config/reference/)
  Official docs. Use for: how config-server serves per-service config, `spring.config.import`, refresh scopes.
- [Spring Cloud Netflix Eureka reference docs](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html)
  Official docs. Use for: service registration/discovery mechanics, self-preservation mode, client-side load balancing.
- [Spring Cloud Gateway reference docs](https://docs.spring.io/spring-cloud-gateway/reference/)
  Official docs. Use for: route predicates/filters, how Gateway resolves `lb://service-name` via Eureka.
- [Spring for Apache Kafka reference docs](https://docs.spring.io/spring-kafka/reference/)
  Official docs. Use for: `KafkaTemplate`, `@KafkaListener`, producer/consumer config — this repo uses Kafka, not RabbitMQ, despite what the curriculum text says.
- [Micrometer Tracing + Zipkin docs](https://docs.micrometer.io/tracing/reference/)
  Official docs. Use for: how trace/span IDs propagate across service calls, reading the Zipkin UI.
- [OpenFeign / Spring Cloud OpenFeign docs](https://docs.spring.io/spring-cloud-openfeign/reference/)
  Official docs. Use for: declarative REST clients vs `RestTemplate`, covered in curriculum Section 9.

## Wisdom (Communities)

- Not yet explored — revisit once fluency with the core patterns is established. No opt-out stated by user.

## Gaps

- No primary source yet for *why* this specific repo diverges from its own curriculum text (Kafka vs RabbitMQ) — likely just author's implementation choice; flag as an observation, not an error, in lessons that touch Section 13.
