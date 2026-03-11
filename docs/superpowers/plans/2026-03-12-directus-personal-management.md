# Directus Personal Management — Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up Directus at `app.hidave.co` as a working personal command center — correct data model, initial projects created, and end-to-end workflow verified.

**Architecture:** Use existing Directus collections (projects, tasks, goals). Add missing fields to match the approved data model. Create 3 seed projects. Verify Claude-to-Directus push workflow works end-to-end.

**Tech Stack:** Directus MCP (`@directus/content-mcp`), Directus REST API at `app.hidave.co`

**Spec:** `docs/superpowers/specs/2026-03-12-directus-personal-management-design.md`

---

## Chunk 1: Verify and Fix Collections

### Task 1: Inspect Existing Collections

**Files:** None (read-only verification)

- [ ] **Step 1: List all collections in Directus**

Use Directus MCP to list collections. Confirm `projects`, `tasks`, `goals` exist.

Expected: All 3 collections present.

- [ ] **Step 2: Inspect `projects` collection fields**

Fetch fields for `projects` collection. Note which fields exist and which are missing from the spec:
- Required: `name`, `context` (dropdown), `status` (dropdown), `description`

- [ ] **Step 3: Inspect `tasks` collection fields**

Fetch fields for `tasks` collection. Note which fields exist and which are missing:
- Required: `title`, `status` (dropdown), `priority` (dropdown), `due_date`, `project` (relation), `notes`, `created_by` (dropdown)

- [ ] **Step 4: Commit findings as a note**

No code changes. Proceed to Task 2 with list of missing fields.

---

### Task 2: Fix `projects` Collection

**Files:** Directus schema (via MCP)

- [ ] **Step 1: Add `context` field if missing**

Create field on `projects`:
- Type: `string` (select/dropdown)
- Options: `personal`, `3tsolution`, `ecommerce`
- Required: yes

- [ ] **Step 2: Add `status` field if missing**

Create field on `projects`:
- Type: `string` (select/dropdown)
- Options: `active`, `on-hold`, `done`
- Default: `active`

- [ ] **Step 3: Add `description` field if missing**

Create field on `projects`:
- Type: `text`
- Required: no

- [ ] **Step 4: Verify — fetch projects schema and confirm all 4 fields present**

Expected: `name`, `context`, `status`, `description` all exist.

---

### Task 3: Fix `tasks` Collection

**Files:** Directus schema (via MCP)

- [ ] **Step 1: Add `priority` field if missing**

Create field on `tasks`:
- Type: `string` (select/dropdown)
- Options: `high`, `medium`, `low`
- Default: `medium`

- [ ] **Step 2: Add `due_date` field if missing**

Create field on `tasks`:
- Type: `date`
- Required: no

- [ ] **Step 3: Add `notes` field if missing**

Create field on `tasks`:
- Type: `text`
- Required: no

- [ ] **Step 4: Add `created_by` field if missing**

Create field on `tasks`:
- Type: `string` (select/dropdown)
- Options: `claude`, `manual`
- Default: `manual`

- [ ] **Step 5: Verify `status` field has correct options**

Confirm `status` dropdown includes: `todo`, `in-progress`, `review`, `done`.
If options differ, update them.

- [ ] **Step 6: Verify `project` relation exists**

Confirm `tasks.project` is a M2O relation pointing to `projects` collection.
If missing, create it.

- [ ] **Step 7: Verify — fetch tasks schema and confirm all 7 fields present**

Expected: `title`, `status`, `priority`, `due_date`, `project`, `notes`, `created_by` all exist.

---

## Chunk 2: Seed Data and End-to-End Test

### Task 4: Create Initial Projects

**Files:** Directus data (via MCP)

- [ ] **Step 1: Create `Personal` project**

```json
{
  "name": "Personal",
  "context": "personal",
  "status": "active",
  "description": "Personal tasks and goals outside of work"
}
```

- [ ] **Step 2: Create `Ecommerce` project**

```json
{
  "name": "Ecommerce",
  "context": "ecommerce",
  "status": "active",
  "description": "Solo ecommerce business — target 70M VND/month by end 2026"
}
```

- [ ] **Step 3: Create `3TSolution` project**

```json
{
  "name": "3TSolution",
  "context": "3tsolution",
  "status": "active",
  "description": "ERP software for Kingston outdoor furniture manufacturing"
}
```

- [ ] **Step 4: Verify — list all projects**

Fetch projects list. Confirm 3 records exist with correct `context` and `status` values.

---

### Task 5: End-to-End Workflow Test

**Files:** None (verification only)

- [ ] **Step 1: Create a test task with `review` status**

```json
{
  "title": "[TEST] Workflow verification task",
  "status": "review",
  "priority": "low",
  "notes": "This is a test task created by Claude to verify the push workflow.",
  "created_by": "claude"
}
```

- [ ] **Step 2: Verify task appears in Directus**

Fetch the task by ID. Confirm all fields are set correctly.

- [ ] **Step 3: Instruct Truong to check app.hidave.co**

Tell Truong: "Mở app.hidave.co → Tasks → tìm task '[TEST] Workflow verification task' với status = review. Bạn thấy không?"

Expected: Truong confirms task is visible.

- [ ] **Step 4: Update task status to `done`**

After Truong confirms, update status to `done` to close the test.

- [ ] **Step 5: Delete test task**

Delete the `[TEST]` task to keep data clean.

- [ ] **Step 6: Confirm workflow is live**

Tell Truong: "Setup hoàn tất. Directus sẵn sàng sử dụng. Workflow: Claude show kết quả trong chat → bạn confirm → Claude push lên Directus."
