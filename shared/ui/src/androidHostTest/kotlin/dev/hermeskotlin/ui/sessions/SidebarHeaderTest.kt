package dev.hermeskotlin.ui.sessions

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.composeunstyled.theme.ColorScheme
import dev.hermeskotlin.designsystem.HermesTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.composeunstyled.Text
import dev.hermeskotlin.ui.bots.BotsRoster
import dev.hermeskotlin.ui.bots.BotsUiState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarHeaderTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The library has no manifest declaring the activity that hosts the menu.
    @get:Rule
    val rules: TestRule = RuleChain.outerRule(RegisterHostActivity()).around(composeRule)

    private var accountOpened = 0
    private var gatewaysOpened = 0

    private fun show(
        gateway: String = "homelab",
        scheme: ColorScheme = ColorScheme.Light,
        withGatewayMenu: Boolean = true,
    ) {
        composeRule.setContent {
            HermesTheme(scheme) {
                MainHeader(
                    gatewayLabel = gateway,
                    connection = null,
                    onAccount = { accountOpened++ },
                    onSettings = {},
                    onSearch = {},
                    onOpenGateways = if (withGatewayMenu) ({ gatewaysOpened++ }) else null,
                )
            }
        }
        settle()
    }

    @Test
    fun theHeaderOpensGatewayAndAccountMenuWithoutProfileSwitcher() {
        show()
        composeRule.onNodeWithText("homelab").assertIsDisplayed()
        composeRule.onNodeWithText("MotherBase").performClick()
        settle()
        assertEquals(0, accountOpened)
        composeRule.onNodeWithText("Switch or add a gateway").assertIsDisplayed().performClick()
        settle()
        assertEquals(1, gatewaysOpened)
    }

    @Test
    fun theMenuReachesTheAccountSheet() {
        show(scheme = ColorScheme.Dark)
        composeRule.onNodeWithText("MotherBase").performClick()
        settle()
        composeRule.onNodeWithText("Account and gateways").performClick()
        assertEquals(1, accountOpened)
    }

    @Test
    fun withoutGatewaySwitcherTheHeaderOpensAccountDirectly() {
        show(withGatewayMenu = false)
        composeRule.onNodeWithText("MotherBase").performClick()
        settle()
        assertEquals(1, accountOpened)
        assertEquals(0, gatewaysOpened)
    }

    @Test
    fun aLongGatewayNameRendersCleanly() {
        show(gateway = "a-very-long-homelab-gateway-hostname-for-testing")
        composeRule.onNodeWithText("MotherBase").assertIsDisplayed()
        composeRule.onNodeWithText("a-very-long-homelab-gateway-hostname-for-testing").assertIsDisplayed()
    }

    @Test
    fun theRosterDrawsItsTopAboveTheBots() {
        // The sidebar's page buttons when it has no Chats / Bots switch, so the Bots side still reaches the pages.
        composeRule.setContent {
            HermesTheme(ColorScheme.Dark) {
                BotsRoster(
                    state = BotsUiState(loading = false),
                    avatars = emptyMap(),
                    selectedId = null,
                    selectedRunning = false,
                    nowSeconds = 0.0,
                    actions = PreviewBotActions,
                    onRetry = {},
                    onDismissNotice = {},
                    top = { Text("Page buttons") },
                )
            }
        }
        settle()
        val top = composeRule.onNodeWithText("Page buttons").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bots = composeRule.onNodeWithText("No bots on this gateway yet.").getUnclippedBoundsInRoot()
        assertTrue(top.bottom <= bots.top, "top ${top.bottom}, bots ${bots.top}")
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2000)
        composeRule.waitForIdle()
    }

    private class RegisterHostActivity : TestRule {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                val app = RuntimeEnvironment.getApplication()
                Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(
                    ComponentName(app.packageName, ComponentActivity::class.java.name),
                )
                base.evaluate()
            }
        }
    }
}
