package dev.hermeskotlin.ui.preview

import dev.hermeskotlin.core.capabilities.Skill
import dev.hermeskotlin.core.calendar.CalendarAccount
import dev.hermeskotlin.core.calendar.CalendarEvent
import dev.hermeskotlin.core.calendar.CalendarInfo
import dev.hermeskotlin.core.calendar.CalendarSettings
import dev.hermeskotlin.core.calendar.DispatchCalendarOverview
import dev.hermeskotlin.core.calendar.DispatchCalendarTask
import dev.hermeskotlin.core.calendar.DispatchProjectSummary
import dev.hermeskotlin.core.calendar.DispatchTimeBlock
import dev.hermeskotlin.core.calendar.ScheduleOperation
import dev.hermeskotlin.core.calendar.ScheduleProposal
import dev.hermeskotlin.core.calendar.calendarDateFromIso
import dev.hermeskotlin.core.config.ConfigCatalog
import dev.hermeskotlin.core.config.ConfigSchema
import dev.hermeskotlin.ui.calendar.CalendarUiState
import dev.hermeskotlin.ui.calendar.CalendarViewMode
import dev.hermeskotlin.ui.sessions.ConfigPlace
import dev.hermeskotlin.ui.sessions.ConfigUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import dev.hermeskotlin.core.chat.BackgroundProcess
import dev.hermeskotlin.core.chat.Checkpoint
import dev.hermeskotlin.core.chat.CheckpointDiff
import dev.hermeskotlin.core.chat.Checkpoints
import dev.hermeskotlin.core.chat.ContextBreakdown
import dev.hermeskotlin.core.chat.GoalContract
import dev.hermeskotlin.core.chat.GoalControl
import dev.hermeskotlin.core.chat.GoalGate
import dev.hermeskotlin.core.chat.HeartbeatControl
import dev.hermeskotlin.core.chat.LoopControl
import dev.hermeskotlin.core.chat.SessionControl
import dev.hermeskotlin.core.chat.ContextCategory
import dev.hermeskotlin.core.chat.ContextFile
import dev.hermeskotlin.core.chat.SessionUsage
import dev.hermeskotlin.core.cron.CronJob
import dev.hermeskotlin.core.cron.DeliveryTarget
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.ui.sessions.ScheduledUiState
import dev.hermeskotlin.ui.sessions.said
import dev.hermeskotlin.ui.resources.Res
import dev.hermeskotlin.ui.resources.sessions_hub_blocked
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import dev.hermeskotlin.core.gateway.CheckNote
import dev.hermeskotlin.core.gateway.CheckStage
import dev.hermeskotlin.core.gateway.StageResult
import dev.hermeskotlin.core.insights.ModelUsage
import dev.hermeskotlin.core.insights.SkillUsage
import dev.hermeskotlin.core.insights.SkillsBlock
import dev.hermeskotlin.core.insights.ToolUsage
import dev.hermeskotlin.core.insights.UsageDay
import dev.hermeskotlin.core.insights.UsageReport
import dev.hermeskotlin.core.insights.UsageTotals
import dev.hermeskotlin.core.insights.isoDateOf
import dev.hermeskotlin.core.models.ModelCatalog
import dev.hermeskotlin.core.models.ModelOption
import dev.hermeskotlin.core.models.ModelProvider
import dev.hermeskotlin.core.sessions.SessionTotals
import dev.hermeskotlin.ui.chat.CheckpointsState
import dev.hermeskotlin.ui.chat.ProcessesState
import dev.hermeskotlin.ui.chat.UsageSheetState
import dev.hermeskotlin.ui.sessions.CapabilitiesUiState
import dev.hermeskotlin.ui.sessions.HubDetail
import dev.hermeskotlin.ui.sessions.SkillsHubUiState
import dev.hermeskotlin.core.capabilities.HubFinding
import dev.hermeskotlin.core.capabilities.HubPreview
import dev.hermeskotlin.core.capabilities.HubScan
import dev.hermeskotlin.core.capabilities.HubSkill
import dev.hermeskotlin.ui.sessions.InsightsUiState
import dev.hermeskotlin.ui.sessions.JobEditor
import dev.hermeskotlin.ui.sessions.Loadable
import kotlin.time.Clock
import dev.hermeskotlin.core.plugins.DashboardPlugin
import dev.hermeskotlin.core.gateway.GatewayList
import dev.hermeskotlin.core.gateway.SavedGateway
import dev.hermeskotlin.ui.GatewayChoices
import dev.hermeskotlin.core.journey.JourneyGraph
import dev.hermeskotlin.core.journey.JourneyNode
import dev.hermeskotlin.ui.journey.JourneyState
import dev.hermeskotlin.core.plugins.PluginTab
import dev.hermeskotlin.core.tasks.DirectusConfigStatus
import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.PlanAction
import dev.hermeskotlin.core.tasks.PlanParam
import dev.hermeskotlin.core.tasks.StagedPlan
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.ui.tasks.ChannelChatLine
import dev.hermeskotlin.ui.tasks.TasksUiState

