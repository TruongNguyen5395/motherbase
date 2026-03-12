# OpenClaw Memory System Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade motherbase from a flat memory index to a full OpenClaw-inspired three-tier memory system: identity layer (SOUL.md, USER.md), curated long-term memory (MEMORY.md), and daily working logs (memory/YYYY-MM-DD.md), all loaded at session start via CLAUDE.md.

**Architecture:** Plain Markdown files in `context/` serve as the memory store. CLAUDE.md instructs Claude to load them in order at session start. A 30-min HEARTBEAT.md checklist drives maintenance. No code or databases — files only.

**Tech Stack:** Markdown, Claude Code CLAUDE.md file references (@-syntax)

**Spec:** `docs/superpowers/specs/2026-03-12-openclaw-memory-system-design.md`

---

## Chunk 1: Identity Layer

### Task 1: Create `context/SOUL.md`

**Files:**
- Create: `context/SOUL.md`

- [ ] **Step 1: Create SOUL.md**

Create `context/SOUL.md` with the following content:

```markdown
# Soul

You are Nguyễn Hữu Trường's (Truong) executive assistant and second brain.

## Identity

- **Analytical:** Data-driven, structured thinking. Always prefer tables over prose. Show numbers, not feelings.
- **Direct:** Say what needs to be said. Flag problems early. Challenge assumptions when something doesn't add up.
- **Reliable:** Remember everything. Handle details without being asked. Follow through on open loops.

## Values

- Truong's time is the scarcest resource — protect it ruthlessly
- Revenue first: solo ecommerce takes priority over all other work
- Honesty over comfort: bad news delivered early is better than good news delivered late
- Systems over heroics: repeatable processes beat one-off solutions

## Behavioral Rules

- Never guess — ask when unclear
- Never pad responses — concise and accurate always
- Never delete — archive instead (move to `.tmp/`)
- Never assume context from previous sessions — read the memory files
- Write to `context/memory/YYYY-MM-DD.md` whenever a meaningful decision or action occurs

## Language

Mix English and Vietnamese naturally. Truong is Vietnamese and uses both interchangeably.
```

- [ ] **Step 2: Verify file was created correctly**

Run: `cat context/SOUL.md`
Expected: File contains all sections (Identity, Values, Behavioral Rules, Language)

- [ ] **Step 3: Commit**

```bash
git add context/SOUL.md
git commit -m "feat: add SOUL.md — agent identity layer (OpenClaw memory system)"
```

---

### Task 2: Create `context/USER.md`

Consolidates content from: `context/me.md`, `context/work.md`, `context/team.md`, `context/current-priorities.md`, `context/goals.md`.

**Files:**
- Create: `context/USER.md`
- Reference (read-only): all 5 existing context files

- [ ] **Step 1: Create USER.md**

Create `context/USER.md` with the following content:

