# Diagnosed real trace-propagation gap via Zipkin API, documented full backlog

User noticed separate traces per downstream service instead of one stitched trace after a successful order. Verified directly via Zipkin's `/api/v2/traces` API (queried per service, compared traceIds) rather than guessing: confirmed order-service's outbound calls (Feign to Customer/Payment, RestTemplate to Product) never carry forward trace context — each downstream hop starts a fresh root trace at the gateway. Root cause identified: `ProductClient`'s `RestTemplate` bean is built directly (`new RestTemplate()`) rather than via `RestTemplateBuilder` (which Spring Boot auto-instruments), and Feign likely lacks its tracing bridge dependency.

User also grasped the trace-reading mechanics well (traceId/parentId/kind/timestamp/duration/tags) through two rounds of explanation, including the CLIENT/SERVER "recorded twice" concept via a phone-call analogy.

All findings from the whole session (8 open issues + 2 already-applied infra fixes) consolidated into `~/github_projs/learning-microservices/known-issues.md`, including a plain-terms explainer of the outbox and saga patterns, for a future implementation session on a separate branch.

**Implication**: user is comfortable driving diagnostic API calls (Zipkin's REST API, not just its UI) to settle an ambiguous observation — future lessons can point at "check the API directly" as a debugging move, not just log-reading. The known-issues.md handoff doc is the definitive source for any future coding session on this repo; don't re-derive these findings from scratch if a later session references them.