/** Made-up data for the sidebar pages and the chat's sheets, in the same home-server story as [ChatSamples]. */
internal object PageSamples {

    /** A month of use, busier on weekdays, ending today. */
    fun insights(today: Long = Clock.System.now().toEpochMilliseconds() / 86_400_000): InsightsUiState {
        val pattern = listOf(4, 7, 5, 9, 6, 2, 1, 5, 8, 6, 11, 7, 3, 1, 6, 9, 7, 12, 8, 2, 2, 7, 10, 8, 14, 9, 3, 2, 8, 6)
        val daily = pattern.mapIndexed { i, weight ->
            UsageDay(
                day = isoDateOf(today - (pattern.size - 1 - i)),
                inputTokens = weight * 182_000L,
                outputTokens = weight * 9_400L,
                cacheReadTokens = weight * 410_000L,
                estimatedCost = weight * 0.61,
                sessions = (weight + 1) / 2,
                apiCalls = weight * 14,
            )
        }
        val report = UsageReport(
            daily = daily,
            byModel = listOf(
                ModelUsage("claude-sonnet-5-5", inputTokens = 29_100_000, outputTokens = 1_480_000, estimatedCost = 98.4, sessions = 61),
                ModelUsage("claude-haiku-4-5", inputTokens = 6_200_000, outputTokens = 410_000, estimatedCost = 7.9, sessions = 38),
                ModelUsage("claude-opus-5-5", inputTokens = 1_900_000, outputTokens = 120_000, estimatedCost = 17.6, sessions = 4),
            ),
            totals = UsageTotals(
                input = daily.sumOf { it.inputTokens },
                output = daily.sumOf { it.outputTokens },
                cacheRead = daily.sumOf { it.cacheReadTokens },
                estimatedCost = daily.sumOf { it.estimatedCost },
                sessions = daily.sumOf { it.sessions },
                apiCalls = daily.sumOf { it.apiCalls },
            ),
            periodDays = 30,
            skillsBlock = SkillsBlock(listOf(SkillUsage("homelab-runbook", 23), SkillUsage("weekly-report", 9), SkillUsage("grafana", 4))),
            tools = listOf(ToolUsage("terminal", 812), ToolUsage("read_file", 344), ToolUsage("patch", 158), ToolUsage("web_search", 97), ToolUsage("delegate_task", 31)),
        )
        return InsightsUiState(report = report, loading = false)
    }

    val capabilities = CapabilitiesUiState(
        skills = Loadable(
            listOf(
                Skill("homelab-runbook", "How the NAS, router and backups are set up, and how to fix them", category = "homelab", usage = 23),
                Skill("grafana", "Build and edit Grafana dashboards from Prometheus metrics", category = "homelab", provenance = "hub", usage = 4),
                Skill("smart-disks", "Read SMART data and judge whether a disk is failing", category = "homelab", enabled = false),
                Skill("weekly-report", "Write the Monday email of backup sizes and disk health", category = "writing", usage = 9),
                Skill("arxiv", "Search arXiv and summarize papers", category = "research", provenance = "bundled"),
                Skill("github-pr", "Open, review and merge pull requests with gh", category = "software", provenance = "bundled"),
                Skill("python-debug", "Read a traceback and find the failing line", category = "software", provenance = "bundled"),
            ),
        ),
    )

    private val hubFeatured = listOf(
        HubSkill("grafana", "Build and edit Grafana dashboards from Prometheus metrics", source = "github", identifier = "acme/skills/grafana", trustLevel = "trusted"),
        HubSkill("smart-disks", "Read SMART data and judge whether a disk is failing", source = "github", identifier = "acme/skills/smart-disks", trustLevel = "community"),
    )
    private val hubOfficial = listOf(
        HubSkill("arxiv", "Search arXiv and summarize papers", source = "official", identifier = "official/research/arxiv", trustLevel = "builtin", category = "research"),
        HubSkill("github-pr", "Open, review and merge pull requests with gh", source = "official", identifier = "official/software/github-pr", trustLevel = "builtin", category = "software"),
    )
    private val hubScan = HubScan(
        name = "smart-disks",
        identifier = "acme/skills/smart-disks",
        trustLevel = "community",
        verdict = "caution",
        summary = "2 findings",
        policy = "ask",
        policyReason = "Community skills with findings need your yes.",
        findings = listOf(
            HubFinding("medium", "exec", "scripts/check.sh", 12, "Runs a shell command built from input"),
            HubFinding("low", "network", "SKILL.md", 40, "Fetches a file from the web"),
        ),
    )
    private val hubPreview = HubPreview(
        name = "smart-disks",
        description = "Read SMART data and judge whether a disk is failing",
        identifier = "acme/skills/smart-disks",
        trustLevel = "community",
        tags = listOf("disks", "homelab"),
        skillMd = "# smart-disks\n\nRead SMART data with `smartctl` and say whether a disk is failing.\n\n- Reallocated sectors\n- Pending sectors",
        files = listOf("SKILL.md", "scripts/check.sh"),
    )

