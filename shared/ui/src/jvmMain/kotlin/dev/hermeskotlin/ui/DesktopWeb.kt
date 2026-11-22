package dev.hermeskotlin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.koin.core.context.GlobalContext

/**
 * A browser the desktop app lends the shared screens when the runtime it runs on has one (JetBrains Runtime's
 * Chromium, JCEF). Without it, a widget says it can't show and a plugin page opens in the system's browser.
 */
interface DesktopWeb {
    /**
     * A widget's [document] (see `widgetDocument`), served as the only thing it may load: no network, no
     * navigation, no new windows, no camera or microphone. [onFailure] says why it can't show.
     *
     * [keepsWheel] says, from the page's title (where the widget reports how far it's scrolled) and the wheel's
     * direction, whether a turn of the wheel scrolls the widget; when it doesn't, the chat around it scrolls.
     */
    @Composable
    fun Widget(document: String, name: String, modifier: Modifier, keepsWheel: (title: String?, down: Boolean) -> Boolean, onFailure: (String) -> Unit)

    /** A plugin's page at [url] on [gateway], signed in as the app is, with a bar that names it and closes it. */
    @Composable
    fun PluginPage(url: String, gateway: String, title: String, onClose: () -> Unit)

    /**
     * An interactive Bot Screen viewport served solely from [url] (local `DisplayRelay`), with
     * [initScript] run on main-frame load and [evalScript] evaluated on updates.
     */
    @Composable
    fun RemoteScreen(
        url: String,
        initScript: String,
        evalScript: String?,
        captureAllKeys: Boolean,
        modifier: Modifier,
        onFailure: (String) -> Unit,
    )
}

/** The app's [DesktopWeb], or null where the runtime has no browser. */
@Composable
internal fun rememberDesktopWeb(): DesktopWeb? {
    val koin = GlobalContext.getOrNull() ?: return null
    return remember(koin) { koin.getOrNull<DesktopWeb>() }
}
