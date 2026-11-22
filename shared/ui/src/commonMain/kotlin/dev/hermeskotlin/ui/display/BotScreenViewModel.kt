package dev.hermeskotlin.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hermeskotlin.core.display.DisplayBridgeSession
import dev.hermeskotlin.core.display.DisplayCloseCodes
import dev.hermeskotlin.core.display.DisplayStore
import dev.hermeskotlin.core.display.ProfileScreenState
import dev.hermeskotlin.core.display.RelayEvent
import dev.hermeskotlin.core.display.ScreenQuality
import dev.hermeskotlin.core.display.DisplayRelay
import dev.hermeskotlin.core.gateway.GatewayRepository
import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.ui.resources.Res
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class ScreenDisconnectNotice {
    Evicted,
    Stopped,
    Expired,
    Lost,
}

data class BotScreenUiState(
    val profile: String = ProfileScreenState.DEFAULT_PROFILE,
    val gatewayUrl: GatewayUrl? = null,
    val screenOpen: Boolean = false,
    val bridge: DisplayBridgeSession? = null,
    val wsConnected: Boolean = false,
    val disconnectNotice: ScreenDisconnectNotice? = null,
    val surfaceFailure: String? = null,
    val quality: ScreenQuality = ScreenQuality.Balanced,
    val touchMode: TouchControlMode = TouchControlMode.Trackpad,
    val dragLocked: Boolean = false,
    val landscapeFullscreen: Boolean = false,
    val keyboardBarOpen: Boolean = false,
    val remoteClipboardText: String? = null,
    val showCloseHoldConfirm: Boolean = false,
    val showStopConfirm: Boolean = false,
)

/**
 * Coordinates Bot Screen lifecycle, relay bridges, lease handoffs, 30s auto-raise cooldown,
 * and OTP background survival across Desktop split-pane and Android full-screen viewers.
 */
