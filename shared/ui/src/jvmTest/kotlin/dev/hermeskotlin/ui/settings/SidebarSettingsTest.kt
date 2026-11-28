package dev.hermeskotlin.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.settings.AppSettings
import dev.hermeskotlin.designsystem.HermesTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SidebarSettingsTest {

    @Test
    fun theSectionGoesWithOneSwitchAndEachPageWithItsOwn() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            HermesTheme(ColorScheme.Dark) {
                SidebarPagesView(settings, onUpdate = { settings = it(settings) }, onBack = {}, desktop = false)
            }
        }
        onNodeWithText("Inbox").performScrollTo().assertIsOn()
        onNodeWithText("Dispatch").performScrollTo().assertIsOn().performClick()
        assertEquals(listOf("Tasks"), settings.sidebarNavHidden)
        onNodeWithText("Dispatch").assertIsOff()

        // The whole section off: the pages go from the page too, and what was hidden is kept.
        onNodeWithText("Show page buttons").performClick()
        assertFalse(settings.sidebarNav)
        onNodeWithText("Scheduled").assertDoesNotExist()
        onNodeWithText("Show page buttons").performClick()
        assertTrue(settings.sidebarNav)
        onNodeWithText("Dispatch").performScrollTo().assertIsOff()
        onNodeWithText("Scheduled").performScrollTo().assertIsOn()
    }

    @Test
    fun aDesktopShowsInboxInSidebarSettings() = runComposeUiTest {
        setContent {
            HermesTheme(ColorScheme.Light) {
                SidebarPagesView(AppSettings(), onUpdate = {}, onBack = {}, desktop = true)
            }
        }
        onNodeWithText("Inbox").performScrollTo().assertIsOn()
        onNodeWithText("Scheduled").performScrollTo().assertIsOn()
    }

    @Test
    fun unifiedSidebarSettingsToggleActiveOverviewAndMultiExpand() = runComposeUiTest {
        var settings by mutableStateOf(AppSettings())
        setContent {
            HermesTheme(ColorScheme.Light) {
                SettingsView(settings, GatewayInfo(), "https://fixture.example", { change -> settings = change(settings) }, {}, {}, {})
            }
        }
        onNodeWithText("Active & needs you").performScrollTo().assertIsOn().performClick()
        assertFalse(settings.showActiveOverview)
        onNodeWithText("Expand multiple agents").performScrollTo().assertIsOff().performClick()
        assertTrue(settings.multiExpandAgents)
        // The pages row says how many show.
        onNodeWithText("Sidebar pages").performScrollTo().assertExists()
        onNodeWithText("All shown.").performScrollTo().assertExists()
    }
}
