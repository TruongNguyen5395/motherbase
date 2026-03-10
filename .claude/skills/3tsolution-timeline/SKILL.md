---
name: 3tsolution-timeline
description: Use when someone asks to update 3TSolution timeline, add tasks to 3TS Lark Base, check 3TS project deadlines, manage 3TSolution project tasks, or sync timeline data to Lark.
argument-hint: [action] [project-name]
---

## What This Skill Does

Manages 3TSolution project timelines in Lark Base (Bitable). Supports creating projects, creating/updating/deleting tasks, and handling phase-based task structures.

## Prerequisites

Read these before any operation:
- `.env` for Lark credentials (LARK_APP_ID, LARK_APP_SECRET)
- This skill's [lark-api-reference.md](lark-api-reference.md) for API patterns, table IDs, and field definitions

## Steps

### 1. Authenticate

Get a `tenant_access_token` from Lark API using LARK_APP_ID and LARK_APP_SECRET from `.env`.

### 2. Fetch Current State

Always fetch the current state before making changes:
- GET all records from the **Project** table
- GET all records from the **Công Việc** (tasks) table (paginate if >200)
- Map parent-child relationships via the `Parent items` field
- Map project links via the `Project` DuplexLink field

Present the current state to the user in a structured format grouped by project.

### 3. Determine Action

Based on $ARGUMENTS or conversation context:

| Action | What to do |
|--------|-----------|
| `add` | Create new projects and/or tasks |
| `check` | Show current state, flag overdue deadlines |
| `update` | Modify existing task names, deadlines, or relationships |
| `delete` | Remove tasks (confirm with user first) |
| `restructure` | Change task structure (e.g., add phases) |

If no action is clear, ask the user.

### 4. Handle Phase-Based Projects
s
Some projects have multi-phase workflows (e.g., Demo → Pilot → Fix → Go-live). When you detect a source document with multiple date columns per task:

1. **ASK the user** to confirm the phase names and what each phase means
2. Show an example: "I see columns [X, Y, Z]. Should I create separate tasks like `[X] Task Name`, `[Y] Task Name`?"
3. Only proceed after user confirms the phase names

Phase task naming convention: `[Phase Name] Original Task Name`

Each phase-task is a child of the same parent, with its own deadline.

### 5. Create Records

When creating tasks:

- **Parent tasks** (section headers): No deadline. No `Hoàn Thành?` checkbox.
- **Child tasks**: Must have `Parent items` link, `Deadline`, and `Project` link.
- **Project link**: DuplexLink field — value is an array of record ID strings: `["recXXX"]`
- **Parent items**: SingleLink field — value is an array of record ID strings: `["recXXX"]`
- **Deadline**: Unix timestamp in milliseconds
- **Batch create**: Max 500 records per API call. Use `/records/batch_create`.

### 6. Verify

After any write operation:
- Fetch and display the affected records to confirm correctness
- Report: total records created/updated/deleted, any errors

## Important Rules

1. **Always fetch before modify** — never assume current state
2. **Parent tasks never have deadlines** — only children do
3. **Confirm before delete** — always ask user before deleting records
4. **Batch limits** — max 500 records per batch API call
5. **DuplexLink format** — array of record ID strings, NOT objects
6. **Date format** — Unix timestamp in milliseconds (e.g., `new Date("2026-03-16").getTime()`)
7. **Phase names are dynamic** — never hardcode phase names, always confirm with user
8. **Use Node.js** — all API calls via Node.js https module (no external dependencies)

## Output

After completing an action, summarize:
- What was done (created/updated/deleted)
- Record counts per project
- Any warnings (overdue deadlines, missing data)

## Notes

- If a task has a date range (e.g., "15/04 – 29/4"), use the **last date** as deadline
- Tasks marked "Hoàn Thành" (completed) in source docs: create without deadline, optionally check `Hoàn Thành?`
- Tasks marked "Sẽ chuyển sang giai đoạn bổ sung": create without deadline (placeholder)
- Write all API scripts to `.tmp/` — these are disposable
