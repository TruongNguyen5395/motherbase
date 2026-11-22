package dev.hermeskotlin.core.display

import dev.hermeskotlin.core.gateway.GatewayUrl
import dev.hermeskotlin.core.network.HermesJson
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

actual class DisplayRelay actual constructor(
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
) {
    private data class BridgeTarget(
        val profile: String,
        val gatewayUrl: GatewayUrl,
        val path: String,
        val ticket: String,
    )

    private class ActiveBridge(
        val wsToken: String,
        val localSocket: Socket,
        val job: Job,
        val upstreamRef: java.util.concurrent.atomic.AtomicReference<DefaultClientWebSocketSession?> =
            java.util.concurrent.atomic.AtomicReference(null),
        val preserveLeaseOnClose: AtomicBoolean = AtomicBoolean(false),
    )

    private val lock = Any()
    private val random = SecureRandom()
    private val relaySecret: String = randomHex(24)

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var boundPort: Int = 0

    private val closed = AtomicBoolean(false)

    @Volatile
    private var viewerHtml: ByteArray = DEFAULT_PLACEHOLDER_HTML

    @Volatile
    private var rfbJs: ByteArray = ByteArray(0)

    private val targets = LinkedHashMap<String, BridgeTarget>()
    private val activeBridges = ConcurrentHashMap<String, ActiveBridge>()
    private val socketExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "mb-display-relay-client").apply { isDaemon = true }
    }

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 64)
    actual val events: SharedFlow<RelayEvent> = _events.asSharedFlow()

    actual val localHostPort: String
        get() = "127.0.0.1:${ensureStarted()}"

    actual val localOrigin: String
        get() = "http://$localHostPort"

    actual fun registerAssets(viewerHtml: ByteArray, rfbJs: ByteArray) {
        if (viewerHtml.isNotEmpty()) this.viewerHtml = viewerHtml
        if (rfbJs.isNotEmpty()) this.rfbJs = rfbJs
    }

    actual fun prepareBridge(
        gatewayUrl: GatewayUrl,
        path: String,
        ticket: String,
    ): DisplayBridgeSession {
        val port = ensureStarted()
        val wsToken = randomHex(24)
        synchronized(targets) {
            targets[wsToken] = BridgeTarget(
                profile = "",
                gatewayUrl = gatewayUrl,
                path = path,
                ticket = ticket,
            )
            while (targets.size > MAX_PENDING_TARGETS) {
                val oldestKey = targets.keys.firstOrNull() ?: break
                targets.remove(oldestKey)
            }
        }
        return DisplayBridgeSession(
            viewerUrl = "http://127.0.0.1:$port/$relaySecret/viewer.html",
            wsUrl = "ws://127.0.0.1:$port/$relaySecret/ws/$wsToken",
            hostPort = "127.0.0.1:$port",
            wsToken = wsToken,
        )
    }

    actual fun closeBridge(wsToken: String, preserveLeaseOnDisconnect: Boolean) {
        synchronized(targets) {
            targets.remove(wsToken)
        }
        val active = activeBridges.remove(wsToken) ?: return
        active.preserveLeaseOnClose.set(preserveLeaseOnDisconnect)
        val upstream = active.upstreamRef.get()
        if (upstream != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    if (preserveLeaseOnDisconnect) {
                        upstream.close(CloseReason(DisplayCloseCodes.BACKGROUNDED.toShort(), "background"))
                    } else {
                        upstream.close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
                    }
                }
                active.job.cancel()
            }
        } else {
            active.job.cancel()
        }
        runCatching { active.localSocket.close() }
    }

    internal fun shutdownForTests() {
        if (!closed.compareAndSet(false, true)) return
        activeBridges.keys.toList().forEach { closeBridge(it, preserveLeaseOnDisconnect = true) }
        runCatching { serverSocket?.close() }
        socketExecutor.shutdownNow()
    }

    private fun ensureStarted(): Int {
        if (boundPort > 0 && serverSocket?.isClosed == false) return boundPort
        synchronized(lock) {
            if (boundPort > 0 && serverSocket?.isClosed == false) return boundPort
            val server = ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            serverSocket = server
            boundPort = server.localPort
            closed.set(false)
            thread(isDaemon = true, name = "mb-display-relay-accept") {
                acceptLoop(server)
            }
            return boundPort
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (!closed.get() && !server.isClosed) {
            val socket = try {
                server.accept()
            } catch (_: Exception) {
                break
            }
            if (!socket.inetAddress.isLoopbackAddress) {
                runCatching { socket.close() }
                continue
            }
            socketExecutor.execute {
                handleConnection(socket)
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        var keepOpenForWs = false
        try {
            socket.soTimeout = 15_000
            socket.tcpNoDelay = true
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestLine = readAsciiLine(input, MAX_HEADER_LINE) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                writeHttp(output, "400 Bad Request", "text/plain", ByteArray(0))
                return
            }
            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val path = rawTarget.substringBefore('?')

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readAsciiLine(input, MAX_HEADER_LINE) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val name = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[name] = value
                }
                if (headers.size > MAX_HEADER_COUNT) {
                    writeHttp(output, "431 Request Header Fields Too Large", "text/plain", ByteArray(0))
                    return
                }
            }

            if (!isHostAllowed(headers["host"], boundPort)) {
                writeHttp(output, "403 Forbidden", "text/plain", ByteArray(0))
                return
            }

            val secretPrefix = "/$relaySecret/"
            if (!path.startsWith(secretPrefix)) {
                writeHttp(output, "403 Forbidden", "text/plain", ByteArray(0))
                return
            }
            val subPath = path.removePrefix(secretPrefix)

            when {
                method == "GET" && subPath == "viewer.html" -> {
                    writeHttp(output, "200 OK", "text/html; charset=utf-8", viewerHtml)
                }
                method == "GET" && subPath == "rfb.js" -> {
                    if (rfbJs.isEmpty()) {
                        writeHttp(output, "503 Service Unavailable", "text/plain", "Assets not loaded".encodeToByteArray())
                    } else {
                        writeHttp(output, "200 OK", "application/javascript; charset=utf-8", rfbJs)
                    }
                }
                method == "POST" && subPath == "event" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_EVENT_BYTES) ?: 0
                    val body = if (contentLength > 0) readExact(input, contentLength) else ByteArray(0)
                    val parsed = runCatching {
                        HermesJson.parseToJsonElement(body.decodeToString()) as? JsonObject
                    }.getOrNull()
                    val type = (parsed?.get("type") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (type.isNotEmpty() && parsed != null) {
                        _events.tryEmit(RelayEvent.ClientEvent(type, parsed))
                    }
                    writeHttp(output, "204 No Content", "text/plain", ByteArray(0))
                }
                method == "GET" && subPath.startsWith("ws/") -> {
                    val wsToken = subPath.removePrefix("ws/")
                    val target = synchronized(targets) { targets.remove(wsToken) }
                    val wsKey = headers["sec-websocket-key"]
                    val upgrade = headers["upgrade"]
                    if (target == null || wsKey.isNullOrBlank() || !upgrade.equals("websocket", ignoreCase = true)) {
                        writeHttp(output, "403 Forbidden", "text/plain", ByteArray(0))
                        return
                    }
                    keepOpenForWs = true
                    socket.soTimeout = 0
                    startWebSocketBridge(wsToken, target, wsKey, socket, input, output)
                }
                else -> {
                    writeHttp(output, "404 Not Found", "text/plain", ByteArray(0))
                }
            }
        } catch (_: Exception) {
            // Connection dropped cleanly or malformed request
        } finally {
            if (!keepOpenForWs) {
                runCatching { socket.close() }
            }
        }
    }

    private fun startWebSocketBridge(
        wsToken: String,
        target: BridgeTarget,
        wsKey: String,
        socket: Socket,
        input: InputStream,
        output: OutputStream,
    ) {
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((wsKey.trim() + WS_GUID).toByteArray(Charsets.US_ASCII)),
        )
        val handshake = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ").append(accept).append("\r\n")
            append("\r\n")
        }
        output.write(handshake.toByteArray(Charsets.US_ASCII))
        output.flush()

        val cleanPath = target.path.trimStart('/').ifEmpty { "api/display/ws" }
        val baseWs = target.gatewayUrl.siblingSocketUrl(cleanPath)
        val sep = if ('?' in baseWs) '&' else '?'
        val upstreamUrl = "$baseWs${sep}display_ticket=${target.ticket}"
        val writeLock = Any()

        lateinit var active: ActiveBridge
        val job = scope.launch(Dispatchers.IO) {
            var disconnectCode: Int? = null
            var disconnectReason = ""
            var clean = false
            try {
                val upstream = httpClient.webSocketSession(upstreamUrl)
                active.upstreamRef.set(upstream)
                _events.tryEmit(RelayEvent.WsConnected(wsToken))

                val downReader = launch(Dispatchers.IO) {
                    pumpClientToUpstream(input, output, writeLock, upstream, active)
                }

                try {
                    var fragmentStarted = false
                    for (frame in upstream.incoming) {
                        when (frame) {
                            is Frame.Binary -> {
                                val opcode = if (fragmentStarted) OP_CONT else OP_BINARY
                                writeWsFrame(output, writeLock, fin = frame.fin, opcode = opcode, payload = frame.readBytes())
                                fragmentStarted = !frame.fin
                            }
                            is Frame.Text -> {
                                val opcode = if (fragmentStarted) OP_CONT else OP_TEXT
                                writeWsFrame(output, writeLock, fin = frame.fin, opcode = opcode, payload = frame.readBytes())
                                fragmentStarted = !frame.fin
                            }
                            is Frame.Ping -> {
                                writeWsFrame(output, writeLock, fin = true, opcode = OP_PONG, payload = frame.readBytes())
                            }
                            is Frame.Close -> Unit
                            else -> Unit
                        }
                    }
                    val closeReason = runCatching { upstream.closeReason.await() }.getOrNull()
                    disconnectCode = closeReason?.code?.toInt()
                    disconnectReason = closeReason?.message.orEmpty()
                    clean = disconnectCode == DisplayCloseCodes.CLEAN || disconnectCode == DisplayCloseCodes.GOING_AWAY
                    sendCloseToBrowser(output, writeLock, disconnectCode, disconnectReason)
                } finally {
                    downReader.cancel()
                    runCatching { upstream.cancel() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                disconnectCode = DisplayCloseCodes.DROPPED
                disconnectReason = e.message.orEmpty()
                clean = false
                sendCloseToBrowser(output, writeLock, null, "")
            } finally {
                activeBridges.remove(wsToken, active)
                runCatching { socket.close() }
                if (!active.preserveLeaseOnClose.get()) {
                    _events.tryEmit(
                        RelayEvent.WsDisconnected(
                            wsToken = wsToken,
                            code = disconnectCode,
                            reason = disconnectReason,
                            clean = clean,
                        ),
                    )
                }
            }
        }
        active = ActiveBridge(wsToken = wsToken, localSocket = socket, job = job)
        activeBridges[wsToken] = active
    }

    private suspend fun pumpClientToUpstream(
        input: InputStream,
        output: OutputStream,
        writeLock: Any,
        upstream: DefaultClientWebSocketSession,
        active: ActiveBridge,
    ) {
        var fragmentOpcode = OP_BINARY
        val fragmentBuffer = ByteArrayOutputStream()
        try {
            while (scope.isActive && !active.localSocket.isClosed) {
                val b0 = input.read()
                if (b0 < 0) break
                val b1 = input.read()
                if (b1 < 0) break
                val fin = (b0 and 0x80) != 0
                val opcode = b0 and 0x0F
                val masked = (b1 and 0x80) != 0
                var len = (b1 and 0x7F).toLong()
                if (len == 126L) {
                    val hi = input.read()
                    val lo = input.read()
                    if (hi < 0 || lo < 0) break
                    len = ((hi shl 8) or lo).toLong()
                } else if (len == 127L) {
                    val lenBytes = readExact(input, 8)
                    len = 0L
                    for (b in lenBytes) {
                        len = (len shl 8) or (b.toLong() and 0xFFL)
                    }
                }
                if (len < 0 || len > MAX_WS_FRAME_BYTES) break
                val maskKey = if (masked) readExact(input, 4) else null
                val payload = if (len > 0) readExact(input, len.toInt()) else ByteArray(0)
                if (maskKey != null) {
                    for (i in payload.indices) {
                        payload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
                    }
                }

                when (opcode) {
                    OP_PING -> writeWsFrame(output, writeLock, fin = true, opcode = OP_PONG, payload = payload)
                    OP_PONG -> Unit
                    OP_CLOSE -> {
                        val code = if (payload.size >= 2) {
                            ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                        } else {
                            DisplayCloseCodes.CLEAN
                        }
                        val reason = if (payload.size > 2) payload.decodeToString(2, payload.size) else ""
                        if (active.preserveLeaseOnClose.get()) {
                            upstream.close(CloseReason(DisplayCloseCodes.BACKGROUNDED.toShort(), "background"))
                        } else {
                            upstream.close(CloseReason(code.toShort(), reason))
                        }
                        break
                    }
                    OP_TEXT, OP_BINARY -> {
                        if (fin) {
                            val frame = if (opcode == OP_TEXT) Frame.Text(true, payload) else Frame.Binary(true, payload)
                            upstream.send(frame)
                        } else {
                            fragmentOpcode = opcode
                            fragmentBuffer.reset()
                            fragmentBuffer.write(payload)
                        }
                    }
                    OP_CONT -> {
                        if (fragmentBuffer.size() + payload.size > MAX_WS_FRAME_BYTES) break
                        fragmentBuffer.write(payload)
                        if (fin) {
                            val combined = fragmentBuffer.toByteArray()
                            fragmentBuffer.reset()
                            val frame = if (fragmentOpcode == OP_TEXT) Frame.Text(true, combined) else Frame.Binary(true, combined)
                            upstream.send(frame)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Local socket closed or upstream closed while reading
        }
    }

    private fun sendCloseToBrowser(output: OutputStream, writeLock: Any, code: Int?, reason: String) {
        runCatching {
            val payload = if (code != null) {
                val reasonBytes = reason.encodeToByteArray().let { if (it.size > 120) it.copyOf(120) else it }
                ByteArray(2 + reasonBytes.size).apply {
                    this[0] = ((code ushr 8) and 0xFF).toByte()
                    this[1] = (code and 0xFF).toByte()
                    reasonBytes.copyInto(this, destinationOffset = 2)
                }
            } else {
                ByteArray(0)
            }
            writeWsFrame(output, writeLock, fin = true, opcode = OP_CLOSE, payload = payload)
        }
    }

    private fun writeWsFrame(output: OutputStream, writeLock: Any, fin: Boolean, opcode: Int, payload: ByteArray) {
        synchronized(writeLock) {
            val b0 = (if (fin) 0x80 else 0) or (opcode and 0x0F)
            output.write(b0)
            val len = payload.size
            when {
                len < 126 -> output.write(len)
                len <= 0xFFFF -> {
                    output.write(126)
                    output.write((len ushr 8) and 0xFF)
                    output.write(len and 0xFF)
                }
                else -> {
                    output.write(127)
                    val l = len.toLong()
                    for (shift in 56 downTo 0 step 8) {
                        output.write(((l ushr shift) and 0xFF).toInt())
                    }
                }
            }
            if (len > 0) output.write(payload)
            output.flush()
        }
    }

    private fun writeHttp(output: OutputStream, status: String, contentType: String, body: ByteArray) {
        val header = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun isHostAllowed(host: String?, port: Int): Boolean {
        if (host.isNullOrBlank()) return false
        val normalized = host.trim().lowercase()
        return normalized == "127.0.0.1:$port" ||
            normalized == "localhost:$port" ||
            normalized == "[::1]:$port" ||
            normalized == "127.0.0.1" ||
            normalized == "localhost"
    }

    private fun readAsciiLine(input: InputStream, maxLen: Int): String? {
        val out = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else out.toString(Charsets.US_ASCII.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                if (out.size() >= maxLen) throw IllegalArgumentException("Header line too long")
                out.write(b)
            }
        }
        return out.toString(Charsets.US_ASCII.name())
    }

    private fun readExact(input: InputStream, len: Int): ByteArray {
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) throw EOFException("Unexpected EOF")
            off += n
        }
        return buf
    }

    private fun randomHex(bytes: Int): String {
        val raw = ByteArray(bytes)
        random.nextBytes(raw)
        val sb = StringBuilder(bytes * 2)
        for (b in raw) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_HEADER_LINE = 8 * 1024
        private const val MAX_HEADER_COUNT = 64
        private const val MAX_EVENT_BYTES = 256 * 1024
        private const val MAX_WS_FRAME_BYTES = 16 * 1024 * 1024
        private const val MAX_PENDING_TARGETS = 8

        private const val OP_CONT = 0x0
        private const val OP_TEXT = 0x1
        private const val OP_BINARY = 0x2
        private const val OP_CLOSE = 0x8
        private const val OP_PING = 0x9
        private const val OP_PONG = 0xA

        private val DEFAULT_PLACEHOLDER_HTML = "<!doctype html><html><body style='background:#0d1117'></body></html>".encodeToByteArray()
    }
}