```markdown
# User Profile: Nguyễn Hữu Trường (Truong)

**Last updated:** 2026-03-12

---

## Identity

| Field | Value |
|-------|-------|
| Name | Nguyễn Hữu Trường (Truong) |
| Location | Ho Chi Minh City, Vietnam |
| Timezone | Asia/Ho_Chi_Minh (UTC+7) |
| Language | Vietnamese (native), English (working proficiency) |

## Roles

| Role | Company | Description |
|------|---------|-------------|
| Software Team Lead | 3TSolution | Leading a 5-person software team building ERP for outdoor furniture manufacturing |
| Business Analyst | 3TSolution | Feature lists, workflow analysis, wireframes, BRDs |
| Entrepreneur | Solo company (TBD) | Building an ecommerce business, starting with Facebook |

## #1 Priority

Building my own company to generate revenue as fast as possible, while maintaining the software team at 3TSolution (which provides income).

---

## 3TSolution (Day Job)

| Detail | Info |
|--------|------|
| Company | 3TSolution — sub-company of Kingston |
| Kingston | Outdoor furniture manufacturer, ~400 employees |
| Founded | 2025 |
| My role | Software Team Lead + Business Analyst |
| Product | ERP software for manufacturing outdoor furniture |
| Team size | 12 total (5 software, rest execution team at Kingston factory) |

**BA work I regularly do:** feature list creation, workflow analysis, wireframe building, BRD writing.

### Key People

| Name | Role | When to Loop In |
|------|------|----------------|
| Miss Thao | Shareholder | Most important person — major decisions, funding, strategic direction |
| Mr. Tan | Factory Manager | Factory operations, manufacturing-related decisions |
| Mr. Hai Anh | Product Owner / Team Lead | Day-to-day team leadership, product decisions, sprint planning |

**Communication:** Lark Suite (primary)
**Pain point:** Team struggles to keep up with deadlines — deadlines sometimes get forgotten

---

## Solo Ecommerce Company (Side Business)

| Detail | Info |
|--------|------|
| Company name | TBD |
| Model | Ecommerce — selling products online |
| Platform | Facebook first, then expand to other platforms |
| Status | Early stage — building structure |
| Team | Just me. No team yet. |

---

## Tools & Integrations

| Tool | Used For |
|------|----------|
| Google Workspace | Personal work and productivity |
| Lark Suite | 3TSolution team communication and collaboration |
| Directus (app.hidave.co) | Personal management portal / central brain UI |
| Claude Code | AI assistant (this workspace) |

**Integration details:**
- Google Workspace: `gws` CLI (`@googleworkspace/cli` v0.10.0), wrapper at `tools/gws.sh`. Auth persistent at `/config/.config/gws/credentials.json`.
- Canva: built-in MCP
- Lark Bitable: `@larksuiteoapi/lark-mcp` MCP
- Directus: `@directus/content-mcp` MCP

### Directus Collections

| Collection | Purpose |
|------------|---------|
| `projects` | Active projects and workstreams |
| `tasks` | Central task hub |
| `goals` | Revenue targets and milestones |
| `knowledge` | Second brain — notes, learnings, insights |
| `documents` | Document registry |
| `decisions` | Decision log (append-only) |
| `daily_journal` | Daily thoughts, reflections, and notes |

---

## Current Priorities

**Last updated:** 2026-03-10

| # | Priority | Context |
|---|----------|---------|
| 1 | Generate ecommerce revenue ASAP | Target: 70M VND/month by end of 2026 |
| 2 | Help 3TSolution team hit deadlines | Team struggles with deadline tracking — needs a system |
| 3 | BA work for ERP features | Feature lists, workflow analysis, wireframes, BRDs |
| 4 | Build structure for solo company | Set up operations, workflows, and systems before scaling |
| 5 | Establish daily/weekly routines | Reports, scheduling, content — need repeatable processes |

---

## Goals & Milestones

### Q1 2026 (Current)

**Revenue target:** Laying groundwork — no specific revenue target this quarter.

### Q3 2026

| Goal | Target | Status |
|------|--------|--------|
| Ecommerce monthly revenue | 40,000,000 VND | Not started |

### Full Year 2026

| Goal | Target | Status |
|------|--------|--------|
| Ecommerce monthly revenue | 70,000,000 VND/month | Not started |
| 3TSolution team delivery | Consistent deadline adherence | In progress |
| Solo company structure | Operations and systems established | In progress |
```

- [ ] **Step 2: Verify file was created correctly**

Run: `cat context/USER.md`
Expected: All sections present — Identity, Roles, 3TSolution, Solo Company, Tools, Priorities, Goals

- [ ] **Step 3: Commit**

```bash
git add context/USER.md
git commit -m "feat: add USER.md — consolidated Truong profile (OpenClaw memory system)"
```

---

## Chunk 2: Memory & Heartbeat

### Task 3: Create `context/MEMORY.md`

Long-term curated memory. Starts with content distilled from existing CLAUDE.md memory notes and auto-memory.

**Files:**
- Create: `context/MEMORY.md`

- [ ] **Step 1: Create MEMORY.md**

Create `context/MEMORY.md` with the following content:

```markdown
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
```

- [ ] **Step 2: Verify file was created correctly**

Run: `cat context/MEMORY.md`
Expected: All sections present with content

- [ ] **Step 3: Commit**

```bash
git add context/MEMORY.md
git commit -m "feat: add MEMORY.md — curated long-term memory (OpenClaw memory system)"
```

---

### Task 4: Create `context/HEARTBEAT.md`

**Files:**
- Create: `context/HEARTBEAT.md`

- [ ] **Step 1: Create HEARTBEAT.md**

Create `context/HEARTBEAT.md` with the following content:

```markdown
# Heartbeat Checklist

Run every 30 minutes during active sessions.

---

## Every 30 Min

- [ ] Write key decisions/actions from this session to `context/memory/YYYY-MM-DD.md`
- [ ] Flag any upcoming deadlines in the next 48 hours (check USER.md + MEMORY.md open loops)
- [ ] If `context/memory/YYYY-MM-DD.md` has 5+ entries → distill top items to `context/MEMORY.md`

## Daily (First Session of Day)

- [ ] Create today's log file at `context/memory/YYYY-MM-DD.md` if it doesn't exist
- [ ] Read yesterday's log (`context/memory/YYYY-MM-DD.md`) for carryover context
- [ ] Scan `context/MEMORY.md` open loops — are any resolved or actionable today?

## Weekly (Monday First Session)

- [ ] Review last 7 daily logs — distill patterns or decisions into `context/MEMORY.md`
- [ ] Update priorities in `context/USER.md` if focus has shifted
- [ ] Archive resolved items from open loops in `context/MEMORY.md`

---

## Log Format (`context/memory/YYYY-MM-DD.md`)

```markdown
# YYYY-MM-DD

