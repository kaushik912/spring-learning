# Config server request/response verified hands-on

Ran config-server locally and confirmed `curl http://localhost:8888/customer-service/default` returns the right service's config by name-matching. Evidence: completed Lesson 1's hands-on steps successfully ("worked fine"). Confirms the core pattern (client name → config server → matching YAML) landed — future lessons can build on this without re-explaining the request/response shape, and can move to services that actually *consume* this on boot (Section 6, Customer service).
