package dev.hermeskotlin.ui.tasks

import dev.hermeskotlin.core.tasks.DirectusProject
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.tasks.EisenhowerQuadrant
import dev.hermeskotlin.core.tasks.MatrixLevel
import dev.hermeskotlin.core.tasks.TaskChannel
import dev.hermeskotlin.core.tasks.TaskContext
import dev.hermeskotlin.ui.components.TimeWords
import dev.hermeskotlin.ui.components.fill
import dev.hermeskotlin.ui.components.localMoment
import dev.hermeskotlin.ui.components.messageTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal const val DEFAULT_PROJECT_PREVIEW_LIMIT = 5
internal const val DEFAULT_ATTENTION_PRIORITY_LIMIT = 6
internal const val UNASSIGNED_PROJECT_BUCKET_ID = "__unassigned__"
internal const val STANDALONE_WORKSTREAM_KEY = "__standalone__"

enum class TasksBoardViewMode { PROJECTS, MATRIX, COMPLETED }

enum class TaskFocusGrouping { WORKSTREAM, STATUS, MATRIX }

/** Unread and blocking-input attention signals for task indicators. */
enum class TaskReplyBadge(val rank: Int) {
    NONE(0),
    UNREAD(1),
    WAITING_INPUT(2),
}

/** Resolved 2D matrix position for a task, preserving manual urgency while applying due-date elevation. */
internal data class TaskMatrixPosition(
    val impact: MatrixLevel,
    val rawUrgency: MatrixLevel,
    val effectiveUrgency: MatrixLevel,
    val boostedByDue: Boolean,
    val quadrant: EisenhowerQuadrant,
)

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
    val unreadCount: Int = 0,
)

internal data class AttentionSplit(
    val highPriority: List<DirectusTask> = emptyList(),
    val extraHighPriorityCount: Int = 0,
) {
    val totalCount: Int get() = highPriority.size + extraHighPriorityCount
    val isEmpty: Boolean get() = highPriority.isEmpty()
}

private val EXPLICIT_TZ_OFFSET_RE = Regex("""[+-]\d{2}:?\d{2}$""")

/**
 * Parses an ISO-8601 datetime (`2026-11-28T06:05:50Z`, `...+00:00`, or bare Directus UTC
 * `2026-11-28T06:05:50` / `2026-11-28 06:05:50`) into epoch milliseconds. Returns null on blank
 * or non-datetime strings.
 */
@OptIn(ExperimentalTime::class)
internal fun parseUtcOrOffsetIsoMillis(raw: String?): Long? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val isoLike = if ('T' !in trimmed && ' ' in trimmed) trimmed.replaceFirst(' ', 'T') else trimmed
    val timePart = isoLike.substringAfter('T', "")
    if (timePart.isEmpty()) return null
    val hasExplicitTz = timePart.endsWith("Z", ignoreCase = true) || EXPLICIT_TZ_OFFSET_RE.containsMatchIn(timePart)
    val normalized = if (hasExplicitTz) isoLike else "${isoLike}Z"
    return runCatching { Instant.parse(normalized).toEpochMilliseconds() }.getOrNull()
}

/** Formats a Directus UTC or offset datetime into a concise local clock/date label using [messageTime]. */
@OptIn(ExperimentalTime::class)
internal fun formatCompletedTimestamp(
    raw: String?,
    use24Hour: Boolean = true,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    words: TimeWords = TimeWords.current,
): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val millis = parseUtcOrOffsetIsoMillis(trimmed)
        ?: return trimmed.takeIf { it.length >= 10 && epochDayFromCivilIso(it) != null }?.take(10)
    return messageTime(localMoment(millis), localMoment(nowMillis), use24Hour, words)
}

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

internal fun taskDaysUntilDue(task: DirectusTask, todayEpochDay: Long): Int? {
    if (task.status == DirectusTask.STATUS_DONE) return null
    val dueDay = parseDueEpochDay(task.dueDate) ?: return null
    return (dueDay - todayEpochDay).toInt()
}

