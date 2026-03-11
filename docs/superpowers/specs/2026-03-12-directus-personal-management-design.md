# Directus Personal Management — Design Spec

**Date:** 2026-03-12
**Status:** Approved
**Approach:** Option C — Staged Rollout

---

## Problem

Truong has Directus at `app.hidave.co` but hasn't used it. The goal is to make it a unified command center for all personal work — ecommerce, 3TSolution, and personal tasks — where Claude executes and Truong reviews.

---

## Architecture

| Component | Role |
|-----------|------|
| Directus (`app.hidave.co`) | Visual dashboard — the single place to see all work |
| Claude Code | Execution terminal — creates tasks, pushes data on confirm |
| Google Workspace | Documents, email, calendar — NOT replaced by Directus |
| Lark Suite | 3TSolution team communication — NOT replaced by Directus |

Directus and Google Workspace are complementary, not competing:
- Directus → structured data (tasks, goals, projects)
- Google Workspace → documents, calendar, external communication

---

## Data Model

### `projects`

| Field | Type | Values |
|-------|------|--------|
| name | string | — |
| context | dropdown | `personal` / `3tsolution` / `ecommerce` |
| status | dropdown | `active` / `on-hold` / `done` |
| description | text | — |

### `tasks`

| Field | Type | Values |
|-------|------|--------|
| title | string | — |
| status | dropdown | `todo` / `in-progress` / `review` / `done` |
| priority | dropdown | `high` / `medium` / `low` |
| due_date | date | — |
| project | relation (M2O) | → `projects` |
| notes | text | Claude ghi chú khi tạo |
| created_by | dropdown | `claude` / `manual` |

### `goals`

Keep as-is. Already seeded with Q3 2026 (40M VND) and Year-end (70M VND/month).

---

## Workflow: Claude ↔ Directus ↔ Truong

```
Claude thực thi tác vụ
        ↓
Hiện kết quả trong chat → Truong xem
        ↓
Truong confirm "ok đưa lên"
        ↓
Claude push lên Directus
```

**Rule:** Claude never auto-pushes. Truong must confirm first. Directus only contains approved data.

### Task Status Flow

```
Claude creates → [review] → Truong confirms → [todo] → [in-progress] → [done]
```

### 3 Trigger Types for Claude to Create Tasks

| Trigger | Example | Action |
|---------|---------|--------|
| Explicit request | "tạo plan ecommerce tháng này" | Create tasks + link to project |
| End of session | After important conversation | Push summary + action items |
| Proactive | Upcoming deadline detected | Create reminder task |

---

## Staged Rollout

### Phase 1 — Setup & First Use (Now)

1. Verify existing collections have required fields — add missing ones
2. Create initial projects: Personal, Ecommerce, 3TSolution
3. Test end-to-end: Claude creates task → Truong reviews on app.hidave.co → confirm → pushed
4. Confirm workflow works

### Phase 2 — Expand Based on Real Usage (After 2-3 weeks)

Based on actual usage, decide if needed:
- Separate dashboard views per context
- Weekly automated summaries
- Additional collections (daily_journal, habits, etc.)

**Do not build Phase 2 before knowing what's actually needed.**

---

## Out of Scope (Phase 1)

- Lark → Directus sync (too complex for Phase 1)
- Automated dashboard views
- Webhooks or scheduled jobs
- Mobile access optimization
