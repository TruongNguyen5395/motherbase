package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.ui.components.TimeWords
import dev.hermeskotlin.ui.components.fill
import dev.hermeskotlin.ui.components.localMoment
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal const val DEFAULT_PROJECT_PREVIEW_LIMIT = 5
internal const val DEFAULT_ATTENTION_RISK_LIMIT = 5
internal const val UNASSIGNED_PROJECT_BUCKET_ID = "__unassigned__"
internal const val STANDALONE_WORKSTREAM_KEY = "__standalone__"

enum class TaskFocusGrouping { WORKSTREAM, STATUS }

internal data class ProjectBucket(
    val key: String,
    val project: DirectusProject?,
    val tasks: List<DirectusTask>,
)

internal data class WorkstreamGroup(
    val key: String,
    val title: String,
    val tasks: List<DirectusTask>,
    val alertCount: Int,
)

internal data class AttentionSplit(
    val plansReady: List<DirectusTask> = emptyList(),
    val riskAlerts: List<DirectusTask> = emptyList(),
    val extraRiskCount: Int = 0,
) {
    val totalCount: Int get() = plansReady.size + riskAlerts.size + extraRiskCount
    val isEmpty: Boolean get() = plansReady.isEmpty() && riskAlerts.isEmpty()
}

private val EXPLICIT_TZ_OFFSET_RE = Regex("""[+-]\d{2}:?\d{2}$""")

internal fun currentLocalEpochDay(): Long =
    localMoment(Clock.System.now().toEpochMilliseconds()).epochDay

/**
 * Resolves a Directus `due_date` string onto a local calendar epoch day.
 * Offset/UTC timestamps map through [localMoment]; bare wall-clock strings (`YYYY-MM-DD` or
 * `YYYY-MM-DDTHH:MM:SS`) already carry local calendar dates from Directus.
 */
@OptIn(ExperimentalTime::class)
internal fun parseDueEpochDay(dueDate: String?): Long? {
    val trimmed = dueDate?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val timePart = trimmed.substringAfter('T', "")
    val hasExplicitTz = timePart.endsWith("Z", ignoreCase = true) || EXPLICIT_TZ_OFFSET_RE.containsMatchIn(timePart)
    if (hasExplicitTz) {
        val instant = runCatching { Instant.parse(trimmed) }.getOrNull()
        if (instant != null) return localMoment(instant.toEpochMilliseconds()).epochDay
    }
    return epochDayFromCivilIso(trimmed)
}

internal fun epochDayFromCivilIso(iso: String): Long? {
    if (iso.length < 10) return null
    val parts = iso.take(10).split('-')
    if (parts.size != 3) return null
    val y = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val d = parts[2].toLongOrNull() ?: return null
    if (m !in 1L..12L || d !in 1L..31L) return null
    val year = if (m <= 2L) y - 1L else y
    val era = (if (year >= 0L) year else year - 399L) / 400L
    val yoe = year - era * 400L
    val doy = (153L * (if (m > 2L) m - 3L else m + 9L) + 2L) / 5L + d - 1L
    val doe = yoe * 365L + yoe / 4L - yoe / 100L + doy
    return era * 146097L + doe - 719468L
}

internal fun civilDateFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468L
    val era = (if (z >= 0L) z else z - 146096L) / 146097L
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    val y = yoe + era * 400L + if (m <= 2L) 1L else 0L
    return Triple(y.toInt(), m.toInt(), d.toInt())
}

internal fun formatShortDueLabel(dueEpochDay: Long, words: TimeWords = TimeWords.current): String {
    val (_, month, day) = civilDateFromEpochDay(dueEpochDay)
    val monthLabel = words.monthsShort.getOrElse(month - 1) { month.toString() }
    return fill(words.monthDay, monthLabel, day)
}

