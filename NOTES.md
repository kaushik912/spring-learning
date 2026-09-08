# Notes

- User wants reports/explanations concise (global CLAUDE.md preference) — keep lesson prose tight, no padding.
- Workspace deliberately kept OUTSIDE the reference repo (`../reference_repos/fully-completed-microservices-Java-Springboot`) so that repo's git status stays clean. Lessons should link to files there by relative path.
- Docker approved for infra containers (docker-compose in the reference repo) as of workspace creation. Re-confirm if scope grows beyond that (e.g. building/running service Docker images).
- Already covered informally before this workspace existed: Section 4 (Bootstrap) — CustomerApplication skeleton, pom.xml dependency roles, docker-compose services. Treat as taught; first lesson should pick up at Section 5 (Config server) unless user wants Section 4 formalized into a lesson too.
- All bugs/gaps found hands-on across lessons are tracked in `known-issues.md` (workspace root), meant for a future implementation pass on a separate branch/session — not fixed in the reference repo itself beyond the two infra fixes already applied (Kafka image pin, `max.block.ms`), which are still uncommitted in the reference repo's working tree.