internal fun inferImpactFromPriority(priority: String?): MatrixLevel = when (priority?.trim()?.lowercase()) {
    "urgent", "high" -> MatrixLevel.HIGH
    "medium" -> MatrixLevel.MEDIUM
    "low" -> MatrixLevel.LOW
    else -> MatrixLevel.MEDIUM
}

internal fun inferUrgencyFromPriority(priority: String?): MatrixLevel = when (priority?.trim()?.lowercase()) {
    "urgent" -> MatrixLevel.HIGH
    "high", "medium" -> MatrixLevel.MEDIUM
    "low" -> MatrixLevel.LOW
    else -> MatrixLevel.LOW
}

/**
 * Resolves a task's 2D Impact × Urgency position. Explicit manual [DirectusTask.urgency] is always
 * respected so dragging a task to Schedule/Later sticks even when overdue; unplaced tasks
 * (`urgency == null`) elevate automatically when blocked, overdue, or due within 1–2 days.
 */
internal fun resolveTaskMatrix(task: DirectusTask, todayEpochDay: Long): TaskMatrixPosition {
    val impact = MatrixLevel.fromWire(task.impact) ?: inferImpactFromPriority(task.priority)
    val explicitUrgency = MatrixLevel.fromWire(task.urgency)
    val rawUrgency = explicitUrgency ?: inferUrgencyFromPriority(task.priority)
    val effectiveUrgency = if (task.status == DirectusTask.STATUS_DONE || explicitUrgency != null) {
        rawUrgency
    } else {
        val overdue = taskOverdueDays(task, todayEpochDay)
        val daysUntil = taskDaysUntilDue(task, todayEpochDay)
        when {
            task.status == DirectusTask.STATUS_BLOCKED || overdue > 0 || (daysUntil != null && daysUntil <= 1) -> MatrixLevel.HIGH
            daysUntil == 2 && rawUrgency.rank < MatrixLevel.MEDIUM.rank -> MatrixLevel.MEDIUM
            else -> rawUrgency
        }
    }
    return TaskMatrixPosition(
        impact = impact,
        rawUrgency = rawUrgency,
        effectiveUrgency = effectiveUrgency,
        boostedByDue = effectiveUrgency.rank > rawUrgency.rank,
        quadrant = EisenhowerQuadrant.of(impact, effectiveUrgency),
    )
}

internal fun resolveTaskReplyBadge(
    task: DirectusTask,
    seenRoomSeqs: Map<String, Int> = emptyMap(),
    waitingSessionIds: Set<String> = emptySet(),
    localReadAtByTaskId: Map<String, Double> = emptyMap(),
    localUnreadTaskIds: Set<String> = emptySet(),
): TaskReplyBadge {
    if (task.status == DirectusTask.STATUS_DONE) return TaskReplyBadge.NONE
    if (task.channels.any { it.isSession && it.id in waitingSessionIds }) return TaskReplyBadge.WAITING_INPUT
    if (task.id in localUnreadTaskIds) return TaskReplyBadge.UNREAD
    val rs = task.replyStatus ?: return TaskReplyBadge.NONE
    val roomChannelId = rs.lastReplyChannelId
    val roomSeq = rs.latestRoomSeq
    val roomUnread = rs.lastReplyChannelType == TaskChannel.TYPE_ROOM &&
        roomChannelId != null &&
        roomSeq != null &&
        roomSeq > 0 &&
        (seenRoomSeqs[roomChannelId] ?: 0) < roomSeq
    val serverUnread = rs.hasUnreadReply || roomUnread
    if (serverUnread) {
        val localReadAt = localReadAtByTaskId[task.id]
        val replyAt = rs.lastReplyAt
        val suppressedLocally = localReadAt != null && (replyAt == null || replyAt <= localReadAt + 1.0)
        if (!suppressedLocally) return TaskReplyBadge.UNREAD
    }
    return TaskReplyBadge.NONE
}

