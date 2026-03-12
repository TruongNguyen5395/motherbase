# Init Claude Code Project — Version 2 (OpenClaw Memory System)

Set up this project folder as my personal executive assistant / second brain in Claude Code.
This version uses the OpenClaw-inspired memory architecture: identity layer (SOUL + USER), curated long-term memory, and daily logs.

Complete each phase fully before moving to the next.

---

## Phase 1: Create the folder structure

First, initialize a git repo in this folder if one doesn't exist already.

Then create the following structure. Don't put any content in the files yet — just create the skeleton:

```
CLAUDE.md                           # Main brain file (filled in Phase 3)
CLAUDE.local.md                     # Personal overrides (git-ignored)
.gitignore                          # Ignore .env, CLAUDE.local.md, settings.local.json
.claude/
  settings.json                     # Empty JSON object: {}
  rules/                            # Rule files (filled in Phase 3)
  skills/                           # Empty — built organically over time
context/
  SOUL.md                           # Agent identity, personality, values (filled in Phase 3)
  USER.md                           # My consolidated profile (filled in Phase 3)
  MEMORY.md                         # Curated long-term memory (starts with seed content)
  HEARTBEAT.md                      # Session closeout checklist
  memory/                           # Daily working logs
    .gitkeep
templates/
  session-summary.md                # Template for session closeout
references/
  sops/                             # Standard operating procedures (empty)
  examples/                         # Example outputs and style guides (empty)
projects/                           # Active workstreams (empty)
decisions/
  log.md                            # Decision log (append-only)
.tmp/                               # Temporary files — disposable, gitignored
archives/                           # Completed/outdated material (empty)
```

For empty directories, create a `.gitkeep` file inside them so git tracks them.

**`templates/session-summary.md`:**

```markdown
# Session Summary

**Date:**
**Focus:**

## What Got Done
-

## Decisions Made
-

## Open Items / Next Steps
-

## Memory Updates
- Preferences learned:
- Decisions to log:
- Written to daily log:
```

**`decisions/log.md`:**

```markdown
# Decision Log

Append-only. When a meaningful decision is made, log it here.

Format: [YYYY-MM-DD] DECISION: ... | REASONING: ... | CONTEXT: ...

---
```

**`.gitignore`:**

```
.env
CLAUDE.local.md
.claude/settings.local.json
node_modules/
.tmp/
```

**`CLAUDE.local.md`:**

```markdown
# Local Overrides

Personal preferences and overrides that don't get shared via git.
Add anything here that's specific to your local setup.
```

---

## Phase 2: Ask me onboarding questions

Before filling in the files, interview me. Ask ONE SECTION AT A TIME. Wait for my answers before moving to the next section.

### Section 1: About You
- What's your name?
- What's your role/title?
- What's your timezone?
- In one sentence, what do you do?
- What's your #1 priority — the thing everything else should support?

### Section 2: Your Business / Work
- What's your company or business called?
- What are your products, services, or revenue streams?
- What tools do you use day-to-day?
- Do you have any MCP servers connected to Claude Code?

### Section 3: Your Team
- Do you have a team? If yes, how many people?
- Who are the 2-3 key people I should know about? (name, role, when to loop them in)
- Where does your team communicate?
- What's your biggest pain point with managing your team?

### Section 4: Priorities, Goals & Projects
- What are the 3-5 things you're most focused on right now?
- Any deadlines or time-sensitive items I should know about?
- Do you have quarterly goals or milestones you're tracking?
- What active projects or workstreams are you managing?

### Section 5: Communication Preferences
- How do you like information presented?
- Any writing pet peeves?
- What tone do you want internally?
- What tone do you want for external/public-facing content?

### Section 6: What Do You Want Help With?
- What recurring tasks eat up your time?
- What would you hand off to an assistant first?
- Any specific workflows you want to automate or templatize?