    val skillsHub = SkillsHubUiState(
        featured = Loadable(hubFeatured),
        official = Loadable(hubOfficial),
        installed = setOf("acme/skills/grafana"),
    )
    val skillsHubDetail = skillsHub.copy(detail = HubDetail(hubFeatured[1], preview = hubPreview, scan = hubScan.copy(verdict = "safe", policy = "allow", findings = emptyList())))
    val skillsHubCaution = skillsHub.copy(confirm = hubScan)
    val skillsHubBlocked = skillsHub.copy(
        detail = HubDetail(hubFeatured[1], preview = hubPreview, scan = hubScan.copy(verdict = "dangerous", policy = "block", summary = "1 high-risk finding", findings = hubScan.findings.take(1))),
        message = said(Res.string.sessions_hub_blocked, "smart-disks"),
    )

    /** Hermes's settings on a sample gateway, open at [place]: the sections, or one of them by id. */
    fun config(place: String? = null): ConfigUiState {
        val schema = Json { ignoreUnknownKeys = true }.decodeFromString<ConfigSchema>(CONFIG_SCHEMA)
        return ConfigUiState(
            schema = schema,
            values = Json.parseToJsonElement(CONFIG_VALUES).jsonObject,
            view = place?.let { id -> ConfigPlace.Section(ConfigCatalog.sections.first { it.id == id }) } ?: ConfigPlace.Overview,
            saving = setOf("voice.gpt_live.voice").takeIf { place == "voice" }.orEmpty(),
        )
    }

    private val CONFIG_SCHEMA = """{"category_order":["general","agent","terminal","display","delegation","memory","compression","security","voice","tts","stt"],"fields":{
        "model":{"type":"string","description":"Default model","category":"general"},
        "model_context_length":{"type":"number","description":"Context window override","category":"general"},
        "fallback_providers":{"type":"list","description":"Fallback Providers","category":"general"},
        "toolsets":{"type":"list","description":"Toolsets","category":"general"},
        "timezone":{"type":"select","description":"IANA timezone","category":"general","options":["America/New_York","Europe/London","Asia/Tokyo"],"searchable":true,"clearable":true},
        "display.personality":{"type":"string","description":"Personality","category":"display"},
        "display.show_reasoning":{"type":"boolean","description":"Show reasoning","category":"display"},
        "approvals.mode":{"type":"select","description":"Dangerous command approval mode","category":"security","options":["manual","smart","off"]},
        "approvals.timeout":{"type":"number","description":"Approvals → Timeout","category":"security"},
        "command_allowlist":{"type":"list","description":"Command Allowlist","category":"general"},
        "security.redact_secrets":{"type":"boolean","description":"Security → Redact Secrets","category":"security"},
        "checkpoints.enabled":{"type":"boolean","description":"Checkpoints → Enabled","category":"agent"},
        "agent.max_turns":{"type":"number","description":"Agent → Max Turns","category":"agent"},
        "memory.memory_enabled":{"type":"boolean","description":"Memory → Memory Enabled","category":"memory"},
        "memory.user_profile_enabled":{"type":"boolean","description":"Memory → User Profile Enabled","category":"memory"},
        "memory.provider":{"type":"select","description":"Memory provider plugin","category":"memory","options":["","hindsight","honcho"]},
        "compression.enabled":{"type":"boolean","description":"Compression → Enabled","category":"compression"},
        "compression.threshold":{"type":"number","description":"Compression → Threshold","category":"compression"},
        "voice.voice_chat_mode":{"type":"string","description":"Voice chat mode","category":"voice"},
        "voice.gpt_live.voice":{"type":"string","description":"GPT-Live voice","category":"voice"},
        "voice.auto_tts":{"type":"boolean","description":"Voice → Auto Tts","category":"voice"},
        "tts.provider":{"type":"select","description":"Text-to-speech provider","category":"tts","options":["edge","elevenlabs","openai","xai"]},
        "tts.openai.voice":{"type":"string","description":"OpenAI voice","category":"tts"},
        "tts.openai.model":{"type":"string","description":"OpenAI model","category":"tts"},
        "stt.enabled":{"type":"boolean","description":"Stt → Enabled","category":"stt"},
        "stt.local.model":{"type":"select","description":"Local faster-whisper model size","category":"stt","options":["base","small","medium"]},
        "delegation.max_concurrent_children":{"type":"number","description":"Delegation → Max Concurrent Children","category":"delegation"},
        "terminal.backend":{"type":"select","description":"Terminal execution backend","category":"terminal","options":["local","docker","ssh"]}}}"""

