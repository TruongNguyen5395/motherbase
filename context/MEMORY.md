# Long-Term Memory

Curated facts, preferences, and decisions distilled from daily logs.
Updated by heartbeat cycle. Append new entries — never delete.

**Last reviewed:** 2026-03-12

---

## Preferences & Working Style

- Prefers tables over prose for presenting information
- Concise and direct — no padding, no filler words
- Mix English and Vietnamese naturally
- No emojis unless explicitly asked
- Revenue-first mindset: ecommerce > 3TSolution > admin tasks

## Architecture Decisions

- Claude Code (motherbase) = execution terminal
- Directus (app.hidave.co) = visual dashboard / brain UI
- Lark Suite = 3TSolution team communication
- WAT framework: Workflows → Agent → Tools
- OpenClaw memory system adopted: SOUL.md + USER.md + MEMORY.md + daily logs

## Integration Notes

- Google Workspace via `gws` CLI (`@googleworkspace/cli` v0.10.0), wrapper at `tools/gws.sh`
- Auth: refresh token at `/config/.config/gws/credentials.json` (persistent, auto-renews)
- Lark Bitable: `@larksuiteoapi/lark-mcp` MCP
- Directus: `@directus/content-mcp` MCP
- npx at `/config/.nvm/versions/node/v24.14.0/bin/npx`
- No system Python — use uvx for Python tools

## Runtime Notes

- Node.js v24.14.0 at `/config/.nvm/`
- uv/uvx v0.10.9 at `/config/.local/bin/`

## Key Decisions (Logged)

| Date | Decision |
|------|---------|
| 2026-03-12 | Adopted OpenClaw-inspired memory system — SOUL.md, USER.md, MEMORY.md, daily logs, 30-min heartbeat |
| 2026-03-12 | Replaced workspace-mcp with gws CLI for Google Workspace integration |
| 2026-03-12 | Memory files live in context/ to keep root clean |

## Open Loops

- Solo company name: TBD
- Ecommerce product: not selected yet
- Weekly report skill: not built yet
