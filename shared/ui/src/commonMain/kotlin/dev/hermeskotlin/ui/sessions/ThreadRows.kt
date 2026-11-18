package dev.hermeskotlin.ui.sessions

import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.threads.ChatThread
import dev.hermeskotlin.core.threads.ThreadsRepository

/** A thread as its row shows it: the thread, and its chat as the list has it now (made up when not listed yet). */
data class ThreadRow(val thread: ChatThread, val session: SessionSummary)

/** A Directus Dispatch session row paired with its task metadata so the sidebar displays the task title. */
data class DispatchRow(val entry: DispatchSessionEntry, val session: SessionSummary) {
    /** Session rendered in the sidebar, preferring the Dispatch task title when present. */
    val displaySession: SessionSummary
        get() = if (entry.taskTitle.isNotBlank()) session.copy(title = entry.taskTitle) else session
}

/**
 * The recent list split for the sidebar: the Threads section, Dispatch task sessions, then every
 * other chat, each shown once.
 */
data class ThreadSections(
    val threads: List<ThreadRow>,
    val dispatch: List<DispatchRow>,
    val others: List<SessionSummary>,
)

/**
 * Splits [listed] (what the list shows) around [threads] and [dispatchSessions]. A thread's chat is
 * found among [rows] (every chat the list loaded) or [listed], by its id or its compression lineage; one not
 * loaded yet still shows, by the id the thread keeps. Remaining chats present in [dispatchSessions] (by id
 * or lineage) move into [ThreadSections.dispatch] so they stay grouped cleanly under `THREADS` instead of
 * cluttering regular Chats. Each chat appears in at most one section.
 */
internal fun threadSections(
    threads: List<ChatThread>,
    listed: List<SessionSummary>,
    rows: List<SessionSummary>,
    project: Project?,
    index: ProjectIndex,
    dispatchSessions: Map<String, DispatchSessionEntry> = emptyMap(),
): ThreadSections {
    val shown = threads.mapNotNull { thread ->
        val inList = ThreadsRepository.chatOf(thread, listed)
        val session = inList ?: ThreadsRepository.chatOf(thread, rows)
            ?: SessionSummary(thread.sessionId, title = thread.name, pinned = true, cwd = thread.cwd)
        val belongs = project == null || inList != null || index.projectOf(session)?.id == project.id
        ThreadRow(thread, session).takeIf { belongs }
    }
    val afterThreads = listed.filterNot { session -> threads.any { it.holds(session) } }
    if (dispatchSessions.isEmpty()) {
        return ThreadSections(shown, emptyList(), afterThreads)
    }
    val dispatch = ArrayList<DispatchRow>()
    val others = ArrayList<SessionSummary>(afterThreads.size)
    for (session in afterThreads) {
        val entry = dispatchSessions[session.id]
            ?: session.lineageIds.firstNotNullOfOrNull { dispatchSessions[it] }
        if (entry != null) dispatch += DispatchRow(entry, session)
        else others += session
    }
    return ThreadSections(shown, dispatch, others)
}
