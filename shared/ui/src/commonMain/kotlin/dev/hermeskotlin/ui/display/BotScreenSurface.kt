package dev.hermeskotlin.ui.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import dev.hermeskotlin.core.display.DisplayBridgeSession
import dev.hermeskotlin.core.display.ScreenQuality
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class TouchControlMode {
    Trackpad,
    DirectTouch,
}

sealed interface BotScreenAction {
    data class TapKey(val keysym: Int, val code: String = "") : BotScreenAction
    data class KeyCombo(val keys: List<Pair<Int, String>>) : BotScreenAction
    data object CtrlAltDel : BotScreenAction
    data class PasteClipboard(val text: String) : BotScreenAction
    data class TypeText(val text: String) : BotScreenAction
    data class ViewportTransform(val scale: Float, val panX: Float, val panY: Float) : BotScreenAction
    data object ResetZoom : BotScreenAction
    data object Focus : BotScreenAction
    data class PointerTap(val buttonMask: Int) : BotScreenAction
}

@Immutable
class BotScreenController {
    internal var onAction: ((BotScreenAction) -> Unit)? = null

    fun sendKey(keysym: Int, code: String = "") {
        onAction?.invoke(BotScreenAction.TapKey(keysym, code))
    }

    fun sendKeyCombo(vararg keys: Pair<Int, String>) {
        onAction?.invoke(BotScreenAction.KeyCombo(keys.toList()))
    }

    fun sendCtrlAltDel() {
        onAction?.invoke(BotScreenAction.CtrlAltDel)
    }

    fun pasteClipboard(text: String) {
        onAction?.invoke(BotScreenAction.PasteClipboard(text))
    }

    fun typeText(text: String) {
        onAction?.invoke(BotScreenAction.TypeText(text))
    }

    fun resetZoom() {
        onAction?.invoke(BotScreenAction.ResetZoom)
    }

    fun focusScreen() {
        onAction?.invoke(BotScreenAction.Focus)
    }

    fun sendRightClick() {
        onAction?.invoke(BotScreenAction.PointerTap(2))
    }
}

internal object ScreenScriptBuilder {
    fun jsString(value: String): String = JsonPrimitive(value).toString()

    fun connect(
        wsUrl: String,
        interactive: Boolean,
        quality: ScreenQuality,
        showDotCursor: Boolean,
        mobileTouch: Boolean,
    ): String {
        val opts = """{"viewOnly":${!interactive},"scaleViewport":true,"qualityLevel":${quality.qualityLevel},"compressionLevel":${quality.compressionLevel},"showDotCursor":$showDotCursor,"focusOnClick":${!mobileTouch}}"""
        val escapedWs = jsString(wsUrl)
        return "if(window.mbScreenApi){window.mbScreenApi.connect($escapedWs,$opts);}else{window.__mbPendingConnect=[$escapedWs,$opts];}"
    }

    fun updateOptions(interactive: Boolean, quality: ScreenQuality, showDotCursor: Boolean): String =
        "if(window.mbScreenApi){window.mbScreenApi.setViewOnly(${!interactive});window.mbScreenApi.setQuality(${quality.qualityLevel},${quality.compressionLevel});window.mbScreenApi.setShowDotCursor($showDotCursor);}"

    fun action(act: BotScreenAction): String = when (act) {
        is BotScreenAction.TapKey ->
            "window.mbScreenApi&&window.mbScreenApi.tapKey(${act.keysym},${jsString(act.code)});"
        is BotScreenAction.KeyCombo -> {
            val arr = act.keys.joinToString(",", "[", "]") { (sym, code) -> "[$sym,${jsString(code)}]" }
            "window.mbScreenApi&&window.mbScreenApi.sendCombo($arr);"
        }
        BotScreenAction.CtrlAltDel ->
            "window.mbScreenApi&&window.mbScreenApi.sendCtrlAltDel();"
        is BotScreenAction.PasteClipboard ->
            "window.mbScreenApi&&window.mbScreenApi.pasteClipboard(${jsString(act.text)});"
        is BotScreenAction.TypeText ->
            "window.mbScreenApi&&window.mbScreenApi.typeText(${jsString(act.text)});"
        is BotScreenAction.ViewportTransform ->
            "window.mbScreenApi&&window.mbScreenApi.setViewportTransform(${act.scale},${act.panX},${act.panY});"
        BotScreenAction.ResetZoom ->
            "window.mbScreenApi&&window.mbScreenApi.resetTransform();"
        BotScreenAction.Focus ->
            "window.mbScreenApi&&window.mbScreenApi.focusScreen();"
        is BotScreenAction.PointerTap ->
            "window.mbScreenApi&&window.mbScreenApi.pointerTap(-1,-1,${act.buttonMask});"
    }
}

@Composable
expect fun BotScreenSurface(
    bridge: DisplayBridgeSession,
    interactive: Boolean,
    quality: ScreenQuality,
    touchMode: TouchControlMode,
    dragLocked: Boolean,
    controller: BotScreenController,
    onEvent: (type: String, payload: JsonObject) -> Unit,
    onFailure: (String) -> Unit,
    modifier: Modifier = Modifier,
)

/** Requests landscape orientation on mobile when [landscape] is true; no-op on desktop. */
@Composable
expect fun BotScreenOrientationEffect(landscape: Boolean)
