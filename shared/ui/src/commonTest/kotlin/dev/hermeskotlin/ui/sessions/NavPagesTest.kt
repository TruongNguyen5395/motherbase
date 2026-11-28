package dev.hermeskotlin.ui.sessions

import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.core.settings.withSidebarPage
import dev.hermeskotlin.ui.SidebarPage
import kotlin.test.Test
import kotlin.test.assertEquals

class NavPagesTest {

    private val gatewayPages = listOf(
        SidebarPage.Inbox,
        SidebarPage.Tasks,
        SidebarPage.Calendar,
        SidebarPage.Scheduled,
    )

    @Test
    fun byDefaultEveryGatewayPageHasItsButton() {
        assertEquals(gatewayPages, shownNavPages(AppSettings(), desktop = false))
        // Before the settings are read, the sidebar doesn't flash without them.
        assertEquals(gatewayPages, shownNavPages(null, desktop = false))
    }

    @Test
    fun aDesktopIncludesInboxToo() {
        assertEquals(gatewayPages, shownNavPages(AppSettings(), desktop = true))
        assertEquals(gatewayPages, navPages(desktop = true))
    }

    @Test
    fun theWholeSectionOffLeavesNoButtons() {
        assertEquals(emptyList(), shownNavPages(AppSettings(sidebarNav = false), desktop = false))
        assertEquals(emptyList(), shownNavPages(AppSettings(sidebarNav = false), desktop = true))
    }

    @Test
    fun hiddenPagesGoAndTheRestKeepTheirOrder() {
        val settings = AppSettings()
            .withSidebarPage(SidebarPage.Inbox.name, shown = false)
            .withSidebarPage(SidebarPage.Calendar.name, shown = false)
        assertEquals(
            listOf(SidebarPage.Tasks, SidebarPage.Scheduled),
            shownNavPages(settings, desktop = false),
        )
        // Back on, the section has them as they were left.
        assertEquals(shownNavPages(settings, desktop = false), shownNavPages(settings.copy(sidebarNav = false).copy(sidebarNav = true), desktop = false))
    }

    @Test
    fun aNameThisVersionDoesNotKnowChangesNothing() {
        // A page an older or newer version hid, under a name this one has no page for.
        assertEquals(gatewayPages, shownNavPages(AppSettings(sidebarNavHidden = listOf("Gallery")), desktop = false))
    }
}