## Key Decisions
- [HH:MM] Decision: ...

## Actions & Outcomes
- [HH:MM] Completed: ...
- [HH:MM] Blocked on: ...

## To Remember Next Session
- ...
```
```

- [ ] **Step 2: Verify file was created correctly**

Run: `cat context/HEARTBEAT.md`
Expected: 30-min, daily, and weekly checklists present

- [ ] **Step 3: Commit**

```bash
git add context/HEARTBEAT.md
git commit -m "feat: add HEARTBEAT.md — 30-min maintenance checklist (OpenClaw memory system)"
```

---

### Task 5: Create today's daily log

**Files:**
- Create: `context/memory/2026-03-12.md`

- [ ] **Step 1: Create memory directory and today's log**

```bash
mkdir -p context/memory
```

Create `context/memory/2026-03-12.md` with the following content:

```markdown
# 2026-03-12

## Key Decisions

- [session] Adopted OpenClaw-inspired memory system for motherbase
- [session] Chose Option A (Full OpenClaw Port) over hybrid or Directus-first approach
- [session] Memory files live in context/ (not workspace root) to keep root clean
- [session] 30-minute heartbeat cycle (shorter than OpenClaw default)
- [session] context/USER.md consolidates all 5 previous context files

## Actions & Outcomes

- [session] Analyzed OpenClaw codebase — memory architecture fully understood
- [session] Design doc written: docs/superpowers/specs/2026-03-12-openclaw-memory-system-design.md
- [session] Implementation plan written: docs/superpowers/plans/2026-03-12-openclaw-memory-system.md
- [session] SOUL.md, USER.md, MEMORY.md, HEARTBEAT.md created
- [session] Old context files archived to .tmp/
- [session] CLAUDE.md updated to load new memory system

## To Remember Next Session

- Memory system is now active — load SOUL.md → USER.md → MEMORY.md → today's log → yesterday's log
- Old context files are in .tmp/ for reference if needed
- Heartbeat runs every 30 min — check HEARTBEAT.md
```

- [ ] **Step 2: Verify directory and file exist**

Run: `ls context/memory/`
Expected: `2026-03-12.md` present

- [ ] **Step 3: Commit**

```bash
git add context/memory/
git commit -m "feat: add daily log structure + today's log (OpenClaw memory system)"
```

---

## Chunk 3: Migration & Wiring

### Task 6: Archive old context files to `.tmp/`

**Files:**
- Move: `context/me.md` → `.tmp/me.md`
- Move: `context/work.md` → `.tmp/work.md`
- Move: `context/team.md` → `.tmp/team.md`
- Move: `context/current-priorities.md` → `.tmp/current-priorities.md`
- Move: `context/goals.md` → `.tmp/goals.md`

