package com.raiferoleplay.game.cef

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONObject

/**
 * Wrapper around a WebView instance so the native layer can manage overlays via CefManager.
 */
class CefView(
    private val context: Context,
    private val browserId: Int,
    private val initialX: Int,
    private val initialY: Int,
    private val initialWidth: Int,
    private val initialHeight: Int,
    private val initialUrl: String,
    private val initialVisible: Boolean = true,
    private val initialBgAlpha: Int = 255,
    private val offscreen: Boolean = false
) {
    private val tag = "CefView[$browserId]"

    private val webView: WebView
    private val container: FrameLayout
    private val eventCallback: CefManager.CefEventCallback? = CefManager.getInstance()?.getEventCallback()

    init {
        container = FrameLayout(context).apply {
            val w = if (initialWidth <= 0) FrameLayout.LayoutParams.MATCH_PARENT else initialWidth
            val h = if (initialHeight <= 0) FrameLayout.LayoutParams.MATCH_PARENT else initialHeight
            layoutParams = FrameLayout.LayoutParams(w, h)
            x = initialX.toFloat()
            y = initialY.toFloat()
            val lower = initialUrl.lowercase()
            val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
            setBackgroundColor(if (isHttp) Color.TRANSPARENT else Color.argb(initialBgAlpha, 0, 0, 0))
            visibility = if (initialVisible) View.VISIBLE else View.GONE
        }

        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            isFocusable = false
            isFocusableInTouchMode = false
            isHapticFeedbackEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                setAllowFileAccessFromFileURLs(true)
                @Suppress("DEPRECATION")
                setAllowUniversalAccessFromFileURLs(true)
            }

            setBackgroundColor(Color.TRANSPARENT)
            setWillNotDraw(false)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    url?.let {
                        eventCallback?.onPageLoaded(browserId, it)
                        eventCallback?.onUrlChanged(browserId, it)
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    error?.let {
                        eventCallback?.onError(browserId, it.errorCode, it.description.toString())
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { eventCallback?.onTitleChanged(browserId, it) }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    consoleMessage?.let {
                        eventCallback?.onConsoleMessage(
                            browserId,
                            it.messageLevel().ordinal,
                            it.message()
                        )
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            addJavascriptInterface(HostBridge(), "Android")
        }

        container.addView(webView)

        if (initialUrl.isNotEmpty()) {
            loadUrl(initialUrl)
        }
        Log.d(tag, "Created at ($initialX,$initialY) size ${initialWidth}x$initialHeight url=$initialUrl")
    }

    inner class HostBridge {
        @JavascriptInterface
        fun onEvent(eventType: String, data: String) {
            Log.d(tag, "JS Event: $eventType -> $data")
        }

        @JavascriptInterface
        fun sendToServer(data: String) {
            Log.d(tag, "JS -> server: $data")
            CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_CONSOLE, data)
        }

        @JavascriptInterface
        fun sendMessage(message: String) {
            Log.d(tag, "JS message: $message")
            CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_CONSOLE, message)
        }
    }

    private fun normalizeUrl(url: String): String {
        val assetsPrefix = "resource://assets/"
        if (url.startsWith(assetsPrefix, ignoreCase = true)) {
            val suffix = url.substring(assetsPrefix.length)
            val clean = if (suffix.startsWith("/")) suffix.substring(1) else suffix
            return "file:///android_asset/" + clean
        }
        val resPrefix = "resource://res/"
        if (url.startsWith(resPrefix, ignoreCase = true)) {
            val suffix = url.substring(resPrefix.length)
            val clean = if (suffix.startsWith("/")) suffix.substring(1) else suffix
            return "file:///android_res/" + clean
        }
        return url
    }

    fun loadUrl(url: String) {
        val normalized = normalizeUrl(url)
        webView.loadUrl(normalized)
        Log.d(tag, "Loading URL: $normalized")
    }

    fun reload() {
        webView.reload()
    }

    fun stop() {
        webView.stopLoading()
    }

    fun evaluateJavaScript(script: String, callback: ValueCallback<String>? = null) {
        webView.evaluateJavascript(script, callback)
    }

    fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        container.x = x.toFloat()
        container.y = y.toFloat()
        val w = if (width <= 0) FrameLayout.LayoutParams.MATCH_PARENT else width
        val h = if (height <= 0) FrameLayout.LayoutParams.MATCH_PARENT else height
        container.layoutParams = FrameLayout.LayoutParams(w, h)
    }

    fun setVisible(visible: Boolean) {
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setFocus(focused: Boolean) {
        if (focused) {
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            webView.requestFocus()
        } else {
            webView.clearFocus()
            webView.isFocusable = false
            webView.isFocusableInTouchMode = false
        }
    }

    fun setBackgroundAlpha(alpha: Int) {
        container.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
    }

    fun getContainer(): FrameLayout = container
    fun getWebView(): WebView = webView

    fun destroy() {
        webView.destroy()
        container.removeAllViews()
    }

    fun handleMouseMove(x: Int, y: Int) {
        dispatchMotionEvent(MotionEvent.ACTION_MOVE, x, y)
    }

    fun handleMouseDown(x: Int, y: Int, button: Int) {
        dispatchMotionEvent(MotionEvent.ACTION_DOWN, x, y)
    }

    fun handleMouseUp(x: Int, y: Int, button: Int) {
        dispatchMotionEvent(MotionEvent.ACTION_UP, x, y)
    }

    private fun dispatchMotionEvent(action: Int, x: Int, y: Int) {
        val localX = x - container.x.toInt()
        val localY = y - container.y.toInt()
        val event = MotionEvent.obtain(
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            action,
            localX.toFloat(),
            localY.toFloat(),
            0
        )
        webView.dispatchTouchEvent(event)
        event.recycle()
    }

    fun handleScroll(deltaX: Int, deltaY: Int) {
        val event = MotionEvent.obtain(
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            MotionEvent.ACTION_SCROLL,
            webView.width / 2f,
            webView.height / 2f,
            0
        )
        webView.dispatchTouchEvent(event)
        event.recycle()
    }

    fun handleKeyDown(keyCode: Int) {
        webView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
    }

    fun handleKeyUp(keyCode: Int) {
        webView.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    fun handleTextInput(text: String) {
        val escaped = JSONObject.quote(text)
        val script = "if(document.activeElement && typeof document.activeElement.value !== 'undefined'){document.activeElement.value += $escaped;}"
        webView.evaluateJavascript(script, null)
    }

    fun captureWebView(): Bitmap? {
        return try {
            if (webView.width == 0 || webView.height == 0) return null
            val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (t: Throwable) {
            Log.e(tag, "Failed to capture WebView", t)
            null
        }
    }
}
