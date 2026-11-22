package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.createHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayRelayTest {

    @Test
    fun servesStaticAssetsRejectsBadHostsAndBridgesBinaryRfbFrames() = runBlocking {
        val upstreamReqPath = CompletableDeferred<String>()
        val upstreamClosedCode = CompletableDeferred<Int?>()
        val upstreamServer = ServerSocket(0, 4, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        val upstreamThread = thread(isDaemon = true) {
            upstreamServer.accept().use { sock ->
                val input = BufferedInputStream(sock.getInputStream())
                val output = sock.getOutputStream()
                val reqLine = readLine(input)
                upstreamReqPath.complete(reqLine.substringBetween(" ", " "))
                var key = ""
                while (true) {
                    val line = readLine(input)
                    if (line.isEmpty()) break
                    if (line.lowercase().startsWith("sec-websocket-key:")) {
                        key = line.substringAfter(':').trim()
                    }
                }
                val accept = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
                )
                output.write(
                    ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n")
                        .toByteArray(),
                )
                output.flush()

                // Send server banner (RFB 003.008) as binary frame
                writeUnmaskedFrame(output, 0x2, "RFB 003.008\n".toByteArray())

                // Expect client binary echo back, then read close
                val frame = readWsFrame(input)
                assertEquals(0x2, frame.first)
                assertContentEquals("HELLO-RFB".toByteArray(), frame.second)
                // Send close 4000 (evicted)
                val closePayload = byteArrayOf(((4000 ushr 8) and 0xFF).toByte(), (4000 and 0xFF).toByte())
                writeUnmaskedFrame(output, 0x8, closePayload)
                val reply = runCatching { readWsFrame(input) }.getOrNull()
                val code = reply?.second?.takeIf { it.size >= 2 }?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) }
                upstreamClosedCode.complete(code ?: 4000)
            }
        }

        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = createHttpClient()
        val relay = DisplayRelay(client, relayScope)
        relay.registerAssets("<html>viewer</html>".encodeToByteArray(), "export default 1;".encodeToByteArray())

        try {
            val bridge = relay.prepareBridge(
                gatewayUrl = GatewayUrl.parse("http://127.0.0.1:${upstreamServer.localPort}"),
                path = "/api/display/ws",
                ticket = "tkt-999",
            )

            // Static GET checks
            val htmlConn = URL(bridge.viewerUrl).openConnection() as HttpURLConnection
            assertEquals(200, htmlConn.responseCode)
            assertEquals("<html>viewer</html>", htmlConn.inputStream.bufferedReader().readText())

            val jsUrl = bridge.viewerUrl.replace("viewer.html", "rfb.js")
            val jsConn = URL(jsUrl).openConnection() as HttpURLConnection
            assertEquals(200, jsConn.responseCode)
            assertEquals("export default 1;", jsConn.inputStream.bufferedReader().readText())

            // Bad secret check
            val badConn = URL("http://${bridge.hostPort}/wrong-secret/viewer.html").openConnection() as HttpURLConnection
            assertEquals(403, badConn.responseCode)

            // Bad Host header check (DNS rebinding guard)
            Socket("127.0.0.1", bridge.hostPort.substringAfter(':').toInt()).use { raw ->
                raw.getOutputStream().write("GET /wrong/viewer.html HTTP/1.1\r\nHost: evil.example:80\r\n\r\n".toByteArray())
                raw.getOutputStream().flush()
                val resp = readLine(BufferedInputStream(raw.getInputStream()))
                assertTrue(resp.contains("403"))
            }

            // Connect local browser socket to ws://...
            val disconnectedEvent = async {
                withTimeout(5_000) {
                    relay.events.first { it is RelayEvent.WsDisconnected } as RelayEvent.WsDisconnected
                }
            }
            val wsPath = "/" + bridge.wsUrl.substringAfter("://").substringAfter('/')
            val port = bridge.hostPort.substringAfter(':').toInt()
            Socket("127.0.0.1", port).use { ws ->
                val out = ws.getOutputStream()
                val inp = BufferedInputStream(ws.getInputStream())
                val clientKey = Base64.getEncoder().encodeToString("0123456789abcdef".toByteArray())
                out.write(
                    ("GET $wsPath HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $clientKey\r\nSec-WebSocket-Version: 13\r\n\r\n")
                        .toByteArray(),
                )
                out.flush()
                val statusLine = readLine(inp)
                assertTrue(statusLine.contains("101"), "Expected 101, got $statusLine")
                while (readLine(inp).isNotEmpty()) {
                    // consume headers
                }

                // Read RFB banner from upstream through relay
                val banner = readWsFrame(inp)
                assertEquals(0x2, banner.first)
                assertContentEquals("RFB 003.008\n".toByteArray(), banner.second)

                // Send masked client binary frame through relay to upstream
                writeMaskedFrame(out, 0x2, "HELLO-RFB".toByteArray())

                // Receive Close(4000) forwarded from upstream
                val close = readWsFrame(inp)
                assertEquals(0x8, close.first)
            }

            val ev = disconnectedEvent.await()
            assertEquals(4000, ev.code)
            assertFalse(ev.clean)
            assertEquals("/api/display/ws?display_ticket=tkt-999", withTimeout(5_000) { upstreamReqPath.await() })
        } finally {
            relay.shutdownForTests()
            relayScope.cancel()
            client.close()
            upstreamServer.close()
            upstreamThread.join(2_000)
        }
    }

    private fun readLine(input: InputStream): String {
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) out.write(b)
        }
        return out.toString("UTF-8")
    }

    private fun readWsFrame(input: InputStream): Pair<Int, ByteArray> {
        val b0 = input.read()
        val b1 = input.read()
        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        var len = b1 and 0x7F
        if (len == 126) {
            len = ((input.read() shl 8) or input.read())
        }
        val mask = if (masked) ByteArray(4) { input.read().toByte() } else null
        val data = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(data, off, len - off)
            if (n < 0) break
            off += n
        }
        if (mask != null) {
            for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
        }
        return opcode to data
    }

    private fun writeUnmaskedFrame(output: OutputStream, opcode: Int, data: ByteArray) {
        output.write(0x80 or opcode)
        output.write(data.size)
        output.write(data)
        output.flush()
    }

    private fun writeMaskedFrame(output: OutputStream, opcode: Int, data: ByteArray) {
        val mask = byteArrayOf(1, 2, 3, 4)
        output.write(0x80 or opcode)
        output.write(0x80 or data.size)
        output.write(mask)
        val masked = ByteArray(data.size) { i -> (data[i].toInt() xor mask[i and 3].toInt()).toByte() }
        output.write(masked)
        output.flush()
    }

    private fun String.substringBetween(a: String, b: String): String =
        substringAfter(a).substringBefore(b)
}
