package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.gateway.GatewayUrl
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.JsonObject

data class DisplayBridgeSession(
    val viewerUrl: String,
    val wsUrl: String,
    val hostPort: String,
    val wsToken: String,
)

sealed interface RelayEvent {
    data class WsConnected(val wsToken: String) : RelayEvent
    data class WsDisconnected(
        val wsToken: String,
        val code: Int?,
        val reason: String,
        val clean: Boolean,
    ) : RelayEvent

    data class ClientEvent(
        val type: String,
        val payload: JsonObject,
    ) : RelayEvent
}

/**
 * Loopback HTTP + RFC 6455 WebSocket relay serving bundled noVNC assets and bridging RFB binary
 * frames onto the gateway's authenticated `/api/display/ws?display_ticket=...` endpoint.
 */
expect class DisplayRelay(
    httpClient: HttpClient,
    scope: CoroutineScope,
) {
    val localHostPort: String
    val localOrigin: String
    val events: SharedFlow<RelayEvent>

    fun registerAssets(viewerHtml: ByteArray, rfbJs: ByteArray)

    fun prepareBridge(
        gatewayUrl: GatewayUrl,
        path: String,
        ticket: String,
    ): DisplayBridgeSession

    fun closeBridge(wsToken: String, preserveLeaseOnDisconnect: Boolean)
}