- [ ] **Step 1: Move old context files to .tmp/**

```bash
mkdir -p .tmp
mv context/me.md .tmp/me.md
mv context/work.md .tmp/work.md
mv context/team.md .tmp/team.md
mv context/current-priorities.md .tmp/current-priorities.md
mv context/goals.md .tmp/goals.md
```

- [ ] **Step 2: Verify old files are gone from context/, present in .tmp/**

Run: `ls context/ && echo "---" && ls .tmp/`
Expected:
- `context/` no longer contains me.md, work.md, team.md, current-priorities.md, goals.md
- `.tmp/` contains all 5 files

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: archive old context files to .tmp/ (replaced by USER.md)"
```

---

### Task 7: Update `CLAUDE.md`

Replace the old context file references with the new memory system load order and add heartbeat + daily log write instructions.

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Update the Context section in CLAUDE.md**

Replace the current `## Context` section:

```markdown
## Context

All context lives in dedicated files — read them, don't repeat them here:

- @context/me.md — Who Truong is, his roles, his #1 priority
- @context/work.md — 3TSolution + solo ecommerce details, tools, integrations
- @context/team.md — Team structure, key people, communication channels
- @context/current-priorities.md — What Truong is focused on right now
- @context/goals.md — Quarterly goals and revenue targets
```

With this new section:

```markdown
## Memory System (OpenClaw-style)

Load at session start in this order:

1. @context/SOUL.md — who Claude is (identity, values, behavioral rules)
2. @context/USER.md — who Truong is (profile, roles, priorities, goals)
3. @context/MEMORY.md — curated long-term memory (decisions, preferences, open loops)
4. Today's log: `context/memory/YYYY-MM-DD.md` (if exists)
5. Yesterday's log: `context/memory/YYYY-MM-DD.md` (if exists)

### Writing to Memory

Write to `context/memory/YYYY-MM-DD.md` (today's log) when:
- A meaningful decision is made
- An important action is completed or blocked
- Something must be remembered next session

Create the file if it doesn't exist yet. Format:
```markdown
# YYYY-MM-DD

## Key Decisions
- [HH:MM] Decision: ...

## Actions & Outcomes
- [HH:MM] Completed: ...

## To Remember Next Session
- ...
```

### Heartbeat

Every 30 minutes during active sessions: run the checklist in `@context/HEARTBEAT.md`.
```

- [ ] **Step 2: Update the Memory section in CLAUDE.md**

Replace the current `## Memory` section:

```markdown
## Memory

Claude Code maintains persistent memory across conversations. It automatically saves important patterns, preferences, and learnings. No configuration needed.

To remember something specific, just say: "Remember that I always want X."

Memory + context files + decision log = the assistant gets smarter over time without re-explaining things.
```

With:

```markdown
## Memory

Two layers:
- **OpenClaw memory files** (`context/`) — the primary memory system. Loaded at session start. Claude writes to daily logs during sessions and distills to MEMORY.md via heartbeat.
- **Claude Code auto-memory** — background memory managed automatically. Supplements OpenClaw files.

To remember something specific: "Remember that I always want X." Claude will write it to the daily log and MEMORY.md.
```

- [ ] **Step 3: Update Keeping Context Current section**

Replace:

```markdown
## Keeping Context Current

- Update `context/current-priorities.md` when your focus shifts
- Update `context/goals.md` at the start of each quarter
- Log important decisions in `decisions/log.md`
- Add reference files to `references/` as needed
- Build skills when you notice recurring requests
- **Never delete — always archive** to `archives/`
```

With:

```markdown
## Keeping Context Current

- Update priorities in `context/USER.md` when your focus shifts
- Update goals in `context/USER.md` at the start of each quarter
- Log important decisions in `decisions/log.md`
- Add reference files to `references/` as needed
- Build skills when you notice recurring requests
- **Never delete — always archive** (move to `.tmp/`)
- Daily logs live in `context/memory/YYYY-MM-DD.md` — heartbeat distills them to `context/MEMORY.md`
```

- [ ] **Step 4: Verify CLAUDE.md looks correct**

Run: `cat CLAUDE.md`
Expected: New Memory System section present, old context file references removed, heartbeat instruction present

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "feat: update CLAUDE.md to load OpenClaw memory system at session start"
```

---

## Chunk 4: Verification

### Task 8: Final verification

- [ ] **Step 1: Verify complete file structure**

Run: `ls context/ && echo "---" && ls context/memory/`
Expected:
```
HEARTBEAT.md
MEMORY.md
SOUL.md
USER.md
memory/
---
2026-03-12.md
```

- [ ] **Step 2: Verify old files archived**

Run: `ls .tmp/`
Expected: `me.md  work.md  team.md  current-priorities.md  goals.md`

- [ ] **Step 3: Verify CLAUDE.md references updated**

Run: `grep -n "SOUL\|USER\|HEARTBEAT\|context/me\|context/work\|context/team" CLAUDE.md`
Expected: SOUL.md, USER.md, HEARTBEAT.md referenced. No references to old context/me.md, context/work.md, context/team.md.

- [ ] **Step 4: Verify git history is clean**

Run: `git log --oneline -8`
Expected: 6 clean commits for the memory system implementation

- [ ] **Step 5: Final commit if anything missed**

```bash
git status
# If clean: done
# If dirty: stage and commit remaining files
```

---

## Summary

| Task | Files Created/Modified |
|------|----------------------|
| 1 | `context/SOUL.md` (new) |
| 2 | `context/USER.md` (new — consolidates 5 files) |
| 3 | `context/MEMORY.md` (new) |
| 4 | `context/HEARTBEAT.md` (new) |
| 5 | `context/memory/2026-03-12.md` (new) |
| 6 | Old context files → `.tmp/` (archived) |
| 7 | `CLAUDE.md` (updated load order + heartbeat) |
| 8 | Verification |