    private val CONFIG_VALUES = """{"model":"kimi-k3","model_context_length":0,
        "fallback_providers":[{"provider":"opencode-go","model":"glm-5.3"},{"provider":"openrouter","model":"google/gemini-3-flash"}],
        "toolsets":["hermes-cli"],"timezone":"America/New_York","display":{"personality":"","show_reasoning":false},
        "approvals":{"mode":"smart","timeout":120},"command_allowlist":["git status","ls"],"security":{"redact_secrets":true},
        "checkpoints":{"enabled":true},"agent":{"max_turns":90},
        "memory":{"memory_enabled":true,"user_profile_enabled":true,"provider":"hindsight"},"compression":{"enabled":true,"threshold":0.5},
        "voice":{"voice_chat_mode":"gpt-live","gpt_live":{"voice":"willow"},"auto_tts":false},
        "tts":{"provider":"openai","openai":{"voice":"nova","model":"gpt-4o-mini-tts"}},"stt":{"enabled":true,"local":{"model":"small"}},
        "delegation":{"max_concurrent_children":3},"terminal":{"backend":"local"}}"""

    val deliveryTargets = listOf(
        DeliveryTarget("local", "Local (save only)"),
        DeliveryTarget("telegram", "Telegram"),
        DeliveryTarget("email", "Email"),
        DeliveryTarget("discord", "Discord", homeTargetSet = false),
    )

    /** Three jobs, one failing and one paused; with [open], the first one's page and its runs. */
    fun scheduled(open: Boolean = false): ScheduledUiState {
        val now = Clock.System.now()
        val nowSeconds = now.toEpochMilliseconds() / 1000.0
        fun ago(minutes: Int) = nowSeconds - minutes * 60
        return ScheduledUiState(
            jobs = listOf(
                CronJob(
                    "j1",
                    name = JOB_NAME,
                    prompt = JOB_PROMPT,
                    scheduleDisplay = JOB_SCHEDULE,
                    nextRunAt = (now + 21.hours).toString(),
                    lastStatus = "ok",
                    deliver = "telegram",
                ),
                CronJob(
                    "j2",
                    name = "Renew the TLS certificates",
                    prompt = "Renew any certificate that expires within 30 days and reload the proxy.",
                    scheduleDisplay = "0 3 * * 0",
                    nextRunAt = (now + 3.days).toString(),
                    lastStatus = "error",
                    lastError = "certbot: DNS challenge timed out for photos.home.example",
                    deliver = "email",
                ),
                CronJob("j3", name = "Weekly meal plan", prompt = "Plan next week's dinners from what's in season.", scheduleDisplay = "0 18 * * 5", state = "paused", deliver = "local"),
            ),
            loading = false,
            openJobId = "j1".takeIf { open },
            runs = listOf(
                SessionSummary("r1", lastActive = ago(1), isActive = true, messageCount = 3, source = "cron"),
                SessionSummary("r2", preview = "All good: backup 412 GB, NAS 61% full, every disk healthy.", lastActive = ago(60 * 24), messageCount = 6, source = "cron"),
                SessionSummary("r3", preview = "Disk 3 reports 8 reallocated sectors. Worth replacing soon.", lastActive = ago(60 * 24 * 2), messageCount = 9, source = "cron"),
            ),
            deliveryTargets = deliveryTargets,
        )
    }

    val jobEditor = JobEditor(deliver = "telegram")
    const val JOB_PROMPT = "Check last night's backup, the free space on the NAS and the SMART health of each disk. Send me a short summary, and say first if anything needs me."
    const val JOB_SCHEDULE = "0 9 * * 1-5"
    const val JOB_NAME = "Morning server check"

