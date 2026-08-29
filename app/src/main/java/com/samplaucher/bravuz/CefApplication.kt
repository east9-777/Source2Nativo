package com.raiferoleplay.game

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.raiferoleplay.game.cef.CefInit

class CefApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CefInit.init(this)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