### Section 7: Agent Personality
- How direct should I be? (tell you what you don't want to hear vs. softer)
- Should I challenge your assumptions or just execute?
- Any behaviors you explicitly don't want?

---

## Phase 3: Build out the files

Based on my answers, fill in all the files:

### `context/SOUL.md` — Agent Identity

Define who Claude is when working with this person. Based on Section 5 + Section 7 answers.

```markdown
# Soul

You are [Name]'s executive assistant and second brain.

## Identity
- [Trait 1 based on their preferences]
- [Trait 2]
- [Trait 3]

## Values
- [What matters most — derived from their #1 priority]
- [Additional values from their answers]

## Behavioral Rules
- Never guess — ask when unclear
- Never pad responses — concise and accurate always
- Never delete — archive instead (move to .tmp/)
- Never assume context from previous sessions — read the memory files
- Write to context/memory/YYYY-MM-DD.md whenever a meaningful decision or action occurs

## Language
[Note their language preference / style based on answers]
```

### `context/USER.md` — Consolidated Profile

One file that contains everything about the user. Based on Sections 1–4.

Sections to include:
- **Identity** (name, location, timezone, language)
- **Roles** (table: role | company | description)
- **#1 Priority** (one line)
- **Work Context** (each company/business — details, team size, BA work if applicable)
- **Key People** (table: name | role | when to loop in)
- **Communication channels + pain points**
- **Solo/side business** (if applicable)
- **Tools & Integrations** (table)
- **Current Priorities** (numbered list, dated today)
- **Goals & Milestones** (by quarter)

### `context/MEMORY.md` — Seed Long-Term Memory

Start with a seed based on their answers. This grows over time via heartbeat.

```markdown
# Long-Term Memory

Curated facts, preferences, and decisions distilled from daily logs.
Append new entries — never delete.

**Last reviewed:** YYYY-MM-DD

---

## Preferences & Working Style
- [Key preferences from Section 5]

## Key Decisions (Logged)

| Date | Decision |
|------|---------|
| [today] | Initialized OpenClaw memory system for this workspace |

## Open Loops
- [Any unresolved items from onboarding]
```

### `context/HEARTBEAT.md` — Session Closeout Checklist

```markdown
# Heartbeat Checklist

Run at the end of each session (or every 30 min during long sessions).

## Every Session

- [ ] Write key decisions/actions from this session to context/memory/YYYY-MM-DD.md
- [ ] Flag any upcoming deadlines in the next 48 hours
- [ ] If context/memory/YYYY-MM-DD.md has 5+ entries → distill top items to context/MEMORY.md

## Daily (First Session of Day)

- [ ] Create today's log at context/memory/YYYY-MM-DD.md if it doesn't exist
- [ ] Read yesterday's log for carryover context
- [ ] Scan context/MEMORY.md open loops — any resolved or actionable today?

## Weekly (Monday)

- [ ] Review last 7 daily logs — distill patterns into context/MEMORY.md
- [ ] Update priorities in context/USER.md if focus has shifted

---

## Log Format

\`\`\`markdown
# YYYY-MM-DD

## Key Decisions
- [HH:MM] Decision: ...

## Actions & Outcomes
- [HH:MM] Completed: ...

## To Remember Next Session
- ...
\`\`\`
```

### Create today's daily log

Create `context/memory/YYYY-MM-DD.md` (today's date) with the onboarding session logged:

```markdown
# YYYY-MM-DD

## Key Decisions
- [session] Initialized Claude Code second brain with OpenClaw memory system

## Actions & Outcomes
- [session] Completed onboarding — SOUL.md, USER.md, MEMORY.md created

## To Remember Next Session
- Memory system active: load SOUL → USER → MEMORY → today's log → yesterday's log
```

### Rule files in `.claude/rules/`

Based on Section 5 answers:
- **`communication-style.md`** — formatting, tone, pet peeves
- Other domain-specific rules if they emerged (max 3–4 files total, one topic each)

### Projects

If the user listed active projects in Section 4, create a folder for each inside `projects/`. Each gets a `README.md`:
- One-line description
- Current status
- Key dates/deadlines

### `CLAUDE.md` — Main Brain File

Keep UNDER 150 lines. Structure:

```markdown
# [Name]'s Executive Assistant

[One-line identity]. Top priority: [their #1 priority].

---

## Memory System (OpenClaw-style)

Load at session start in this order:

1. @context/SOUL.md — who Claude is (identity, values, rules)
2. @context/USER.md — who [Name] is (profile, roles, priorities, goals)
3. @context/MEMORY.md — curated long-term memory
4. Today's log: context/memory/YYYY-MM-DD.md (if exists)
5. Yesterday's log: context/memory/YYYY-MM-DD.md (if exists)

### Writing to Memory

Write to context/memory/YYYY-MM-DD.md when:
- A meaningful decision is made
- An important action is completed or blocked
- Something must be remembered next session

### Heartbeat

At end of session or every 30 min: run checklist in @context/HEARTBEAT.md.

---

## WAT Framework

- **Workflows** (workflows/): Markdown SOPs
- **Tools** (tools/): Scripts for deterministic execution
- **Templates** (templates/): Reusable templates
- **References** (references/): SOPs and examples

---

## Connected Integrations

[Table of integrations from Section 2 answers]

---

## Skills

Skills live in .claude/skills/. Each skill: .claude/skills/skill-name/SKILL.md
Built organically as recurring workflows emerge.

### Skills to Build (Backlog)

[List from Section 6 answers]

---

## Decision Log

All meaningful decisions → decisions/log.md. Append-only, never edit past entries.

---

## Memory

Two layers:
- **OpenClaw files** (context/) — primary. Loaded at session start. Claude writes daily logs and distills to MEMORY.md.
- **Claude Code auto-memory** — background, automatic. Supplements OpenClaw files.

To remember something: "Remember that I always want X."

---

## Projects

Active workstreams → projects/. Each project has a README.md.

---

## Keeping Context Current

- Update priorities in context/USER.md when focus shifts
- Update goals in context/USER.md each quarter
- Log decisions in decisions/log.md
- Build skills when you notice repeating the same request
- **Never delete — archive** (move to .tmp/ or archives/)
- Daily logs: context/memory/YYYY-MM-DD.md → distilled to context/MEMORY.md
```

---

## Final Step

After everything is created:

1. Show a tree view of every file and folder created
2. One-line summary per file
3. List the "Skills to Build" backlog from Section 6
4. Show this maintenance cheat sheet:

**Keeping Your Assistant Sharp**
- **Every session:** Run heartbeat checklist before closing (or say "run heartbeat")
- **Weekly:** Review context/MEMORY.md open loops — anything resolved?
- **Monthly:** Glance at priorities in context/USER.md — still accurate?
- **Quarterly:** Update goals section in context/USER.md
- **As needed:** Log decisions. Add references. Build new skills.
- **Pro tip:** "Remember that I always prefer X" → Claude writes it to daily log and MEMORY.md permanently.

5. Create the first git commit with all files
6. Ask if they want to build any skills right now

---

## Important Rules for Claude

- Do NOT create any skills yet. Skills directory stays empty after setup.
- Keep CLAUDE.md UNDER 150 lines.
- Use @ imports in CLAUDE.md — never repeat content from context files.
- One rule file = one topic. Max 3–4 rule files to start.
- Ask onboarding questions ONE SECTION AT A TIME. Wait for response before next section.
- If user says "skip", create file with placeholder and move on.
- SOUL.md defines personality — make it specific to the user's answers, not generic.
- USER.md replaces all individual context files (me.md, work.md, team.md, etc.) — one file only.
- Always create today's daily log during setup so the memory system is active from day one.