    val usage = UsageSheetState(
        totals = SessionTotals(
            inputTokens = 184_200,
            outputTokens = 6_120,
            cacheReadTokens = 512_800,
            reasoningTokens = 2_400,
            apiCalls = 14,
            estimatedCostUsd = 0.86,
            model = "claude-sonnet-5-5",
        ),
        breakdown = ContextBreakdown(
            categories = listOf(
                ContextCategory("system_prompt", "System prompt", 3_800),
                ContextCategory("tool_definitions", "Tool definitions", 14_200),
                ContextCategory("rules", "Rules", 1_900),
                ContextCategory("skills", "Skills", 4_600),
                ContextCategory("mcp", "MCP tools", 6_300),
                ContextCategory("memory", "Memory", 2_100),
                ContextCategory("conversation", "Conversation", 38_700),
            ),
            used = 71_600,
            max = 200_000,
            estimated = false,
            files = listOf(
                ContextFile("AGENTS.md", "~/homelab/AGENTS.md", 1_200, loaded = true),
                ContextFile("homelab-runbook", "~/.hermes/skills/homelab-runbook/SKILL.md", 2_900, loaded = true),
            ),
        ),
        limits = listOf("Weekly limit: 38% used, resets Monday"),
    )

    val liveUsage = SessionUsage(input = 184_200, output = 6_120, contextUsed = 71_600, contextMax = 200_000, contextPercent = 36)

    val processes = ProcessesState(
        processes = listOf(
            BackgroundProcess(
                id = "proc_1",
                command = "rsync -a --info=progress2 /srv/photos/ /mnt/nas/backup/2026-10-03/",
                cwd = "/opt/backup",
                pid = 48211,
                running = true,
                uptimeSeconds = 754,
                exitCode = null,
                output = "    412.88G  61%   88.41MB/s    1:18:02 (xfr#18214, to-chk=11902/31040)\n" +
                    "    418.02G  62%   89.17MB/s    1:15:31 (xfr#18522, to-chk=11594/31040)\n" +
                    "    423.40G  63%   90.02MB/s    1:12:58 (xfr#18871, to-chk=11245/31040)",
            ),
            BackgroundProcess(
                id = "proc_2",
                command = "python3 -m http.server 8080",
                cwd = "/opt/backup/report",
                pid = 48302,
                running = true,
                uptimeSeconds = 312,
                exitCode = null,
                output = "Serving HTTP on 0.0.0.0 port 8080 (http://0.0.0.0:8080/) ...",
            ),
            BackgroundProcess(
                id = "proc_3",
                command = "smartctl -t long /dev/sdb",
                cwd = null,
                pid = 47990,
                running = false,
                uptimeSeconds = null,
                exitCode = 0,
                output = "Testing has begun.\nPlease wait 255 minutes for test to complete.",
            ),
        ),
    )

    /**
     * The backup chat's automation, after the gateway's real goal_loop_heartbeat snapshot: a goal four
     * turns in with sub-goals and a check, the loop it holds back, and a heartbeat. Times follow the
     * clock so the next run reads as a time of day.
     */
    val control: SessionControl
        get() {
            val now = Clock.System.now().toEpochMilliseconds() / 1000.0
            return SessionControl(
                goal = GoalControl(
                    title = "Get the test suite green on the feat/goals branch",
                    status = "active",
                    turnsUsed = 4,
                    maxTurns = 20,
                    contract = GoalContract(
                        outcome = "Core tests pass",
                        verification = "./gradlew :shared:core:testAndroidHostTest",
                        constraints = "No new dependencies",
                        boundaries = "",
                        stopWhen = "",
                    ),
                    subgoals = listOf("Fix the flaky reconnect test", "Pin the wire contract in a test"),
                    gates = listOf(
                        GoalGate(
                            command = "./gradlew :shared:core:testAndroidHostTest",
                            timeoutSeconds = 600,
                            maxRetries = 2,
                            attempts = 2,
                            lastExitCode = 1,
                        ),
                    ),
                    pausedReason = null,
                    lastVerdict = "continue",
                    lastReason = "Two tests still fail in ChatSessionTest.",
                    waitBarrier = null,
                    updatedAt = now - 9 * 60,
                ),
                loop = LoopControl(
                    prompt = "Check the deploy log and report errors",
                    status = "active",
                    selfPaced = false,
                    intervalSeconds = 1800.0,
                    times = 6,
                    until = "",
                    ticksFired = 2,
                    lastFiredAt = now - 12 * 60,
                    nextDueAt = now + 18 * 60,
                    awaitingResponse = false,
                    deferredByGoal = true,
                    pausedReason = null,
                    lastStopReason = null,
                ),
                heartbeat = HeartbeatControl(
                    prompt = "Any new GitHub notifications?",
                    status = "active",
                    intervalSeconds = 3600,
                    lastFiredAt = now - 42 * 60,
                    fireCount = 3,
                ),
                revision = "dced25cac75f65f0dce50607239ba5a41dbc0b9b91d1c4e75503dde64af77467",
            )
        }

