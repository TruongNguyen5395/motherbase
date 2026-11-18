package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.TaskContext
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
    ) = DirectusTask(
        id = id,
        title = title,
        status = status,
        priority = priority,
        dueDate = dueDate,
        parentId = parentId,
        context = TaskContext(taskId = id, projectId = projectId, agentState = agentState),
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
    fun attentionSplitSeparatesPlansFromRisksAndCapsRisksAtFive() {
        val planTask = task("plan", agentState = TaskContext.STATE_NEEDS_YOU)
        val highOnTime = task("high-ok", priority = "high", dueDate = "2026-11-25")
        val blocked = task("blocked", status = DirectusTask.STATUS_BLOCKED)
        val overdueTasks = (1..6).map { idx ->
            task("late-$idx", dueDate = "2026-11-1${idx}T12:00:00")
        }

        val split = buildAttentionSplit(
            tasks = listOf(highOnTime, planTask, blocked) + overdueTasks,
            todayEpochDay = today,
        )

        assertEquals(listOf("plan"), split.plansReady.map { it.id })
        assertEquals(5, split.riskAlerts.size)
        assertEquals("blocked", split.riskAlerts.first().id)
        assertEquals(2, split.extraRiskCount)
        assertFalse(split.riskAlerts.any { it.id == "high-ok" })
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
}