/**
 * Resolves whether a non-Done task is currently executing an agent turn.
 * Live gateway/session signals override a stale Directus `running` state after a turn completes.
 */
internal fun isTaskActivelyRunning(
    task: DirectusTask,
    runningSessionIds: Set<String> = emptySet(),
    clearedStaleRunningTaskIds: Set<String> = emptySet(),
    activeTurnOverride: Boolean = false,
): Boolean {
    if (task.status == DirectusTask.STATUS_DONE) return false
    if (activeTurnOverride) return true
    if (task.channels.any { it.isSession && it.id in runningSessionIds }) return true
    return task.agentState == TaskContext.STATE_RUNNING && task.id !in clearedStaleRunningTaskIds
}

internal fun isAttentionRisk(task: DirectusTask, todayEpochDay: Long): Boolean =
    task.status != DirectusTask.STATUS_DONE &&
        (task.status == DirectusTask.STATUS_BLOCKED || taskOverdueDays(task, todayEpochDay) > 0)

internal fun isHighPriorityAttention(task: DirectusTask, todayEpochDay: Long): Boolean {
    if (task.status == DirectusTask.STATUS_DONE) return false
    val matrix = resolveTaskMatrix(task, todayEpochDay)
    return matrix.quadrant == EisenhowerQuadrant.DO_NOW ||
        isAttentionRisk(task, todayEpochDay)
}

private fun priorityWeight(priority: String?): Int = when (priority?.lowercase()) {
    "urgent" -> 400
    "high" -> 200
    "medium" -> 100
    "low" -> 50
    else -> 0
}

private fun taskUrgencyScore(
    task: DirectusTask,
    todayEpochDay: Long,
    replyBadge: TaskReplyBadge = TaskReplyBadge.NONE,
): Int {
    if (task.status == DirectusTask.STATUS_DONE) return -1_000 + priorityWeight(task.priority)
    val matrix = resolveTaskMatrix(task, todayEpochDay)
    var score = matrix.impact.rank * 600 + matrix.effectiveUrgency.rank * 500 + priorityWeight(task.priority)
    if (replyBadge == TaskReplyBadge.WAITING_INPUT) score += 14_000
    if (replyBadge == TaskReplyBadge.UNREAD) score += 11_000
    if (matrix.quadrant == EisenhowerQuadrant.DO_NOW) score += 6_000
    if (task.status == DirectusTask.STATUS_BLOCKED) score += 4_500
    val overdue = taskOverdueDays(task, todayEpochDay)
    if (overdue > 0) score += 3_000 + minOf(overdue, 90) * 15
    if (task.status == DirectusTask.STATUS_IN_PROGRESS) score += 1_000
    if (task.agentState == TaskContext.STATE_RUNNING || task.agentState == TaskContext.STATE_QUEUED) score += 600
    if (parseDueEpochDay(task.dueDate) != null) score += 20
    return score
}

internal fun sortTasksForBoard(
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    replyBadges: Map<String, TaskReplyBadge> = emptyMap(),
): List<DirectusTask> =
    tasks.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<DirectusTask>> {
                taskUrgencyScore(it.value, todayEpochDay, replyBadges[it.value.id] ?: TaskReplyBadge.NONE)
            }
                .thenBy { parseDueEpochDay(it.value.dueDate) ?: Long.MAX_VALUE }
                .thenBy { it.index },
        )
        .map { it.value }