    /** Settings → Check connection, where the server and sign-in pass but the live link doesn't. */
    val connectionCheck = mapOf(
        CheckStage.Server to StageResult.Passed(CheckNote(CheckNote.Kind.ServerAnswers, listOf("0.9.0", "hermes.example.ts.net"))),
        CheckStage.SignIn to StageResult.Passed(CheckNote(CheckNote.Kind.SignedInAs, listOf("Alex Morgan"))),
        CheckStage.Live to StageResult.Failed(
            problem = CheckNote(CheckNote.Kind.ChatRefused, listOf("400")),
            fix = CheckNote(CheckNote.Kind.FixAllowUpgrades),
        ),
    )

    /** Three snapshots of the backup chat's folder, the newest opened on what changed since. */
    val checkpoints = CheckpointsState(
        list = Checkpoints(
            enabled = true,
            checkpoints = listOf(
                Checkpoint("9f2c1ab47e0d55aa", "2026-10-08T20:41:03+03:00", "before patch backup.sh"),
                Checkpoint("4be07d19c2a3e810", "2026-10-08T20:36:47+03:00", "before write_file prune.sh"),
                Checkpoint("11aa22bb33cc44dd", "2026-10-08T19:58:12+03:00", "before patch crontab"),
            ),
        ),
        diffs = mapOf(
            "9f2c1ab47e0d55aa" to CheckpointDiff(
                stat = " backup.sh | 4 +++-\n 1 file changed, 3 insertions(+), 1 deletion(-)",
                diff = """--- a/backup.sh
+++ b/backup.sh
@@ -12,7 +12,9 @@ DEST=/mnt/nas/backups
 rsync -a --delete "${'$'}SRC" "${'$'}DEST/${'$'}(date +%F)"
-echo "done"
+# Keep the last 14 snapshots.
+ls -1d "${'$'}DEST"/20* | head -n -14 | xargs -r rm -rf
+echo "done, ${'$'}(ls -1d "${'$'}DEST"/20* | wc -l) kept"
 exit 0""",
            ),
        ),
    )

    /** Three providers, enough models that the picker offers search; OpenRouter also offers two Anthropic models. */
    val modelCatalog = ModelCatalog(
        providers = listOf(
            ModelProvider(
                "anthropic", "Anthropic",
                // Opus has a priority tier, so the sheet shows its Fast mode card.
                listOf("claude-opus-5-5", "claude-sonnet-5", "claude-haiku-4-5").map {
                    ModelOption(it, "anthropic", reasoning = true, fast = it == "claude-opus-5-5")
                },
            ),
            ModelProvider(
                "openrouter", "OpenRouter",
                listOf(
                    "anthropic/claude-opus-5.5" to "$5 / $25", "anthropic/claude-sonnet-5" to "$3 / $15",
                    "z-ai/glm-5.3-flash" to "$0.06 / $0.40", "deepseek/deepseek-v4" to "$0.27 / $1.10",
                    "moonshotai/kimi-k2.6" to "$0.60 / $2.50", "xiaomi/mimo-v2.6-pro" to "$0.40 / $1.60",
                    "qwen/qwen3-235b-a22b" to "$0.13 / $0.60", "google/gemini-3.8-flash" to "$0.30 / $2.50",
                ).map { (id, price) -> ModelOption(id, "openrouter", reasoning = true, price = price) },
            ),
            ModelProvider(
                "openai-codex", "OpenAI Codex",
                listOf("gpt-6.1-sol", "gpt-6.1-mini", "gpt-6-codex", "gpt-6-nano").map { ModelOption(it, "openai-codex", reasoning = true) },
            ),
        ),
        currentModel = "claude-opus-5-5",
        currentProvider = "anthropic",
    )

    /** Two models starred, one from each of two providers. */
    val starredModels = listOf("anthropic/claude-opus-5-5", "openrouter/z-ai/glm-5.3-flash")

    /** Dashboard plugins with a page, one bundled with Hermes and the rest installed on the gateway. */
    val plugins = listOf(
        DashboardPlugin("kanban", "Kanban", "Boards the agent and you share: cards move as work gets done.", "1.4.0", "bundled", PluginTab("/kanban")),
        DashboardPlugin("achievements", "Achievements", "Badges for streaks, first runs and long tasks.", "0.3.2", "user", PluginTab("/achievements")),
        DashboardPlugin("homelab-status", "Homelab status", "Disks, backups and containers on one page.", "2.0.1", "user", PluginTab("/homelab")),
    )