internal fun taskOverdueDays(task: DirectusTask, todayEpochDay: Long): Int {
    if (task.status == DirectusTask.STATUS_DONE) return 0
    val dueDay = parseDueEpochDay(task.dueDate) ?: return 0
    return (todayEpochDay - dueDay).toInt().coerceAtLeast(0)
}

internal fun isAttentionRisk(task: DirectusTask, todayEpochDay: Long): Boolean =
    task.status != DirectusTask.STATUS_DONE &&
        (task.status == DirectusTask.STATUS_BLOCKED || taskOverdueDays(task, todayEpochDay) > 0)

private fun priorityWeight(priority: String?): Int = when (priority?.lowercase()) {
    "urgent" -> 400
    "high" -> 200
    "medium" -> 100
    "low" -> 50
    else -> 0
}

private fun taskUrgencyScore(task: DirectusTask, todayEpochDay: Long): Int {
    if (task.status == DirectusTask.STATUS_DONE) return -1_000 + priorityWeight(task.priority)
    var score = priorityWeight(task.priority)
    if (task.agentState == TaskContext.STATE_NEEDS_YOU) score += 10_000
    if (task.status == DirectusTask.STATUS_BLOCKED) score += 5_000
    val overdue = taskOverdueDays(task, todayEpochDay)
    if (overdue > 0) score += 3_000 + minOf(overdue, 90) * 15
    if (task.status == DirectusTask.STATUS_IN_PROGRESS) score += 1_200
    if (task.agentState == TaskContext.STATE_RUNNING || task.agentState == TaskContext.STATE_QUEUED) score += 800
    if (parseDueEpochDay(task.dueDate) != null) score += 20
    return score
}

internal fun sortTasksForBoard(tasks: List<DirectusTask>, todayEpochDay: Long): List<DirectusTask> =
    tasks.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<DirectusTask>> { taskUrgencyScore(it.value, todayEpochDay) }
                .thenBy { parseDueEpochDay(it.value.dueDate) ?: Long.MAX_VALUE }
                .thenBy { it.index },
        )
        .map { it.value }

internal fun buildAttentionSplit(
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    riskLimit: Int = DEFAULT_ATTENTION_RISK_LIMIT,
): AttentionSplit {
    val plansReady = sortTasksForBoard(
        tasks.filter { it.agentState == TaskContext.STATE_NEEDS_YOU },
        todayEpochDay,
    )
    val allRisks = tasks
        .filter { it.agentState != TaskContext.STATE_NEEDS_YOU && isAttentionRisk(it, todayEpochDay) }
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<DirectusTask>> { if (it.value.status == DirectusTask.STATUS_BLOCKED) 1 else 0 }
                .thenByDescending { taskOverdueDays(it.value, todayEpochDay) }
                .thenByDescending { priorityWeight(it.value.priority) }
                .thenBy { it.index },
        )
        .map { it.value }
    return AttentionSplit(
        plansReady = plansReady,
        riskAlerts = allRisks.take(riskLimit),
        extraRiskCount = (allRisks.size - riskLimit).coerceAtLeast(0),
    )
}

internal fun buildProjectBuckets(
    projects: List<DirectusProject>,
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
): List<ProjectBucket> {
    val activeProjects = projects.filterNot { it.archived }
    val projectById = activeProjects.associateBy { it.id }
    val grouped = tasks.groupBy { task -> task.projectId?.takeIf { it in projectById } }
    return buildList {
        activeProjects.forEach { project ->
            add(
                ProjectBucket(
                    key = project.id,
                    project = project,
                    tasks = sortTasksForBoard(grouped[project.id].orEmpty(), todayEpochDay),
                ),
            )
        }
        val unassigned = grouped[null].orEmpty()
        if (unassigned.isNotEmpty() || activeProjects.isEmpty()) {
            add(
                ProjectBucket(
                    key = UNASSIGNED_PROJECT_BUCKET_ID,
                    project = null,
                    tasks = sortTasksForBoard(unassigned, todayEpochDay),
                ),
            )
        }
    }
}

