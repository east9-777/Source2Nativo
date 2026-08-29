package com.raiferoleplay.game.cef

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifetime of overlay WebView instances that are driven from the SA:MP native layer.
 */
class CefManager private constructor(private val activity: Activity) {
    private val tag = "CefManager"

    private val browsers = ConcurrentHashMap<Int, CefView>()

    private var overlayContainer: FrameLayout? = null
    private val windowManager: WindowManager = activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayParams: WindowManager.LayoutParams? = null

    @Volatile
    private var interactive: Boolean = false

    private val visibilityCache = ConcurrentHashMap<Int, Boolean>()
    @Volatile
    private var pausedHidden = false

    @Volatile
    private var eventCallback: CefEventCallback? = null

    companion object {
        @Volatile
        private var INSTANCE: CefManager? = null

        @JvmStatic
        fun getInstance(): CefManager? = INSTANCE

        @JvmStatic
        fun initialize(activity: Activity): CefManager {
            return INSTANCE ?: synchronized(CefManager::class.java) {
                INSTANCE ?: CefManager(activity).also { INSTANCE = it }
            }
        }
    }

    init {
        setupOverlayContainer()
        setupEventCallback()
        Log.i(tag, "CefManager ready")
    }

    private fun setupEventCallback() {
        eventCallback = object : CefEventCallback {
            override fun onPageLoaded(browserId: Int, url: String) {
                CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_LOADED, url)
            }

            override fun onUrlChanged(browserId: Int, url: String) {
                CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_URL_CHANGED, url)
            }

