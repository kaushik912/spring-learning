---
name: cbm-tool-notes
description: How well codebase-memory-mcp tools worked on spring-learning/booking-system, coverage caveats
metadata:
  type: project
---

As of 2026-09-10, `home-kaush-github_projs-spring-learning` was already indexed (660 nodes /
1516 edges, status ready) — no re-index needed. `get_architecture` with `path: "booking-system"`
scoping and `search_graph`/`trace_path`/`get_code_snippet` all worked cleanly and matched actual
source exactly (verified BookingService/BookingController/SeatRepository by direct Read too).

Coverage gaps found via index_status (parse_partial, non-blocking for docs purposes):
- `booking-system/src/main/resources/db/migration/V1__init_schema.sql` lines 27-28 flagged
  parse_partial (SQL constraint line) — irrelevant to code-graph docs, confirmed via direct Read.
- `booking-system/tickets/TICKET-001.yaml` lines 1-26 flagged parse_partial — read directly if
  ticket content is ever needed for docs.

**Why:** saves a re-verification pass next time — the graph tools are trustworthy for this repo's
Java source; only the YAML/SQL edge cases need direct Read/grep fallback.
**How to apply:** trust search_graph/trace_path/get_code_snippet for booking-system Java code
without needing Grep fallback; use direct Read for tickets/*.yaml and migration SQL comments/
constraints since those are flagged parse_partial.
