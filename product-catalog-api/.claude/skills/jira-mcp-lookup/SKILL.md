---
name: jira-mcp-lookup
description: Use when the user asks about a specific Jira issue/ticket (by key, e.g. SCRUM-8) or wants to search/look up Jira issues — invokes the Atlassian MCP server (mcp__Atlassian__getJiraIssue, searchJiraIssuesUsingJql) to fetch live ticket data.
---

# Jira MCP Lookup Skill

When activated:
- If given a specific issue key, call `mcp__Atlassian__getJiraIssue` directly
  with that key.
- If the ask is broader (no specific key), use
  `mcp__Atlassian__getVisibleJiraProjects` and/or
  `mcp__Atlassian__searchJiraIssuesUsingJql` to find matching issues.
- Summarize status, summary, and assignee in plain language.