            override fun onTitleChanged(browserId: Int, title: String) {
                CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_TITLE_CHANGED, title)
            }

            override fun onConsoleMessage(browserId: Int, level: Int, message: String) {
                CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_CONSOLE, "$level,$message")
            }

            override fun onError(browserId: Int, errorCode: Int, description: String) {
                CefBrowser.nativeSendEvent(browserId, CefBrowser.CEF_EVENT_ERROR, "$errorCode,$description")
            }
        }
    }

    private fun setupOverlayContainer() {
        activity.runOnUiThread {
            if (overlayContainer != null) return@runOnUiThread

            try {
                overlayContainer = FrameLayout(activity).apply {
                    id = View.generateViewId()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    elevation = 10_000f
                    setBackgroundColor(Color.TRANSPARENT)
                }
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                root.addView(overlayContainer)
                overlayContainer?.bringToFront()
                Log.d(tag, "Overlay container attached")
            } catch (t: Throwable) {
                Log.e(tag, "Failed to set up overlay container", t)
            }
        }
    }

    fun createBrowser(
        browserId: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        url: String,
        visible: Boolean = true,
        bgAlpha: Int = 255,
        offscreen: Boolean = false
    ): Boolean {
        if (browsers.containsKey(browserId)) {
            Log.w(tag, "Browser $browserId already exists")
            return false
        }

        return try {
            activity.runOnUiThread {
                val cefView = CefView(
                    activity,
                    browserId,
                    x, y, width, height,
                    url, visible, bgAlpha, offscreen
                )

                browsers[browserId] = cefView
                overlayContainer?.addView(cefView.getContainer())

                cefView.getWebView().apply {
                    isFocusable = interactive
                    isFocusableInTouchMode = interactive
                }
                Log.i(tag, "Browser $browserId created at ($x,$y) size ${width}x$height url=$url")
            }
            true
        } catch (t: Throwable) {
            Log.e(tag, "Failed to create browser $browserId", t)
            false
        }
    }

    fun destroyBrowser(browserId: Int): Boolean {
        val browser = browsers.remove(browserId) ?: return false
        return try {
            activity.runOnUiThread {
                overlayContainer?.removeView(browser.getContainer())
                browser.destroy()
            }
            Log.i(tag, "Browser $browserId destroyed")
            true
        } catch (t: Throwable) {
            Log.e(tag, "Failed to destroy browser $browserId", t)
            false
        }
    }

    fun loadUrl(browserId: Int, url: String): Boolean = withBrowser(browserId) { it.loadUrl(url) }
    fun reload(browserId: Int): Boolean = withBrowser(browserId) { it.reload() }
    fun stop(browserId: Int): Boolean = withBrowser(browserId) { it.stop() }
    fun evaluateJavaScript(browserId: Int, script: String): Boolean = withBrowser(browserId) { it.evaluateJavaScript(script) }
    fun setBounds(browserId: Int, x: Int, y: Int, width: Int, height: Int): Boolean = withBrowser(browserId) { it.setBounds(x, y, width, height) }
    fun setVisible(browserId: Int, visible: Boolean): Boolean = withBrowser(browserId) { it.setVisible(visible) }
    fun setFocus(browserId: Int, focused: Boolean): Boolean = withBrowser(browserId) { it.setFocus(focused) }
    fun setBackgroundAlpha(browserId: Int, alpha: Int): Boolean = withBrowser(browserId) { it.setBackgroundAlpha(alpha) }
    fun handleMouseMove(browserId: Int, x: Int, y: Int): Boolean = withBrowser(browserId) { it.handleMouseMove(x, y) }
    fun handleMouseDown(browserId: Int, x: Int, y: Int, button: Int): Boolean = withBrowser(browserId) { it.handleMouseDown(x, y, button) }
    fun handleMouseUp(browserId: Int, x: Int, y: Int, button: Int): Boolean = withBrowser(browserId) { it.handleMouseUp(x, y, button) }
    fun handleScroll(browserId: Int, deltaX: Int, deltaY: Int): Boolean = withBrowser(browserId) { it.handleScroll(deltaX, deltaY) }
    fun handleKeyDown(browserId: Int, keyCode: Int): Boolean = withBrowser(browserId) { it.handleKeyDown(keyCode) }
    fun handleKeyUp(browserId: Int, keyCode: Int): Boolean = withBrowser(browserId) { it.handleKeyUp(keyCode) }
    fun handleTextInput(browserId: Int, text: String): Boolean = withBrowser(browserId) { it.handleTextInput(text) }

    private inline fun withBrowser(browserId: Int, crossinline block: (CefView) -> Unit): Boolean {
        val browser = browsers[browserId] ?: return false
        return try {
            activity.runOnUiThread { block(browser) }
            true
        } catch (t: Throwable) {
            Log.e(tag, "Browser $browserId operation failed", t)
            false
        }
    }

    fun hideAllForPause() {
        if (pausedHidden) return
        pausedHidden = true
        activity.runOnUiThread {
            try {
                visibilityCache.clear()
                browsers.forEach { (id, view) ->
                    val wasVisible = view.getContainer().visibility == View.VISIBLE
                    visibilityCache[id] = wasVisible
                    view.setVisible(false)
                }
                Log.i(tag, "All browsers hidden for pause")
            } catch (t: Throwable) {
                Log.e(tag, "Failed to hide browsers for pause", t)
            }
        }
    }

    fun restoreAfterResume() {
        if (!pausedHidden) return
        pausedHidden = false
        activity.runOnUiThread {
            try {
                visibilityCache.forEach { (id, wasVisible) ->
                    browsers[id]?.setVisible(wasVisible)
                }
                visibilityCache.clear()
                Log.i(tag, "Browsers restored after resume")
            } catch (t: Throwable) {
                Log.e(tag, "Failed to restore browsers", t)
            }
        }
    }

    fun setEventCallback(callback: CefEventCallback?) {
        eventCallback = callback
    }

    fun getEventCallback(): CefEventCallback? = eventCallback

    fun getCefView(browserId: Int): CefView? = browsers[browserId]

    fun hasBrowser(browserId: Int): Boolean = browsers.containsKey(browserId)

    fun destroyAll() {
        activity.runOnUiThread {
            browsers.values.forEach { it.destroy() }
            browsers.clear()
            overlayContainer?.let { container ->
                (container.parent as? ViewGroup)?.removeView(container)
            }
            overlayContainer = null
            overlayParams = null
        }
        Log.i(tag, "All browsers destroyed")
    }

    fun setInteractive(enabled: Boolean) {
        if (interactive == enabled) return
        interactive = enabled
        activity.runOnUiThread {
            try {
                browsers.values.forEach { view ->
                    val webView = view.getWebView()
                    webView.isClickable = enabled
                    webView.isLongClickable = enabled
                    webView.isFocusable = enabled
                    webView.isFocusableInTouchMode = enabled
                    if (!enabled) {
                        webView.clearFocus()
                    }
                }
                Log.i(tag, "Interactive mode -> $enabled")
            } catch (t: Throwable) {
                Log.e(tag, "Failed to update interactive mode", t)
            }
        }
    }

    interface CefEventCallback {
        fun onPageLoaded(browserId: Int, url: String)
        fun onUrlChanged(browserId: Int, url: String)
        fun onTitleChanged(browserId: Int, title: String)
        fun onConsoleMessage(browserId: Int, level: Int, message: String)
        fun onError(browserId: Int, errorCode: Int, description: String)
    }
}