class BotScreenViewModel(
    private val displayStore: DisplayStore,
    private val displayRelay: DisplayRelay,
    private val gateways: GatewayRepository,
    private val loadAssets: suspend () -> Pair<ByteArray, ByteArray> = {
        Res.readBytes("files/bot_screen/viewer.html") to Res.readBytes("files/bot_screen/rfb.js")
    },
    private val clockMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    val profiles: StateFlow<Map<String, ProfileScreenState>> = displayStore.profiles

    private val _ui = MutableStateFlow(BotScreenUiState())
    val ui: StateFlow<BotScreenUiState> = _ui.asStateFlow()

    private var assetsRegistered = false
    private var autoRaiseEnabled = false
    private var activeSessionId: String? = null
    private var manualDismissed = false
    private var lastAutoRaiseMs = 0L

    init {
        viewModelScope.launch {
            displayRelay.events.collect { event ->
                handleRelayEvent(event)
            }
        }
        viewModelScope.launch {
            displayStore.autoRaise.collect { ev ->
                val cur = _ui.value
                val sessionMatches = activeSessionId == null || ev.sessionId == activeSessionId
                val now = clockMs()
                if (
                    autoRaiseEnabled &&
                    sessionMatches &&
                    !cur.screenOpen &&
                    !manualDismissed &&
                    (now - lastAutoRaiseMs) >= AUTO_RAISE_COOLDOWN_MS
                ) {
                    lastAutoRaiseMs = now
                    openScreen(cur.profile, cur.gatewayUrl)
                }
            }
        }
    }

    fun stateOf(profile: String?): ProfileScreenState = displayStore.stateOf(profile)

    fun bindChatContext(
        profile: String?,
        gatewayUrl: GatewayUrl?,
        sessionId: String?,
        autoOpenEnabled: Boolean,
    ) {
        val pid = displayStore.normalizeProfile(profile)
        val prev = _ui.value
        autoRaiseEnabled = autoOpenEnabled
        activeSessionId = sessionId
        if (prev.profile != pid || prev.gatewayUrl != gatewayUrl) {
            manualDismissed = false
            prev.bridge?.let { displayRelay.closeBridge(it.wsToken, preserveLeaseOnDisconnect = true) }
            _ui.update {
                it.copy(
                    profile = pid,
                    gatewayUrl = gatewayUrl,
                    bridge = null,
                    wsConnected = false,
                    disconnectNotice = null,
                    surfaceFailure = null,
                    dragLocked = false,
                )
            }
            if (prev.screenOpen) {
                viewModelScope.launch { refreshAndConnect(pid, gatewayUrl) }
            } else {
                viewModelScope.launch { displayStore.refreshStatus(pid, quiet = true) }
            }
        }
    }

    fun updateAutoRaiseConfig(sessionId: String?, autoOpenEnabled: Boolean) {
        activeSessionId = sessionId
        autoRaiseEnabled = autoOpenEnabled
    }

    fun openScreen(profile: String? = null, gatewayUrl: GatewayUrl? = null) {
        val pid = displayStore.normalizeProfile(profile ?: _ui.value.profile)
        val gw = gatewayUrl ?: _ui.value.gatewayUrl ?: gateways.list.value.current?.gatewayUrl
        manualDismissed = false
        _ui.update {
            it.copy(
                profile = pid,
                gatewayUrl = gw,
                screenOpen = true,
                disconnectNotice = null,
                surfaceFailure = null,
                showCloseHoldConfirm = false,
            )
        }
        viewModelScope.launch {
            refreshAndConnect(pid, gw)
        }
    }

    fun connectLive() {
        val cur = _ui.value
        viewModelScope.launch {
            observeAndOpenBridge(cur.profile, cur.gatewayUrl)
        }
    }

    fun refreshStatus() {
        val pid = _ui.value.profile
        viewModelScope.launch { displayStore.refreshStatus(pid) }
    }

    fun startScreen() {
        val cur = _ui.value
        viewModelScope.launch {
            val res = displayStore.startScreen(cur.profile)
            if (res.isSuccess && _ui.value.screenOpen) {
                observeAndOpenBridge(cur.profile, cur.gatewayUrl)
            }
        }
    }

    fun requestStopScreen() {
        val cur = _ui.value
        val st = displayStore.stateOf(cur.profile)
        if (st.status?.lease?.holder == dev.hermeskotlin.core.display.LeaseHolder.Human) {
            _ui.update { it.copy(showStopConfirm = true) }
        } else {
            confirmStopScreen(force = false)
        }
    }

    fun dismissStopConfirm() {
        _ui.update { it.copy(showStopConfirm = false) }
    }

    fun confirmStopScreen(force: Boolean) {
        val cur = _ui.value
        _ui.update { it.copy(showStopConfirm = false, dragLocked = false) }
        cur.bridge?.let { displayRelay.closeBridge(it.wsToken, preserveLeaseOnDisconnect = false) }
        _ui.update { it.copy(bridge = null, wsConnected = false) }
        viewModelScope.launch {
            displayStore.stopScreen(cur.profile, force = force)
        }
    }

    fun takeOver() {
        val pid = _ui.value.profile
        viewModelScope.launch {
            displayStore.acquireLease(pid)
        }
    }

    fun handBack(force: Boolean = false) {
        val pid = _ui.value.profile
        _ui.update { it.copy(dragLocked = false, keyboardBarOpen = false) }
        viewModelScope.launch {
            displayStore.releaseLease(pid, force = force)
        }
    }

    fun requestClose() {
        val cur = _ui.value
        if (!cur.screenOpen) return
        val held = displayStore.stateOf(cur.profile).iHoldLease
        if (held) {
            _ui.update { it.copy(showCloseHoldConfirm = true) }
        } else {
            closeScreen(handBackFirst = false)
        }
    }

    fun dismissCloseHoldConfirm() {
        _ui.update { it.copy(showCloseHoldConfirm = false) }
    }

    fun closeScreen(handBackFirst: Boolean) {
        val cur = _ui.value
        manualDismissed = true
        cur.bridge?.let { displayRelay.closeBridge(it.wsToken, preserveLeaseOnDisconnect = !handBackFirst) }
        _ui.update {
            it.copy(
                screenOpen = false,
                bridge = null,
                wsConnected = false,
                dragLocked = false,
                landscapeFullscreen = false,
                keyboardBarOpen = false,
                showCloseHoldConfirm = false,
                showStopConfirm = false,
            )
        }
        if (handBackFirst) {
            viewModelScope.launch { displayStore.releaseLease(cur.profile) }
        }
    }

    fun cycleQuality() {
        _ui.update {
            val next = when (it.quality) {
                ScreenQuality.Sharp -> ScreenQuality.Balanced
                ScreenQuality.Balanced -> ScreenQuality.LowBandwidth
                ScreenQuality.LowBandwidth -> ScreenQuality.Sharp
            }
            it.copy(quality = next)
        }
    }

    fun setTouchMode(mode: TouchControlMode) {
        _ui.update { it.copy(touchMode = mode, dragLocked = false) }
    }

    fun toggleDragLock() {
        _ui.update { it.copy(dragLocked = !it.dragLocked) }
    }

    fun toggleLandscape() {
        _ui.update { it.copy(landscapeFullscreen = !it.landscapeFullscreen) }
    }

    fun toggleKeyboardBar() {
        _ui.update { it.copy(keyboardBarOpen = !it.keyboardBarOpen) }
    }

    fun dismissRemoteClipboard() {
        _ui.update { it.copy(remoteClipboardText = null) }
    }

    fun startInstall() {
        val pid = _ui.value.profile
        viewModelScope.launch {
            displayStore.startInstall(pid)
        }
    }

    fun respondInstallSudo(password: String?) {
        val pid = _ui.value.profile
        viewModelScope.launch {
            displayStore.respondInstallSudo(pid, password)
        }
    }

    fun switchSandboxImage(approve: Boolean) {
        val cur = _ui.value
        viewModelScope.launch {
            val res = displayStore.switchSandboxImage(cur.profile, approve)
            if (res.isSuccess && cur.screenOpen && res.getOrNull()?.first?.running == true) {
                observeAndOpenBridge(cur.profile, cur.gatewayUrl)
            }
        }
    }

    fun noteSurfaceFailure(message: String) {
        _ui.update { it.copy(surfaceFailure = message) }
    }

    /** Called on Android `ON_STOP` so an active human lease survives brief app switches for OTP. */
    fun onAppBackgrounded() {
        val bridge = _ui.value.bridge ?: return
        displayRelay.closeBridge(bridge.wsToken, preserveLeaseOnDisconnect = true)
        _ui.update { it.copy(bridge = null, wsConnected = false) }
    }

    /** Called on Android `ON_START` to re-attach live stream using the preserved `viewer_id`. */
    fun onAppForegrounded() {
        val cur = _ui.value
        if (!cur.screenOpen || cur.bridge != null) return
        val status = displayStore.stateOf(cur.profile).status
        if (status?.running == true) {
            viewModelScope.launch { observeAndOpenBridge(cur.profile, cur.gatewayUrl) }
        }
    }

    private suspend fun refreshAndConnect(profile: String, gatewayUrl: GatewayUrl?) {
        ensureAssets()
        val stRes = displayStore.refreshStatus(profile)
        val status = stRes.getOrNull() ?: return
        if (status.running && _ui.value.screenOpen && _ui.value.profile == profile) {
            observeAndOpenBridge(profile, gatewayUrl)
        }
    }

    private suspend fun observeAndOpenBridge(profile: String, gatewayUrl: GatewayUrl?) {
        ensureAssets()
        val resolvedGateway = gatewayUrl ?: gateways.list.value.current?.gatewayUrl ?: return
        val obs = displayStore.observeScreen(profile).getOrNull() ?: return
        if (!_ui.value.screenOpen || _ui.value.profile != profile) return
        _ui.value.bridge?.let { displayRelay.closeBridge(it.wsToken, preserveLeaseOnDisconnect = true) }
        val bridge = displayRelay.prepareBridge(resolvedGateway, obs.path, obs.ticket)
        _ui.update {
            it.copy(
                bridge = bridge,
                wsConnected = false,
                disconnectNotice = null,
                surfaceFailure = null,
            )
        }
    }

    private suspend fun ensureAssets() {
        if (assetsRegistered) return
        try {
            val (html, js) = loadAssets()
            displayRelay.registerAssets(html, js)
            assetsRegistered = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Leave fallback HTML active and report failure if viewer opens
        }
    }

    private fun handleRelayEvent(event: RelayEvent) {
        val cur = _ui.value
        when (event) {
            is RelayEvent.WsConnected -> {
                if (cur.bridge?.wsToken == event.wsToken) {
                    _ui.update { it.copy(wsConnected = true, disconnectNotice = null) }
                }
            }
            is RelayEvent.WsDisconnected -> {
                if (cur.bridge?.wsToken == event.wsToken) {
                    val notice = when (event.code) {
                        DisplayCloseCodes.EVICTED -> ScreenDisconnectNotice.Evicted
                        DisplayCloseCodes.DISPLAY_STOPPED -> ScreenDisconnectNotice.Stopped
                        DisplayCloseCodes.UNAUTHORIZED, DisplayCloseCodes.FORBIDDEN -> ScreenDisconnectNotice.Expired
                        else -> if (event.clean) null else ScreenDisconnectNotice.Lost
                    }
                    _ui.update { it.copy(bridge = null, wsConnected = false, disconnectNotice = notice) }
                    viewModelScope.launch { displayStore.refreshStatus(cur.profile, quiet = true) }
                }
            }
            is RelayEvent.ClientEvent -> {
                when (event.type) {
                    "desktopname" -> {
                        val name = (event.payload["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        displayStore.noteDesktopName(cur.profile, name)
                    }
                    "clipboard" -> {
                        val text = (event.payload["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        if (text.isNotEmpty()) {
                            _ui.update { it.copy(remoteClipboardText = text) }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        val bridge = _ui.value.bridge
        if (bridge != null) {
            val held = displayStore.stateOf(_ui.value.profile).iHoldLease
            displayRelay.closeBridge(bridge.wsToken, preserveLeaseOnDisconnect = held)
        }
    }

    companion object {
        const val AUTO_RAISE_COOLDOWN_MS = 30_000L
    }
}