/**
 * Balances project Bento cards across two independent vertical columns using estimated visible row weight
 * so a project with 20+ tasks does not leave whitespace under shorter cards on the opposite column.
 */
internal fun distributeBentoMasonry(
    buckets: List<ProjectBucket>,
    expandedProjectIds: Set<String>,
    previewLimit: Int = DEFAULT_PROJECT_PREVIEW_LIMIT,
): Pair<List<ProjectBucket>, List<ProjectBucket>> {
    val left = mutableListOf<ProjectBucket>()
    val right = mutableListOf<ProjectBucket>()
    var leftWeight = 0
    var rightWeight = 0
    buckets.forEach { bucket ->
        val visibleCount = if (bucket.key in expandedProjectIds) bucket.tasks.size else minOf(bucket.tasks.size, previewLimit)
        val weight = 2 + visibleCount
        if (leftWeight <= rightWeight) {
            left += bucket
            leftWeight += weight
        } else {
            right += bucket
            rightWeight += weight
        }
    }
    return left to right
}

/**
 * Groups tasks inside one focused project into collapsible parent workstreams (`parent_id`),
 * promoting sub-parents that own children into separate workstreams so deep project trees flatten cleanly.
 */
internal fun buildWorkstreamGroups(
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    standaloneTitle: String,
): List<WorkstreamGroup> {
    if (tasks.isEmpty()) return emptyList()
    val taskById = tasks.associateBy { it.id }
    val childrenByParent = tasks
        .filter { !it.parentId.isNullOrBlank() && it.parentId in taskById && it.parentId != it.id }
        .groupBy { it.parentId!! }

    val workstreamRoots = tasks.filter { task ->
        val children = childrenByParent[task.id].orEmpty()
        if (children.isEmpty()) {
            false
        } else {
            val isInnerParent = task.parentId != null && task.parentId in taskById
            val hasDirectLeafChild = children.any { child -> childrenByParent[child.id].isNullOrEmpty() }
            isInnerParent || hasDirectLeafChild
        }
    }
    val ownerIds = workstreamRoots.map { it.id }.toSet()
    val assignedIds = mutableSetOf<String>()
    val groups = mutableListOf<WorkstreamGroup>()

    fun collectOwnedDescendants(node: DirectusTask, acc: MutableList<DirectusTask>) {
        if (!assignedIds.add(node.id)) return
        acc += node
        childrenByParent[node.id].orEmpty().forEach { child ->
            if (child.id !in ownerIds) {
                collectOwnedDescendants(child, acc)
            }
        }
    }

    workstreamRoots.forEach { root ->
        val collected = mutableListOf<DirectusTask>()
        collectOwnedDescendants(root, collected)
        if (collected.isNotEmpty()) {
            val sorted = sortTasksForBoard(collected, todayEpochDay)
            val alerts = sorted.count { it.agentState == TaskContext.STATE_NEEDS_YOU || isAttentionRisk(it, todayEpochDay) }
            groups += WorkstreamGroup(
                key = root.id,
                title = root.title,
                tasks = sorted,
                alertCount = alerts,
            )
        }
    }

    val standalone = tasks.filter { it.id !in assignedIds }
    if (standalone.isNotEmpty()) {
        val sorted = sortTasksForBoard(standalone, todayEpochDay)
        val alerts = sorted.count { it.agentState == TaskContext.STATE_NEEDS_YOU || isAttentionRisk(it, todayEpochDay) }
        groups += WorkstreamGroup(
            key = STANDALONE_WORKSTREAM_KEY,
            title = standaloneTitle,
            tasks = sorted,
            alertCount = alerts,
        )
    }

    return groups.sortedWith(
        compareBy<WorkstreamGroup> { if (it.key == STANDALONE_WORKSTREAM_KEY) 1 else 0 }
            .thenByDescending { it.alertCount }
            .thenByDescending { grp -> grp.tasks.maxOfOrNull { taskUrgencyScore(it, todayEpochDay) } ?: 0 },
    )
}