internal fun buildAttentionSplit(
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    @Suppress("UNUSED_PARAMETER") replyBadges: Map<String, TaskReplyBadge> = emptyMap(),
    priorityLimit: Int = DEFAULT_ATTENTION_PRIORITY_LIMIT,
): AttentionSplit {
    val active = tasks.filter { it.status != DirectusTask.STATUS_DONE }
    val allHighPriority = active
        .filter { isHighPriorityAttention(it, todayEpochDay) }
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<DirectusTask>> {
                if (resolveTaskMatrix(it.value, todayEpochDay).quadrant == EisenhowerQuadrant.DO_NOW) 1 else 0
            }
                .thenByDescending { if (it.value.status == DirectusTask.STATUS_BLOCKED) 1 else 0 }
                .thenByDescending { taskOverdueDays(it.value, todayEpochDay) }
                .thenByDescending { taskUrgencyScore(it.value, todayEpochDay) }
                .thenBy { parseDueEpochDay(it.value.dueDate) ?: Long.MAX_VALUE }
                .thenBy { it.index },
        )
        .map { it.value }

    return AttentionSplit(
        highPriority = allHighPriority.take(priorityLimit),
        extraHighPriorityCount = (allHighPriority.size - priorityLimit).coerceAtLeast(0),
    )
}

internal fun buildMatrixQuadrants(
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    replyBadges: Map<String, TaskReplyBadge> = emptyMap(),
): Map<EisenhowerQuadrant, List<DirectusTask>> {
    val grouped = tasks
        .filter { it.status != DirectusTask.STATUS_DONE }
        .groupBy { resolveTaskMatrix(it, todayEpochDay).quadrant }
    return EisenhowerQuadrant.entries.associateWith { quad ->
        sortTasksForBoard(grouped[quad].orEmpty(), todayEpochDay, replyBadges)
    }
}

internal fun buildProjectBuckets(
    projects: List<DirectusProject>,
    tasks: List<DirectusTask>,
    todayEpochDay: Long,
    replyBadges: Map<String, TaskReplyBadge> = emptyMap(),
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
                    tasks = sortTasksForBoard(grouped[project.id].orEmpty(), todayEpochDay, replyBadges),
                ),
            )
        }
        val unassigned = grouped[null].orEmpty()
        if (unassigned.isNotEmpty() || activeProjects.isEmpty()) {
            add(
                ProjectBucket(
                    key = UNASSIGNED_PROJECT_BUCKET_ID,
                    project = null,
                    tasks = sortTasksForBoard(unassigned, todayEpochDay, replyBadges),
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
    replyBadges: Map<String, TaskReplyBadge> = emptyMap(),
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
            val sorted = sortTasksForBoard(collected, todayEpochDay, replyBadges)
            val alerts = sorted.count { isHighPriorityAttention(it, todayEpochDay) }
            val unread = sorted.count {
                val b = replyBadges[it.id] ?: TaskReplyBadge.NONE
                b == TaskReplyBadge.UNREAD || b == TaskReplyBadge.WAITING_INPUT
            }
            groups += WorkstreamGroup(
                key = root.id,
                title = root.title,
                tasks = sorted,
                alertCount = alerts,
                unreadCount = unread,
            )
        }
    }

    val standalone = tasks.filter { it.id !in assignedIds }
    if (standalone.isNotEmpty()) {
        val sorted = sortTasksForBoard(standalone, todayEpochDay, replyBadges)
        val alerts = sorted.count { isHighPriorityAttention(it, todayEpochDay) }
        val unread = sorted.count {
            val b = replyBadges[it.id] ?: TaskReplyBadge.NONE
            b == TaskReplyBadge.UNREAD || b == TaskReplyBadge.WAITING_INPUT
        }
        groups += WorkstreamGroup(
            key = STANDALONE_WORKSTREAM_KEY,
            title = standaloneTitle,
            tasks = sorted,
            alertCount = alerts,
            unreadCount = unread,
        )
    }

    return groups.sortedWith(
        compareBy<WorkstreamGroup> { if (it.key == STANDALONE_WORKSTREAM_KEY) 1 else 0 }
            .thenByDescending { it.alertCount }
            .thenByDescending { grp ->
                grp.tasks.maxOfOrNull { taskUrgencyScore(it, todayEpochDay, replyBadges[it.id] ?: TaskReplyBadge.NONE) } ?: 0
            },
    )
}
