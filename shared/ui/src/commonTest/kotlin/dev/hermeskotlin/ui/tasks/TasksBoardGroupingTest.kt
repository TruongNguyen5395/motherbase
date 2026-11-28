package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.EisenhowerQuadrant
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.core.tasks.TaskReplyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TasksBoardGroupingTest {

    private val today = epochDayFromCivilIso("2026-11-18")!!

    private fun task(
        id: String,
        title: String = id,
        status: String = DirectusTask.STATUS_TODO,
        priority: String = "medium",
        dueDate: String? = null,
        parentId: String? = null,
        projectId: String? = "p1",
        agentState: String = TaskContext.STATE_IDLE,
        impact: String? = null,
        urgency: String? = null,
        channels: List<TaskChannel> = emptyList(),
        replyStatus: TaskReplyStatus? = null,
    ) = DirectusTask(
        id = id,
        title = title,
        status = status,
        priority = priority,
        dueDate = dueDate,
        parentId = parentId,
        context = TaskContext(
            taskId = id,
            projectId = projectId,
            agentState = agentState,
            channels = channels,
            impact = impact,
            urgency = urgency,
        ),
        replyStatus = replyStatus,
    )

    @Test
    fun overdueDaysRespectsDoneStatusAndIsoFormats() {
        val late = task("t1", dueDate = "2026-11-15T18:00:00")
        val dueToday = task("t2", dueDate = "2026-11-18")
        val doneLate = task("t3", status = DirectusTask.STATUS_DONE, dueDate = "2026-11-10T10:00:00")
        val upcoming = task("t4", dueDate = "2026-11-20T17:00:00")

        assertEquals(3, taskOverdueDays(late, today))
        assertEquals(0, taskOverdueDays(dueToday, today))
        assertEquals(0, taskOverdueDays(doneLate, today))
        assertEquals(0, taskOverdueDays(upcoming, today))
        assertNull(parseDueEpochDay(null))
        assertNull(parseDueEpochDay("not-a-date"))
    }

    @Test
    fun matrixAutoBoostsUnplacedUrgencyNearDueDateWhileRespectingExplicitPlacement() {
        val unplacedFuture = task("future", priority = "medium", dueDate = "2026-11-28")
        val unplacedDueTomorrow = task("tomorrow", priority = "high", dueDate = "2026-11-19")
        val unplacedDueInTwoDays = task("two-days", priority = "low", dueDate = "2026-11-20")
        val explicitlyMovedToLater = task("moved-later", priority = "low", impact = "low", urgency = "low", dueDate = "2026-11-15")
        val explicitlyMovedToSchedule = task("moved-sched", priority = "high", impact = "high", urgency = "medium", dueDate = "2026-11-19")

        val pFuture = resolveTaskMatrix(unplacedFuture, today)
        assertEquals(MatrixLevel.MEDIUM, pFuture.impact)
        assertEquals(MatrixLevel.MEDIUM, pFuture.rawUrgency)
        assertEquals(MatrixLevel.MEDIUM, pFuture.effectiveUrgency)
        assertFalse(pFuture.boostedByDue)
        assertEquals(EisenhowerQuadrant.LATER, pFuture.quadrant)

        val pTomorrow = resolveTaskMatrix(unplacedDueTomorrow, today)
        assertEquals(MatrixLevel.MEDIUM, pTomorrow.rawUrgency)
        assertEquals(MatrixLevel.HIGH, pTomorrow.effectiveUrgency)
        assertTrue(pTomorrow.boostedByDue)
        assertEquals(EisenhowerQuadrant.DO_NOW, pTomorrow.quadrant)

        val pTwoDays = resolveTaskMatrix(unplacedDueInTwoDays, today)
        assertEquals(MatrixLevel.LOW, pTwoDays.rawUrgency)
        assertEquals(MatrixLevel.MEDIUM, pTwoDays.effectiveUrgency)
        assertTrue(pTwoDays.boostedByDue)
        assertEquals(EisenhowerQuadrant.LATER, pTwoDays.quadrant)

        val pLater = resolveTaskMatrix(explicitlyMovedToLater, today)
        assertEquals(MatrixLevel.LOW, pLater.effectiveUrgency)
        assertFalse(pLater.boostedByDue)
        assertEquals(EisenhowerQuadrant.LATER, pLater.quadrant)

        val pSched = resolveTaskMatrix(explicitlyMovedToSchedule, today)
        assertEquals(MatrixLevel.MEDIUM, pSched.effectiveUrgency)
        assertFalse(pSched.boostedByDue)
        assertEquals(EisenhowerQuadrant.SCHEDULE, pSched.quadrant)
    }

    @Test
    fun attentionSplitFocusesOnHighPriorityExcludingUnrelatedPlansAndReplies() {
        val unreadReply = task(
            "reply-unread",
            priority = "low",
            replyStatus = TaskReplyStatus(hasUnreadReply = true, awaitingReply = true, lastReplyAt = 2000.0),
        )
        val planReadyLow = task(
            "plan-low",
            priority = "low",
            agentState = TaskContext.STATE_NEEDS_YOU,
            replyStatus = TaskReplyStatus(hasUnreadReply = false, awaitingReply = true, lastReplyAt = 1800.0),
        )
        val doNowNoPlan = task("do-now", impact = "high", urgency = "high")
        val highOnTime = task("high-ok", impact = "high", urgency = "low", dueDate = "2026-11-25")
        val blocked = task("blocked", status = DirectusTask.STATUS_BLOCKED)
        val overdueTasks = (1..6).map { idx ->
            task("late-$idx", dueDate = "2026-11-1${idx}T12:00:00")
        }

        val all = listOf(highOnTime, planReadyLow, unreadReply, doNowNoPlan, blocked) + overdueTasks
        val badges = all.associate { it.id to resolveTaskReplyBadge(it) }

        assertEquals(TaskReplyBadge.UNREAD, badges["reply-unread"])
        assertEquals(TaskReplyBadge.NONE, badges["plan-low"])

        val split = buildAttentionSplit(
            tasks = all,
            todayEpochDay = today,
            replyBadges = badges,
            priorityLimit = 6,
        )

        assertEquals(6, split.highPriority.size)
        assertEquals("do-now", split.highPriority.first().id)
        assertEquals(2, split.extraHighPriorityCount)
        assertFalse(split.highPriority.any { it.id == "high-ok" || it.id == "reply-unread" || it.id == "plan-low" })
    }

    @Test
    fun replyBadgeTracksUnreadAndReTriggersWhenNewerReplyArrives() {
        val roomTask = task(
            "r1",
            channels = listOf(TaskChannel(type = TaskChannel.TYPE_ROOM, id = "room-1", label = "Room")),
            replyStatus = TaskReplyStatus(
                hasUnreadReply = false,
                awaitingReply = true,
                lastReplyChannelId = "room-1",
                lastReplyChannelType = TaskChannel.TYPE_ROOM,
                latestRoomSeq = 9,
                lastReplyAt = 2000.0,
            ),
        )
        // Even with no prior seen-seq entry on client, a room with seq > 0 shows Unread.
        assertEquals(TaskReplyBadge.UNREAD, resolveTaskReplyBadge(roomTask, seenRoomSeqs = emptyMap()))
        assertEquals(TaskReplyBadge.UNREAD, resolveTaskReplyBadge(roomTask, seenRoomSeqs = mapOf("room-1" to 5)))
        // Local read watermark suppresses the currently seen reply, then clears as soon as room seq advances or newer reply arrives.
        assertEquals(
            TaskReplyBadge.NONE,
            resolveTaskReplyBadge(roomTask, seenRoomSeqs = mapOf("room-1" to 5), localReadAtByTaskId = mapOf("r1" to 2000.0)),
        )
        assertEquals(
            TaskReplyBadge.UNREAD,
            resolveTaskReplyBadge(
                roomTask.copy(replyStatus = roomTask.replyStatus?.copy(latestRoomSeq = 10, lastReplyAt = 2010.0)),
                seenRoomSeqs = mapOf("room-1" to 5),
                localReadAtByTaskId = mapOf("r1" to 2000.0),
            ),
        )
        assertEquals(TaskReplyBadge.NONE, resolveTaskReplyBadge(roomTask, seenRoomSeqs = mapOf("room-1" to 9)))
    }

    @Test
    fun masonryBalancesShortProjectsBesideTallProject() {
        val alpha = DirectusProject(id = "alpha", name = "Alpha", sort = 1)
        val beta = DirectusProject(id = "beta", name = "Beta", sort = 2)
        val gamma = DirectusProject(id = "gamma", name = "Gamma", sort = 3)

        val buckets = listOf(
            ProjectBucket("alpha", alpha, (1..21).map { task("a-$it", projectId = "alpha") }),
            ProjectBucket("beta", beta, (1..3).map { task("b-$it", projectId = "beta") }),
            ProjectBucket("gamma", gamma, (1..2).map { task("g-$it", projectId = "gamma") }),
        )

        val (left, right) = distributeBentoMasonry(buckets, expandedProjectIds = emptySet(), previewLimit = 5)

        assertEquals(listOf("alpha"), left.map { it.key })
        assertEquals(listOf("beta", "gamma"), right.map { it.key })
    }

    @Test
    fun workstreamGroupsPromoteSubParentsWithoutDuplicatingOrDroppingTasks() {
        val macroRoot = task("root", title = "Core rollout")
        val packParent = task("wg-pack", title = "Packaging stream", parentId = "root")
        val packChild1 = task("pack-1", parentId = "wg-pack")
        val packChild2 = task("pack-2", parentId = "wg-pack")
        val bomLeaf = task("bom-1", parentId = "root", status = DirectusTask.STATUS_BLOCKED)
        val standalone = task("solo", parentId = null)

        val groups = buildWorkstreamGroups(
            tasks = listOf(macroRoot, packParent, packChild1, packChild2, bomLeaf, standalone),
            todayEpochDay = today,
            standaloneTitle = "Standalone",
        )

        val allIds = groups.flatMap { g -> g.tasks.map { it.id } }
        assertEquals(6, allIds.size)
        assertEquals(6, allIds.toSet().size)

        val packGroup = groups.first { it.key == "wg-pack" }
        assertEquals(setOf("wg-pack", "pack-1", "pack-2"), packGroup.tasks.map { it.id }.toSet())

        val rootGroup = groups.first { it.key == "root" }
        assertEquals(setOf("root", "bom-1"), rootGroup.tasks.map { it.id }.toSet())
        assertEquals(1, rootGroup.alertCount)

        assertEquals(STANDALONE_WORKSTREAM_KEY, groups.last().key)
        assertEquals(listOf("solo"), groups.last().tasks.map { it.id })
        assertTrue(groups.first().alertCount >= groups[1].alertCount)
    }

    @Test
    fun isTaskActivelyRunningUsesLiveSessionsAndSuppressesStaleDirectusRunningState() {
        val idleWithSession = task(
            id = "t-live",
            agentState = TaskContext.STATE_IDLE,
            channels = listOf(TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-live", label = "Session")),
        )
        val staleRunningTask = task(
            id = "t-stale",
            agentState = TaskContext.STATE_RUNNING,
            channels = listOf(TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-done", label = "Session")),
        )
        val doneTask = task(
            id = "t-done",
            status = DirectusTask.STATUS_DONE,
            agentState = TaskContext.STATE_RUNNING,
            channels = listOf(TaskChannel(type = TaskChannel.TYPE_SESSION, id = "sess-live", label = "Session")),
        )

        assertTrue(isTaskActivelyRunning(idleWithSession, runningSessionIds = setOf("sess-live")))
        assertFalse(isTaskActivelyRunning(idleWithSession))

        assertTrue(isTaskActivelyRunning(staleRunningTask))
        assertFalse(
            isTaskActivelyRunning(
                staleRunningTask,
                clearedStaleRunningTaskIds = setOf("t-stale"),
            ),
        )
        assertTrue(
            isTaskActivelyRunning(
                staleRunningTask,
                runningSessionIds = setOf("sess-done"),
                clearedStaleRunningTaskIds = setOf("t-stale"),
            ),
        )
        assertFalse(isTaskActivelyRunning(doneTask, runningSessionIds = setOf("sess-live"), activeTurnOverride = true))
    }
}
