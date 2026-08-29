package com.raiferoleplay.game.cef

import android.app.Application
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Handles early WebView/CEF configuration. Must be invoked from Application.onCreate()
 * before the first WebView instance is created.
 */
object CefInit {
    private const val TAG = "CefInit"
    @Volatile
    private var isInitialized = false

    fun init(application: Application) {
        if (isInitialized) {
            Log.w(TAG, "CEF already initialized")
            return
        }

        try {
            WebView.enableSlowWholeDocumentDraw()
            Log.i(TAG, "Slow whole document draw enabled for WebView")

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(WebView(application), true)

            isInitialized = true
            Log.i(TAG, "CEF initialization finished")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize CEF", t)
            throw RuntimeException("Failed to initialize CEF subsystem", t)
        }
    }

    fun isInitialized(): Boolean = isInitialized
}
