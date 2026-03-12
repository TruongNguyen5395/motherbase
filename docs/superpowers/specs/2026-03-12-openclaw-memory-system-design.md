# OpenClaw-Inspired Memory System Design

**Date:** 2026-03-12
**Status:** Approved
**Context:** Motherbase — Truong's executive assistant second brain

---

## Goal

Upgrade the motherbase memory system from a flat index to a full OpenClaw-inspired three-tier architecture: identity layer, curated long-term memory, and daily working logs — all loading at session start via CLAUDE.md.

---

## Architecture

### Three-Tier Memory Hierarchy

| Tier | File | Lifespan | Update Frequency |
|------|------|----------|-----------------|
| Identity | `context/SOUL.md`, `context/USER.md` | Indefinite | Rare (profile shifts) |
| Long-term | `context/MEMORY.md` | Indefinite | Weekly/monthly review |
| Daily | `context/memory/YYYY-MM-DD.md` | 1 day working log | Every session |

### Load Order at Session Start

| Order | File | Always? |
|-------|------|---------|
| 1 | `context/SOUL.md` | Yes |
| 2 | `context/USER.md` | Yes |
| 3 | `context/MEMORY.md` | Yes |
| 4 | `context/memory/YYYY-MM-DD.md` (today) | Yes |
| 5 | `context/memory/YYYY-MM-DD.md` (yesterday) | If exists |

---

## File Structure

```
motherbase/
├── context/
│   ├── SOUL.md                # Agent identity, personality, values
│   ├── USER.md                # Truong's consolidated profile
│   ├── MEMORY.md              # Curated long-term memory index
│   ├── HEARTBEAT.md           # 30-min background maintenance checklist
│   └── memory/                # Daily working logs
│       └── YYYY-MM-DD.md
├── .tmp/
│   ├── me.md                  # archived from context/
│   ├── work.md                # archived from context/
│   ├── team.md                # archived from context/
│   ├── current-priorities.md  # archived from context/
│   └── goals.md               # archived from context/
└── CLAUDE.md                  # Updated — loads from context/
```

---

## File Contents

### `context/SOUL.md` — Agent Identity

Claude's personality, values, and behavioral boundaries. Loaded every session.

```markdown
# Soul

You are Truong's executive assistant and second brain.

## Identity
- Analytical: data-driven, structured thinking, tables over prose
- Direct: say what needs to be said, flag problems early, challenge assumptions
- Reliable: remember everything, handle details, follow through

## Values
- Truong's time is the scarcest resource — protect it
- Revenue first: solo ecommerce before all other work
- Honesty over comfort: bad news early is better than late

## Boundaries
- Never guess — ask when unclear
- Never pad responses — concise and accurate always
- Never delete — archive instead
```

---

### `context/USER.md` — Truong's Profile

Consolidates all 5 existing context files (me.md, work.md, team.md, current-priorities.md, goals.md) into one file. The source of truth for who Truong is and what he's working on.

Sections:
- Identity (name, location, timezone, language)
- Roles (3TSolution + solo ecommerce)
- #1 Priority
- 3TSolution context (company, team, BA work)
- Solo ecommerce context (model, platform, status)
- Tools & integrations
- Team & key people
- Current priorities (priority stack)
- Goals & milestones (Q1, Q3, full year 2026)

---

### `context/MEMORY.md` — Curated Long-Term Memory

Restructured from the current flat index to OpenClaw-style curated facts. Content distilled from daily logs by the heartbeat cycle.

Structure:
- Preferences & working style
- Key decisions (with dates)
- Important context (recurring patterns, blockers)
- Distilled from daily logs (date of origin)

---

### `context/HEARTBEAT.md` — 30-Min Maintenance Checklist

```markdown
# Heartbeat (every 30 min)

- [ ] Write key decisions/actions from session to memory/YYYY-MM-DD.md
- [ ] Flag any upcoming deadlines in the next 48 hours
- [ ] If memory/YYYY-MM-DD.md has 5+ entries → distill top items to MEMORY.md
```

---

### `context/memory/YYYY-MM-DD.md` — Daily Log Format

Lightweight. Append-only within the day.

```markdown
# YYYY-MM-DD

## Key Decisions
- [timestamp] Decision made: ...

## Actions & Outcomes
- [timestamp] Task completed: ...

## To Remember
- ...
```

---

## CLAUDE.md Changes

Replace references to individual context files with the new load order:

```markdown
## Memory System (OpenClaw-style)

Load at session start:
- @context/SOUL.md — who Claude is
- @context/USER.md — who Truong is
- @context/MEMORY.md — curated long-term memory
- @context/memory/YYYY-MM-DD.md — today's log (if exists)
- @context/memory/YYYY-MM-DD.md — yesterday's log (if exists)

Write to daily log when:
- A meaningful decision is made
- An important action is taken or outcome is reached
- Something needs to be remembered next session

Heartbeat: every 30 min, check HEARTBEAT.md and execute checklist.
```

---

## Migration Plan

1. Create `context/SOUL.md` (new)
2. Create `context/USER.md` (consolidate 5 context files)
3. Restructure `context/MEMORY.md`
4. Create `context/HEARTBEAT.md` (new)
5. Create `context/memory/` directory + today's log
6. Move old context files to `.tmp/`
7. Update `CLAUDE.md` load order

---

## Reference

Inspired by [OpenClaw](https://github.com/openclaw/openclaw) memory architecture.
Key principles: plain text as truth, identity separate from memory, daily logs distilled to long-term, privacy-aware loading, 30-min heartbeat maintenance.
