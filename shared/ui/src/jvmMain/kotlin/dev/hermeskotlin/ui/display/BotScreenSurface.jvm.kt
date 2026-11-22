package dev.hermeskotlin.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.hermeskotlin.core.display.DisplayBridgeSession
import dev.hermeskotlin.core.display.ScreenQuality
import dev.hermeskotlin.designsystem.body
import dev.hermeskotlin.designsystem.textSecondary
import dev.hermeskotlin.designsystem.typography
import dev.hermeskotlin.ui.rememberDesktopWeb
import kotlinx.serialization.json.JsonObject

@Composable
actual fun BotScreenSurface(
    bridge: DisplayBridgeSession,
    interactive: Boolean,
    quality: ScreenQuality,
    touchMode: TouchControlMode,
    dragLocked: Boolean,
    controller: BotScreenController,
    onEvent: (type: String, payload: JsonObject) -> Unit,
    onFailure: (String) -> Unit,
    modifier: Modifier,
) {
    val web = rememberDesktopWeb()
    if (web == null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color(0xFF0D1117)).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Bot Screen live view requires JetBrains Runtime Chromium (JCEF).",
                style = Theme[typography][body],
                color = Theme[dev.hermeskotlin.designsystem.colors][textSecondary],
            )
        }
        return
    }

    val initScript = remember(bridge.wsUrl) {
        ScreenScriptBuilder.connect(
            wsUrl = bridge.wsUrl,
            interactive = interactive,
            quality = quality,
            showDotCursor = false,
            mobileTouch = false,
        )
    }

    var evalSeq by remember { mutableIntStateOf(0) }
    var pendingScript by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(interactive, quality) {
        pendingScript = ScreenScriptBuilder.updateOptions(interactive, quality, showDotCursor = false)
        evalSeq++
    }

    DisposableEffect(controller) {
        controller.onAction = { act ->
            pendingScript = ScreenScriptBuilder.action(act)
            evalSeq++
        }
        onDispose {
            controller.onAction = null
        }
    }

    val versionedEval = remember(evalSeq, pendingScript) {
        pendingScript?.let { "$it /*$evalSeq*/" }
    }

    web.RemoteScreen(
        url = bridge.viewerUrl,
        initScript = initScript,
        evalScript = versionedEval,
        captureAllKeys = interactive,
        modifier = modifier.fillMaxSize(),
        onFailure = onFailure,
    )
}

@Composable
actual fun BotScreenOrientationEffect(landscape: Boolean) {
    // Desktop window layout adapts freely via user resize.
}
