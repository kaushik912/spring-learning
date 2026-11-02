---
name: mysql-mcp-query
description: Use when the user asks a database/data question about this environment's MySQL instance (counts, lookups, table structure, "what's in the db", etc.) — invokes the mysql MCP server (mcp__mysql__mysql_query) to run the query live.
---

# MySQL MCP Query Skill

When activated:
- If the schema isn't already known, inspect it first via `mcp__mysql__mysql_query`
  (`SHOW TABLES`, then `DESCRIBE <table>` for relevant tables).
- Run only read-only statements: `SELECT`, `SHOW`, `DESCRIBE`, `EXPLAIN`. The
  mysql MCP server is read-only by default in this project (no
  `ALLOW_INSERT/UPDATE/DELETE/DDL_OPERATION` env vars are set in `.mcp.json`),
  so never attempt writes or schema changes.
- Summarize the result in plain language — don't just dump raw rows.
