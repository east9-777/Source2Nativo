package com.raiferoleplay.game.cef

import android.util.Log
import com.raiferoleplay.game.game.SAMP
import org.json.JSONObject

/**
 * Entry point used by native code to drive WebView overlays.
 */
class CefBrowser private constructor() {
    companion object {
        private const val TAG = "CefBrowser"

        const val CEF_CREATE = 1
        const val CEF_DESTROY = 2
        const val CEF_LOAD_URL = 3
        const val CEF_RELOAD = 4
        const val CEF_STOP = 5
        const val CEF_EVAL_JS = 6
        const val CEF_SET_BOUNDS = 7
        const val CEF_SET_VISIBLE = 8
        const val CEF_SET_FOCUS = 9
        const val CEF_SET_BG_ALPHA = 10
        const val CEF_INPUT_MOUSE_MOVE = 11
        const val CEF_INPUT_MOUSE_DOWN = 12
        const val CEF_INPUT_MOUSE_UP = 13
        const val CEF_INPUT_SCROLL = 14
        const val CEF_INPUT_KEY_DOWN = 15
        const val CEF_INPUT_KEY_UP = 16
        const val CEF_INPUT_TEXT = 17
        const val CEF_SET_INTERACTIVE = 18

        const val CEF_EVENT_LOADED = 101
        const val CEF_EVENT_URL_CHANGED = 102
        const val CEF_EVENT_TITLE_CHANGED = 103
        const val CEF_EVENT_CONSOLE = 104
        const val CEF_EVENT_ERROR = 105
        const val CEF_EVENT_CLICK = 106

        @JvmStatic
        fun receiveCefPacket(browserId: Int, actionId: Int, rawData: String) {
            Log.d(TAG, "CEF packet -> browserId=$browserId action=$actionId data=$rawData")
            try {
                when (actionId) {
                    CEF_CREATE -> handleCreate(browserId, rawData)
                    CEF_DESTROY -> CefManager.getInstance()?.destroyBrowser(browserId)
                    CEF_LOAD_URL -> CefManager.getInstance()?.loadUrl(browserId, rawData)
                    CEF_RELOAD -> CefManager.getInstance()?.reload(browserId)
                    CEF_STOP -> CefManager.getInstance()?.stop(browserId)
                    CEF_EVAL_JS -> CefManager.getInstance()?.evaluateJavaScript(browserId, rawData.trimStart('|', '\u0000', '1', ' '))
                    CEF_SET_BOUNDS -> handleSetBounds(browserId, rawData)
                    CEF_SET_VISIBLE -> CefManager.getInstance()?.setVisible(browserId, rawData.equals("true", true))
                    CEF_SET_FOCUS -> CefManager.getInstance()?.setFocus(browserId, rawData.equals("true", true))
                    CEF_SET_BG_ALPHA -> CefManager.getInstance()?.setBackgroundAlpha(browserId, rawData.toIntOrNull() ?: 255)
                    CEF_INPUT_MOUSE_MOVE -> handleIntPair(browserId, rawData) { id, x, y -> CefManager.getInstance()?.handleMouseMove(id, x, y) }
                    CEF_INPUT_MOUSE_DOWN -> handleIntTriple(browserId, rawData) { id, x, y, btn -> CefManager.getInstance()?.handleMouseDown(id, x, y, btn) }
                    CEF_INPUT_MOUSE_UP -> handleIntTriple(browserId, rawData) { id, x, y, btn -> CefManager.getInstance()?.handleMouseUp(id, x, y, btn) }
                    CEF_INPUT_SCROLL -> handleIntPair(browserId, rawData) { id, dx, dy -> CefManager.getInstance()?.handleScroll(id, dx, dy) }
                    CEF_INPUT_KEY_DOWN -> CefManager.getInstance()?.handleKeyDown(browserId, rawData.toIntOrNull() ?: 0)
                    CEF_INPUT_KEY_UP -> CefManager.getInstance()?.handleKeyUp(browserId, rawData.toIntOrNull() ?: 0)
                    CEF_INPUT_TEXT -> CefManager.getInstance()?.handleTextInput(browserId, rawData)
                    CEF_SET_INTERACTIVE -> CefManager.getInstance()?.setInteractive(rawData.equals("true", true))
                    else -> Log.w(TAG, "Unknown CEF action $actionId")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling CEF packet", t)
            }
        }

        private fun handleCreate(browserId: Int, data: String) {
            try {
                var x = 0
                var y = 0
                var width = 800
                var height = 600
                var url = ""
                var visible = true
                var bgAlpha = 255
                var offscreen = false

                val payload = data.dropWhile { it != '{' }.ifEmpty { data }.trim()
                if (payload.startsWith("{")) {
                    val json = JSONObject(payload)
                    x = json.optInt("x", 0)
                    y = json.optInt("y", 0)
                    width = json.optInt("w", 800)
                    height = json.optInt("h", 600)
                    url = json.optString("url", "")
                    visible = json.optBoolean("visible", true)
                    bgAlpha = json.optInt("bgAlpha", 255)
                    offscreen = json.optBoolean("offscreen", false)
                } else {
                    val parts = data.split(",")
                    x = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    y = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    width = parts.getOrNull(2)?.toIntOrNull() ?: 800
                    height = parts.getOrNull(3)?.toIntOrNull() ?: 600
                    url = parts.getOrNull(4) ?: ""
                    visible = parts.getOrNull(5)?.equals("true", true) ?: true
                    bgAlpha = parts.getOrNull(6)?.toIntOrNull() ?: 255
                    offscreen = parts.getOrNull(7)?.equals("true", true) ?: false
                }

                Log.d(TAG, "Create -> id=$browserId (${width}x$height @$x,$y) url=$url")
                CefManager.getInstance()?.createBrowser(browserId, x, y, width, height, url, visible, bgAlpha, offscreen)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse create payload: $data", t)
            }
        }

        private fun handleSetBounds(browserId: Int, data: String) {
            val payload = data.trim()
            if (payload.startsWith("{")) {
                try {
                    val json = JSONObject(payload)
                    val x = json.optInt("x", 0)
                    val y = json.optInt("y", 0)
                    val width = json.optInt("w", 800)
                    val height = json.optInt("h", 600)
                    CefManager.getInstance()?.setBounds(browserId, x, y, width, height)
                } catch (t: Throwable) {
                    Log.e(TAG, "Invalid JSON bounds: $payload", t)
                }
            } else {
                val parts = data.split(",")
                if (parts.size >= 4) {
                    val x = parts[0].toIntOrNull() ?: 0
                    val y = parts[1].toIntOrNull() ?: 0
                    val w = parts[2].toIntOrNull() ?: 800
                    val h = parts[3].toIntOrNull() ?: 600
                    CefManager.getInstance()?.setBounds(browserId, x, y, w, h)
                }
            }
        }

        private inline fun handleIntPair(browserId: Int, data: String, block: (Int, Int, Int) -> Unit) {
            val parts = data.split(",")
            if (parts.size >= 2) {
                val first = parts[0].toIntOrNull() ?: 0
                val second = parts[1].toIntOrNull() ?: 0
                block(browserId, first, second)
            }
        }

        private inline fun handleIntTriple(browserId: Int, data: String, block: (Int, Int, Int, Int) -> Unit) {
            val parts = data.split(",")
            if (parts.size >= 3) {
                val first = parts[0].toIntOrNull() ?: 0
                val second = parts[1].toIntOrNull() ?: 0
                val third = parts[2].toIntOrNull() ?: 0
                block(browserId, first, second, third)
            }
        }

        @JvmStatic
        external fun nativeSendEvent(browserId: Int, eventType: Int, data: String)

        @JvmStatic
        fun hideAllForPause() {
            try {
                CefManager.getInstance()?.hideAllForPause()
            } catch (_: Throwable) { }
        }

        @JvmStatic
        fun restoreAfterResume() {
            try {
                CefManager.getInstance()?.restoreAfterResume()
            } catch (_: Throwable) { }
        }

        @JvmStatic
        fun initialize() {
            Log.i(TAG, "Initializing CEF browser system")
            try {
                val activity = SAMP.getInstance()
                if (activity == null) {
                    Log.e(TAG, "SAMP activity is null, cannot initialize CEF")
                    return
                }
                activity.runOnUiThread {
                    CefManager.initialize(activity)
                    Log.i(TAG, "CefManager initialized")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize CefManager", t)
            }
        }

        @JvmStatic
        fun shutdown() {
            try {
                CefManager.getInstance()?.destroyAll()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to shut down CefManager", t)
            }
        }

        @JvmStatic
        fun getScreenSize(): IntArray {
            val activity = SAMP.getInstance()
            val metrics = activity?.resources?.displayMetrics
            val width = metrics?.widthPixels ?: 0
            val height = metrics?.heightPixels ?: 0
            Log.d(TAG, "Screen size ${width}x$height")
            return intArrayOf(width, height)
        }

        @JvmStatic
        fun captureWebView(browserId: Int): ByteArray? {
            return try {
                val cefView = CefManager.getInstance()?.getCefView(browserId)
                    ?: return null.also { Log.e(TAG, "captureWebView: browser $browserId missing") }

                val bitmap = cefView.captureWebView() ?: return null
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val buffer = ByteArray(width * height * 4)
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val base = i * 4
                    buffer[base] = (pixel shr 16 and 0xFF).toByte()
                    buffer[base + 1] = (pixel shr 8 and 0xFF).toByte()
                    buffer[base + 2] = (pixel and 0xFF).toByte()
                    buffer[base + 3] = (pixel shr 24 and 0xFF).toByte()
                }
                buffer
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to capture WebView for $browserId", t)
                null
            }
        }
    }
}
