package dev.hermeskotlin.ui.sessions

import dev.hermeskotlin.core.projects.Project
import dev.hermeskotlin.core.projects.ProjectIndex
import dev.hermeskotlin.core.sessions.SessionSummary
import dev.hermeskotlin.core.tasks.DispatchSessionEntry
import dev.hermeskotlin.core.tasks.DirectusTask
import dev.hermeskotlin.core.threads.ChatThread
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadSectionsTest {

    private val site = Project("p-site", "website", "/srv/site", sessionCount = 2, sessionIds = setOf("site-chat", "site-old"))
    private val index = ProjectIndex(listOf(site))
    private val misc = ChatThread("t1", "Misc", "misc")
    private val web = ChatThread("t2", "Website", "site-old")
    private val rows = listOf(
        SessionSummary("misc", title = "Misc", pinned = true),
        // The website thread's chat was compressed into a new id.
        SessionSummary("site-tip", title = "Website", pinned = true, lineageIds = listOf("site-old", "site-tip")),
        SessionSummary("pinned", title = "A pinned chat", pinned = true),
        SessionSummary("plain", title = "A chat"),
    )

    @Test
    fun threadChatsShowOnlyInTheThreadsSection() {
        val sections = threadSections(listOf(misc, web), rows, rows, project = null, index = index)
        assertEquals(listOf("misc", "site-tip"), sections.threads.map { it.session.id })
        assertEquals(listOf("pinned", "plain"), sections.others.map { it.id })
        assertEquals(listOf("pinned"), sections.others.filter { it.pinned }.map { it.id })
    }

    @Test
    fun aThreadWhoseChatIsntListedYetStillShows() {
        val later = ChatThread("t3", "Later", "not-loaded")
        val row = threadSections(listOf(later), rows, rows, project = null, index = index).threads.single()
        assertEquals("not-loaded", row.session.id)
        assertEquals("Later", row.session.displayTitle)
    }

    @Test
    fun aPickedProjectShowsOnlyItsThreads() {
        val projectRows = listOf(SessionSummary("site-tip", lineageIds = listOf("site-old", "site-tip")), SessionSummary("site-chat"))
        val sections = threadSections(listOf(misc, web), projectRows, rows, project = site, index = index)
        assertEquals(listOf("t2"), sections.threads.map { it.thread.id })
        assertEquals(listOf("site-chat"), sections.others.map { it.id })
    }

    @Test
    fun dispatchChatsPartitionOutOfOthersPreferTaskTitleAndRespectThreads() {
        val dispatchRows = listOf(
            SessionSummary("misc", title = "Misc", pinned = true),
            SessionSummary("disp-1", title = "[Task: Monitor cluster] check logs"),
            SessionSummary("disp-pin", title = "Untitled", pinned = true, lineageIds = listOf("disp-old", "disp-pin")),
            SessionSummary("pinned", title = "A pinned chat", pinned = true),
            SessionSummary("plain", title = "A regular chat"),
        )
        val dispatchMap = mapOf(
            // Even if a thread session somehow exists in Dispatch, Threads owns it first (no duplicate row).
            "misc" to DispatchSessionEntry("misc", "t0", "Should stay under Threads"),
            "disp-1" to DispatchSessionEntry("disp-1", "t1", "Monitor cluster"),
            "disp-old" to DispatchSessionEntry("disp-old", "t2", "Compressed Dispatch task"),
        )
        val sections = threadSections(listOf(misc), dispatchRows, dispatchRows, project = null, index = index, dispatchSessions = dispatchMap)

        assertEquals(listOf("misc"), sections.threads.map { it.session.id })
        assertEquals(listOf("disp-1", "disp-pin"), sections.dispatch.map { it.session.id })
        assertEquals(listOf("Monitor cluster", "Compressed Dispatch task"), sections.dispatch.map { it.displaySession.title })
        assertEquals(listOf("pinned", "plain"), sections.others.map { it.id })

        // Folded Dispatch keeps only selected or active rows, hiding idle ones.
        val visible = visibleDispatch(
            dispatch = sections.dispatch,
            collapsed = true,
            selectedId = "disp-pin",
            statuses = emptyMap(),
            drafts = emptySet(),
        )
        assertEquals(listOf("disp-pin"), visible.map { it.session.id })
    }
}
