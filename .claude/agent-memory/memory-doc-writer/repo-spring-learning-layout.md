---
name: repo-spring-learning-layout
description: Top-level layout of spring-learning monorepo and how it's indexed in codebase-memory-mcp
metadata:
  type: project
---

`/home/kaush/github_projs/spring-learning` is a monorepo of independent Spring/learning
sub-projects, not a single app: `booking-system/` (documented, see [[booking-system-structure]]),
`rest-api/`, `learning-microservices/` (has HTML lesson docs). Root has its own `CLAUDE.md`.

codebase-memory-mcp indexes the whole monorepo under project name
`home-kaush-github_projs-spring-learning` (not a per-submodule project). To scope
`get_architecture`/searches to just booking-system, pass `path: "booking-system"` to
get_architecture, or `file_pattern: "booking-system/..."` to search_graph.

**Why:** avoids re-running list_projects/index_status confusion next time — there is no
separate "booking-system" project entry, everything lives under the spring-learning root project.
**How to apply:** when asked to document/analyze booking-system (or rest-api, or
learning-microservices), use project=`home-kaush-github_projs-spring-learning` and scope with
path/file_pattern rather than searching for a submodule-named project.
