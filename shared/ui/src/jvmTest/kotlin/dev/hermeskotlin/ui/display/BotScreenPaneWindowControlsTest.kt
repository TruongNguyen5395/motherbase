package dev.hermeskotlin.ui.display

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.core.display.ScreenQuality
import dev.hermeskotlin.designsystem.HermesTheme
import dev.hermeskotlin.designsystem.LocalDesktopLayout
import dev.hermeskotlin.ui.LocalWindowControls
import dev.hermeskotlin.ui.WindowControls
import dev.hermeskotlin.ui.components.ProvideAppWords
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BotScreenPaneWindowControlsTest {

    @Test
    fun toolbarLayoutMovesLeaseActionToDockOnlyWhenControlsLeaveNarrowSpace() {
        val winControls = WindowControls(start = 0, end = 148, height = 40)
        val narrowWin = computeScreenToolbarLayout(335.dp, fullScreenOverlay = false, winControls, Density(1f))
        assertTrue(narrowWin.compactToolbar)
        assertTrue(narrowWin.leaseActionInDock)

        val macControls = WindowControls(start = 86, end = 0, height = 40)
        val narrowMacRail = computeScreenToolbarLayout(335.dp, fullScreenOverlay = false, macControls, Density(1f))
        assertTrue(narrowMacRail.compactToolbar)
        assertFalse(narrowMacRail.leaseActionInDock)

        val wideWinOverlay = computeScreenToolbarLayout(1024.dp, fullScreenOverlay = true, winControls, Density(1f))
        assertFalse(wideWinOverlay.compactToolbar)
        assertFalse(wideWinOverlay.leaseActionInDock)
    }

    @Test
    fun narrowRightRailClearsRightWindowControlsWithoutClippingTakeOver() = runDesktopComposeUiTest(width = 1024, height = 480) {
        val controller = BotScreenController()
        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(
                    LocalDesktopLayout provides true,
                    LocalWindowControls provides WindowControls(start = 0, end = 140, height = 40),
                ) {
                    ProvideAppWords {
                        Box(Modifier.fillMaxSize().testTag("window")) {
                            BoxWithConstraints(Modifier.align(Alignment.TopEnd).width(335.dp).fillMaxHeight()) {
                                val layout = computeScreenToolbarLayout(
                                    maxWidth = maxWidth,
                                    fullScreenOverlay = false,
                                    controls = LocalWindowControls.current,
                                    density = LocalDensity.current,
                                )
                                Column(Modifier.fillMaxSize()) {
                                    ScreenHeaderBar(
                                        title = "Agent Desk",
                                        running = true,
                                        interactive = false,
                                        otherHolds = false,
                                        actionBusy = false,
                                        quality = ScreenQuality.Balanced,
                                        compactToolbar = layout.compactToolbar,
                                        showLeaseAction = !layout.leaseActionInDock,
                                        fullScreenOverlay = false,
                                        landscape = false,
                                        keyboardBarOpen = false,
                                        onTakeOver = {},
                                        onHandBack = {},
                                        onForceHandBack = {},
                                        onCycleQuality = {},
                                        onToggleKeyboard = {},
                                        onToggleLandscape = {},
                                        onStop = {},
                                        onClose = {},
                                    )
                                    Box(Modifier.weight(1f))
                                    MobileControlDock(
                                        interactive = false,
                                        otherHolds = false,
                                        actionBusy = false,
                                        compactToolbar = layout.compactToolbar,
                                        showLeaseAction = layout.leaseActionInDock,
                                        quality = ScreenQuality.Balanced,
                                        touchMode = TouchControlMode.Trackpad,
                                        dragLocked = false,
                                        keyboardOpen = false,
                                        controller = controller,
                                        onTakeOver = {},
                                        onHandBack = {},
                                        onForceHandBack = {},
                                        onCycleQuality = {},
                                        onStop = {},
                                        onSelectTouchMode = {},
                                        onToggleDragLock = {},
                                        onToggleKeyboard = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val windowWidth = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot.width
        val close = onNodeWithContentDescription("Close Screen").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val title = onNodeWithText("Agent Desk").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val takeOver = onNodeWithText("Take over").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(close.right <= windowWidth - 140f + 1f, "Close ($close) overlaps right window controls in $windowWidth")
        assertTrue(title.right <= close.left, "Title ($title) collides with Close ($close)")
        assertTrue(takeOver.height <= 42f, "Take over ($takeOver) wrapped onto multiple lines")
    }

    @Test
    fun wideOverlayClearsBothLeftAndRightWindowControls() = headerOverlayClears(start = 100, end = 140)

    private fun headerOverlayClears(start: Int, end: Int) = runDesktopComposeUiTest(width = 1024, height = 240) {
        setContent {
            HermesTheme(ColorScheme.Dark) {
                CompositionLocalProvider(
                    LocalDesktopLayout provides true,
                    LocalWindowControls provides WindowControls(start = start, end = end, height = 40),
                ) {
                    ProvideAppWords {
                        Box(Modifier.fillMaxSize().testTag("window")) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val layout = computeScreenToolbarLayout(
                                    maxWidth = maxWidth,
                                    fullScreenOverlay = true,
                                    controls = LocalWindowControls.current,
                                    density = LocalDensity.current,
                                )
                                ScreenHeaderBar(
                                    title = "Agent Desk",
                                    running = true,
                                    interactive = false,
                                    otherHolds = false,
                                    actionBusy = false,
                                    quality = ScreenQuality.Balanced,
                                    compactToolbar = layout.compactToolbar,
                                    showLeaseAction = !layout.leaseActionInDock,
                                    fullScreenOverlay = true,
                                    landscape = false,
                                    keyboardBarOpen = false,
                                    onTakeOver = {},
                                    onHandBack = {},
                                    onForceHandBack = {},
                                    onCycleQuality = {},
                                    onToggleKeyboard = {},
                                    onToggleLandscape = {},
                                    onStop = {},
                                    onClose = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        val windowWidth = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot.width
        val title = onNodeWithText("Agent Desk").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val close = onNodeWithContentDescription("Close Screen").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val takeOver = onNodeWithText("Take over").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(title.left >= start, "Title ($title) overlaps left window controls ($start)")
        assertTrue(close.right <= windowWidth - end + 1f, "Close ($close) overlaps right window controls ($end)")
        assertTrue(takeOver.right <= windowWidth - end + 1f, "Take over ($takeOver) overlaps right window controls ($end)")
    }
}
