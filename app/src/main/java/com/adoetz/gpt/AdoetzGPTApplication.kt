package com.adoetz.gpt

import android.app.Application
import android.webkit.WebView

/**
 * Application class for AdoetzGPT Enhanced
 * Handles global initialization
 */
class AdoetzGPTApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Enable WebView debugging for debug builds
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        // Initialize any global components here
    }
}
