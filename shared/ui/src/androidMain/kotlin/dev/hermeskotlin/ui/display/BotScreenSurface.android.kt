package dev.hermeskotlin.ui.display

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.hermeskotlin.core.display.DisplayBridgeSession
import dev.hermeskotlin.core.display.ScreenQuality
import dev.hermeskotlin.core.network.HermesJson
import dev.hermeskotlin.ui.KeepScreenOn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.abs

private class AndroidScreenJsHost(
    private val onRawEvent: (String) -> Unit,
) {
    @JavascriptInterface
    fun onEvent(rawJson: String) {
        onRawEvent(rawJson)
    }
}

@SuppressLint("SetJavaScriptEnabled")
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
    KeepScreenOn(on = true)

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val latestOnEvent by rememberUpdatedState(onEvent)
    val latestOnFailure by rememberUpdatedState(onFailure)
    val latestInteractive by rememberUpdatedState(interactive)
    val latestQuality by rememberUpdatedState(quality)
    val latestBridge by rememberUpdatedState(bridge)

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var lastTapMs by remember { mutableStateOf(0L) }

    fun evalJs(js: String) {
        val wv = webViewRef ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }

    fun applyTransform(scale: Float, tx: Float, ty: Float, widthPx: Float, heightPx: Float) {
        val clampedScale = scale.coerceIn(1f, 4f)
        val cssW = (widthPx / density).coerceAtLeast(1f)
        val cssH = (heightPx / density).coerceAtLeast(1f)
        val maxPanX = 0f
        val minPanX = -(cssW * (clampedScale - 1f))
        val maxPanY = 0f
        val minPanY = -(cssH * (clampedScale - 1f))
        zoomScale = clampedScale
        panX = if (clampedScale <= 1.01f) 0f else tx.coerceIn(minPanX, maxPanX)
        panY = if (clampedScale <= 1.01f) 0f else ty.coerceIn(minPanY, maxPanY)
        evalJs(ScreenScriptBuilder.action(BotScreenAction.ViewportTransform(zoomScale, panX, panY)))
    }

    LaunchedEffect(pageLoaded, bridge.wsUrl) {
        if (pageLoaded) {
            evalJs(
                ScreenScriptBuilder.connect(
                    wsUrl = bridge.wsUrl,
                    interactive = interactive,
                    quality = quality,
                    showDotCursor = interactive && touchMode == TouchControlMode.Trackpad,
                    mobileTouch = true,
                ),
            )
        }
    }

    LaunchedEffect(pageLoaded, interactive, quality, touchMode) {
        if (pageLoaded) {
            evalJs(
                ScreenScriptBuilder.updateOptions(
                    interactive = interactive,
                    quality = quality,
                    showDotCursor = interactive && touchMode == TouchControlMode.Trackpad,
                ),
            )
        }
    }

    LaunchedEffect(dragLocked) {
        if (pageLoaded && interactive) {
            if (dragLocked) {
                evalJs("window.mbScreenApi&&window.mbScreenApi.pointerDown(-1,-1,1);")
            } else {
                evalJs("window.mbScreenApi&&window.mbScreenApi.pointerUp(-1,-1);")
            }
        }
    }

    DisposableEffect(controller) {
        controller.onAction = { act ->
            if (act is BotScreenAction.ResetZoom) {
                zoomScale = 1f
                panX = 0f
                panY = 0f
            }
            evalJs(ScreenScriptBuilder.action(act))
        }
        onDispose { controller.onAction = null }
    }

    val webView = remember(context) {
        WebView(context).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false

            addJavascriptInterface(
                AndroidScreenJsHost { raw ->
                    val obj = runCatching { HermesJson.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                        ?: return@AndroidScreenJsHost
                    val type = (obj["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    if (type == "ready") {
                        val b = latestBridge
                        post {
                            evaluateJavascript(
                                ScreenScriptBuilder.connect(
                                    wsUrl = b.wsUrl,
                                    interactive = latestInteractive,
                                    quality = latestQuality,
                                    showDotCursor = latestInteractive,
                                    mobileTouch = true,
                                ),
                                null,
                            )
                        }
                    }
                    if (type.isNotEmpty()) {
                        post { latestOnEvent(type, obj) }
                    }
                },
                "mbHost",
            )

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val reqUri = request?.url ?: return true
                    val allowedHost = latestBridge.hostPort.substringBefore(':')
                    val allowedPort = latestBridge.hostPort.substringAfter(':', "").toIntOrNull()
                    val reqPort = if (reqUri.port == -1) 80 else reqUri.port
                    return !(reqUri.host == allowedHost && reqPort == allowedPort)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    pageLoaded = true
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        latestOnFailure(error?.description?.toString() ?: "Failed to load Bot Screen viewer.")
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        webViewRef = webView
        webView.loadUrl(bridge.viewerUrl)
        onDispose {
            pageLoaded = false
            webView.evaluateJavascript("window.mbScreenApi&&window.mbScreenApi.disconnect();", null)
            webView.removeJavascriptInterface("mbHost")
            webView.destroy()
            webViewRef = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
        // Touch/gesture layer handling Trackpad vs Direct Touch, 2-finger pinch zoom & scroll,
        // without stealing WebView hardware canvas rendering.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(interactive, touchMode, dragLocked, pageLoaded) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startTime = System.currentTimeMillis()
                        var maxPointers = 1
                        var movedPastSlop = false
                        var totalMove = 0f
                        var isPinchOrZoom = false
                        var directPressSent = false
                        val firstPos = down.position

                        if (interactive && touchMode == TouchControlMode.DirectTouch && !dragLocked) {
                            // Wait slightly before committing down unless user drags, so 2-finger pinch doesn't click
                        }

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val active = event.changes.filter { it.pressed }
                            maxPointers = maxOf(maxPointers, event.changes.size)

                            if (event.changes.size >= 2) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroidDelta = pan
                                if (abs(zoom - 1f) >= 0.04f || isPinchOrZoom || zoomScale > 1.03f) {
                                    isPinchOrZoom = true
                                    val c = event.calculateCentroid(useCurrent = false)
                                    val cCssX = c.x / density
                                    val cCssY = c.y / density
                                    val oldScale = zoomScale
                                    val newScale = (oldScale * zoom).coerceIn(1f, 4f)
                                    val scaleRatio = newScale / oldScale
                                    val nextPanX = cCssX - (cCssX - panX) * scaleRatio + centroidDelta.x / density
                                    val nextPanY = cCssY - (cCssY - panY) * scaleRatio + centroidDelta.y / density
                                    applyTransform(newScale, nextPanX, nextPanY, size.width.toFloat(), size.height.toFloat())
                                } else if (interactive && (abs(pan.x) > 1f || abs(pan.y) > 1f)) {
                                    // 2-finger parallel swipe -> remote scroll wheel
                                    evalJs("window.mbScreenApi&&window.mbScreenApi.pointerWheel(${-pan.x / density},${-pan.y / density});")
                                }
                                totalMove += abs(centroidDelta.x) + abs(centroidDelta.y)
                                if (totalMove > touchSlop) movedPastSlop = true
                                event.changes.forEach { it.consume() }
                            } else if (active.size == 1 && maxPointers == 1) {
                                val ch = active.first()
                                val delta = ch.position - ch.previousPosition
                                totalMove += abs(delta.x) + abs(delta.y)
                                if (totalMove > touchSlop) movedPastSlop = true

                                if (movedPastSlop && ch.positionChanged()) {
                                    if (!interactive) {
                                        if (zoomScale > 1.02f) {
                                            applyTransform(
                                                zoomScale,
                                                panX + delta.x / density,
                                                panY + delta.y / density,
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                            )
                                        }
                                    } else if (touchMode == TouchControlMode.Trackpad) {
                                        val dxCss = (delta.x / density) * TRACKPAD_GAIN
                                        val dyCss = (delta.y / density) * TRACKPAD_GAIN
                                        evalJs("window.mbScreenApi&&window.mbScreenApi.pointerMoveRelative($dxCss,$dyCss);")
                                    } else {
                                        val vx = ch.position.x / density
                                        val vy = ch.position.y / density
                                        if (!directPressSent) {
                                            directPressSent = true
                                            evalJs("window.mbScreenApi&&window.mbScreenApi.pointerDownAtViewPx($vx,$vy,1);")
                                        } else {
                                            evalJs("window.mbScreenApi&&window.mbScreenApi.pointerMoveAtViewPx($vx,$vy);")
                                        }
                                    }
                                    ch.consume()
                                }
                            }

                            if (active.isEmpty()) {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (directPressSent) {
                                    evalJs("window.mbScreenApi&&window.mbScreenApi.pointerUp(-1,-1);")
                                } else if (!movedPastSlop) {
                                    if (maxPointers >= 2 && interactive && elapsed < 350L) {
                                        evalJs("window.mbScreenApi&&window.mbScreenApi.pointerTap(-1,-1,2);")
                                    } else if (maxPointers == 1) {
                                        val now = System.currentTimeMillis()
                                        if (!interactive) {
                                            if (now - lastTapMs < 280L) {
                                                val targetZoom = if (zoomScale > 1.2f) 1f else 2.2f
                                                if (targetZoom <= 1f) {
                                                    applyTransform(1f, 0f, 0f, size.width.toFloat(), size.height.toFloat())
                                                } else {
                                                    val fx = firstPos.x / density
                                                    val fy = firstPos.y / density
                                                    applyTransform(
                                                        targetZoom,
                                                        fx - fx * targetZoom,
                                                        fy - fy * targetZoom,
                                                        size.width.toFloat(),
                                                        size.height.toFloat(),
                                                    )
                                                }
                                                lastTapMs = 0L
                                            } else {
                                                lastTapMs = now
                                            }
                                        } else if (touchMode == TouchControlMode.Trackpad) {
                                            if (elapsed >= 450L) {
                                                evalJs("window.mbScreenApi&&window.mbScreenApi.pointerTap(-1,-1,2);")
                                            } else {
                                                evalJs("window.mbScreenApi&&window.mbScreenApi.pointerTap(-1,-1,1);")
                                            }
                                        } else {
                                            val vx = firstPos.x / density
                                            val vy = firstPos.y / density
                                            val buttonMask = if (elapsed >= 450L) 2 else 1
                                            evalJs("window.mbScreenApi&&window.mbScreenApi.pointerTapAtViewPx($vx,$vy,$buttonMask);")
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                },
        )
    }
}

@Composable
actual fun BotScreenOrientationEffect(landscape: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() } ?: return

    DisposableEffect(activity, view, landscape) {
        val previousOrientation = activity.requestedOrientation
        val controller = WindowCompat.getInsetsController(activity.window, view)
        if (landscape) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (landscape) {
                activity.requestedOrientation = previousOrientation
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    this as? Activity ?: (this as? ContextWrapper)?.baseContext?.findActivity()

private const val TRACKPAD_GAIN = 1.25f
