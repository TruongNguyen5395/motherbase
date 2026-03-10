# Decision Log

Append-only. When a meaningful decision is made, log it here.

Format: [YYYY-MM-DD] DECISION: ... | REASONING: ... | CONTEXT: ...

---

[2026-03-11] DECISION: Replace `workspace-mcp` (uvx) with Google Workspace CLI (`gws` / `@googleworkspace/cli`) for all Google integrations | REASONING: `gws` covers all Google APIs (Drive, Docs, Sheets, Gmail, Calendar, Chat, Admin, + more) in one CLI tool with structured JSON output. Dynamically built from Google Discovery Service — auto-updates when Google adds APIs. Replaces 3 separate integrations (workspace-mcp + built-in Gmail MCP + built-in Calendar MCP) with one unified tool. | CONTEXT: Google released gws open-source on 2026-03-06. Experimental but rapidly adopted (10k+ GitHub stars in first week). Auth via refresh token is persistent — one-time setup.