    /** Three saved gateways: the home one in use and primary, a named one, and one signed out. */
    val gateways = GatewayChoices(
        GatewayList(
            gateways = listOf(
                SavedGateway("https://hermes.example.ts.net", name = "homelab"),
                SavedGateway("https://work-hermes.example.com", name = "Work"),
                SavedGateway("http://192.168.1.20:9119"),
            ),
            currentUrl = "https://hermes.example.ts.net",
            primaryUrl = "https://hermes.example.ts.net",
        ),
        signedIn = setOf("https://hermes.example.ts.net", "https://work-hermes.example.com"),
    )

    /** What the agent has picked up over two months: skills it wrote and memories it keeps. */
    fun journey(now: Long = Clock.System.now().epochSeconds): JourneyState {
        val day = 86_400L
        return JourneyState(
            JourneyGraph(
                listOf(
                    JourneyNode("s1", "rotate-nas-snapshots", timestamp = now - 1 * day, category = "homelab", useCount = 6, pinned = true),
                    JourneyNode("m1", "Backups go to /mnt/nas/backups and keep 14 snapshots", kind = "memory", timestamp = now - 2 * day, memorySource = "MEMORY.md"),
                    JourneyNode("s2", "summarize-changelog", timestamp = now - 5 * day, category = "writing", useCount = 3),
                    JourneyNode("m2", "Prefers short answers with the command first", kind = "memory", timestamp = now - 40 * day, memorySource = "USER.md"),
                    JourneyNode("s3", "check-disk-health", timestamp = now - 45 * day, category = "homelab", useCount = 1),
                ),
            ),
        )
    }

    /** Picked lately, newest first. */
    val recentModels = listOf("openai-codex/gpt-6.1-sol", "openrouter/deepseek/deepseek-v4", "anthropic/claude-opus-5-5")

    /** Directus Command Board sample with priority strip, project bento boxes, Room+Session channels, and staged action card. */
    fun tasks(selectedTaskId: String? = "task-1"): TasksUiState {
        val projects = listOf(
            DirectusProject(id = "proj-homelab", name = "Homelab", workspaceCwd = "/srv/homelab", defaultBots = listOf("ops"), color = "#3B82F6", sort = 1),
            DirectusProject(id = "proj-api", name = "Backend API", workspaceCwd = "/srv/api", defaultBots = listOf("dev", "reviewer"), color = "#10B981", sort = 2),
        )
        val plan = StagedPlan(
            summary = "Snapshots aged out on two nodes; prune and verify checksums.",
            question = "Pick retention window and agent to run maintenance:",
            params = listOf(
                PlanParam(key = "retention", label = "Keep", options = listOf("7 days", "14 days", "30 days"), default = "14 days"),
                PlanParam(key = "mode", label = "Mode", options = listOf("Dry run", "Prune & verify"), default = "Prune & verify"),
                PlanParam(key = "bot", label = "Agent", options = listOf("@ops", "@reviewer"), default = "@ops"),
            ),
            actions = listOf(
                PlanAction(label = "Run maintenance", description = "Execute cleanup and log result to Directus", instruction = "Run snapshot pruning"),
                PlanAction(label = "Inspect nodes first", description = "Collect disk report only", instruction = "Inspect disk usage before pruning"),
            ),
        )
        val tasks = listOf(
            DirectusTask(
                id = "task-1",
                title = "Prune aged backup snapshots",
                description = "Two storage nodes are above 85% utilization.",
                status = DirectusTask.STATUS_TODO,
                priority = "high",
                context = TaskContext(
                    taskId = "task-1",
                    projectId = "proj-homelab",
                    assignedBots = listOf("ops", "reviewer"),
                    channels = listOf(
                        TaskChannel(type = TaskChannel.TYPE_ROOM, id = "task-task-1", label = "Room (@ops, @reviewer)"),
                        TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-ops-1", label = "Session (@ops)", profile = "ops"),
                    ),
                    agentState = TaskContext.STATE_NEEDS_YOU,
                    stagedPlan = plan,
                ),
            ),
            DirectusTask(
                id = "task-2",
                title = "Audit gateway rate-limit middleware",
                status = DirectusTask.STATUS_IN_PROGRESS,
                priority = "medium",
                context = TaskContext(
                    taskId = "task-2",
                    projectId = "proj-api",
                    assignedBots = listOf("dev"),
                    agentState = TaskContext.STATE_RUNNING,
                ),
            ),
        )
        return TasksUiState(
            config = DirectusConfigStatus(configured = true, url = "https://directus.example.com", tokenSet = true, tokenPreview = "dir_••••1234", source = "file"),
            projects = projects,
            tasks = tasks,
            bots = BotSamples.roster.bots,
            selectedTaskId = selectedTaskId,
            channelMessages = listOf(
                ChannelChatLine("l1", "ops", "Scanned backup volumes; 18 snapshots eligible for pruning.", fromUser = false, profile = "ops"),
                ChannelChatLine("l2", null, "@ops Hold 14 days by default unless critical.", fromUser = true),
            ),
        )
    }

    fun calendar(viewMode: CalendarViewMode = CalendarViewMode.FOUR_DAYS): CalendarUiState {
        val anchor = calendarDateFromIso("2026-11-26")!!
        val calendars = listOf(
            CalendarInfo(
                id = "cal-primary",
                accountId = "acc-1",
                name = "Work Primary",
                colorHex = "#E67C73",
                isPrimary = true,
                writable = true,
                provider = "google",
            ),
            CalendarInfo(
                id = "cal-mb",
                accountId = "acc-1",
                name = "MotherBase",
                colorHex = "#0F766E",
                isTaskTarget = true,
                writable = true,
                provider = "google",
            ),
            CalendarInfo(
                id = "cal-holidays",
                accountId = "acc-2",
                name = "Public Holidays",
                colorHex = "#10B981",
                writable = false,
                provider = "ics",
            ),
        )
        val events = listOf(
            CalendarEvent(
                id = "ev-1",
                calendarId = "cal-primary",
                accountId = "acc-1",
                title = "Engineering Standup",
                startAt = "2026-11-26T02:00:00Z",
                endAt = "2026-11-26T03:00:00Z",
                timezone = "Asia/Ho_Chi_Minh",
                location = "Room A",
                colorHex = "#E67C73",
                calendarName = "Work Primary",
            ),
            CalendarEvent(
                id = "ev-2",
                calendarId = "cal-mb",
                accountId = "acc-1",
                title = "[Task] Release Packaging Audit",
                startAt = "2026-11-26T07:00:00Z",
                endAt = "2026-11-26T08:30:00Z",
                timezone = "Asia/Ho_Chi_Minh",
                sourceKind = "dispatch_block",
                taskId = "task-1",
                timeBlockId = "blk-1",
                colorHex = "#0F766E",
                calendarName = "MotherBase",
            ),
            CalendarEvent(
                id = "ev-3",
                calendarId = "cal-holidays",
                accountId = "acc-2",
                title = "Company Offsite",
                allDay = true,
                startDate = "2026-11-27",
                endDate = "2026-11-29",
                colorHex = "#10B981",
                calendarName = "Public Holidays",
                calendarWritable = false,
            ),
        )
        val dispatchTasks = listOf(
            DispatchCalendarTask(
                id = "task-1",
                title = "Release Packaging Audit",
                status = "in_progress",
                dueDate = "2026-11-26",
                estimatedMinutes = 90,
                timeBlocks = listOf(
                    DispatchTimeBlock(
                        id = "blk-1",
                        taskId = "task-1",
                        label = "Release Packaging Audit",
                        startAt = "2026-11-26T07:00:00Z",
                        endAt = "2026-11-26T08:30:00Z",
                    ),
                ),
            ),
            DispatchCalendarTask(
                id = "task-2",
                title = "Review Gateway Schema PR",
                status = "todo",
                dueDate = "2026-11-28",
                estimatedMinutes = 45,
            ),
        )
        val proposal = ScheduleProposal(
            id = "prop-1",
            title = "Afternoon Focus Block",
            summary = "Schedule PR review in open afternoon window",
            targetDate = "2026-11-26",
            status = ScheduleProposal.STATUS_STAGED,
            operations = listOf(
                ScheduleOperation(
                    opId = "op-1",
                    kind = "schedule_task",
                    title = "Review Gateway Schema PR",
                    taskId = "task-2",
                    startAt = "2026-11-26T10:00:00Z",
                    endAt = "2026-11-26T10:45:00Z",
                    reason = "Open slot before end of workday",
                ),
            ),
        )
        return CalendarUiState(
            viewMode = viewMode,
            anchorDate = anchor,
            todayDate = anchor,
            currentMinuteOfDay = 15 * 60 + 30,
            settings = CalendarSettings(timezone = "Asia/Ho_Chi_Minh"),
            accounts = listOf(
                CalendarAccount(
                    id = "acc-1",
                    provider = "google",
                    authMode = "oauth",
                    label = "Work Google",
                    email = "team@example.com",
                ),
            ),
            calendars = calendars,
            events = events,
            dispatchOverview = DispatchCalendarOverview(
                configured = true,
                projects = listOf(DispatchProjectSummary(id = "proj-1", name = "Core", color = "#0284C7")),
                tasks = dispatchTasks,
                timeBlocks = dispatchTasks.first().timeBlocks,
            ),
            proposals = listOf(proposal),
        )
    }
}
