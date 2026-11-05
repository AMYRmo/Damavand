package com.amyrmo.damavand.core

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.amyrmo.damavand.core.AppLog as Log
import com.amyrmo.damavand.domain.config.LocalPorts
import libv2ray.Libv2ray

/**
 * Browser Dialer (BPB port, cluster 5): the Xray core serves its dialer page
 * on [ADDRESS] and opens every WebSocket through it; a hidden WebView loads
 * that page, so the TLS handshake is Chromium's, not the core's. The app
 * process is outside the TUN, so the WebView connects directly.
 *
 * The core side is `Libv2ray.reconcileBrowserDialer` (present in zedcore r6's
 * libgojni, package transport/internet/browser_dialer): a non-empty address
 * enables/updates the dialer, "" disables it. Process-wide while on — only
 * WebSocket dials are intercepted, other transports are unaffected.
 */
object BrowserDialer {
    private const val TAG = "BrowserDialer"

    private val ADDRESS = "127.0.0.1:${LocalPorts.BROWSER_DIALER}"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    @Volatile
    private var active = false

    /** Reconciles the dialer with the setting at engine start. */
    fun apply(context: Context, enabled: Boolean) {
        if (enabled) start(context) else stop()
    }

    /** Turns the core's browser dialer on (it serves the page right away) and loads the page. */
    fun start(context: Context) {
        runCatching { Libv2ray.reconcileBrowserDialer(ADDRESS) }
            .onFailure { Log.w(TAG, "reconcileBrowserDialer failed: ${it.message}") }
        active = true
        mainHandler.post { load(context) }
    }

    /** Turns the browser dialer off and drops the WebView. */
    fun stop() {
        if (active) runCatching { Libv2ray.reconcileBrowserDialer("") }
            .onFailure { Log.w(TAG, "reconcileBrowserDialer(stop) failed: ${it.message}") }
        active = false
        mainHandler.post { drop() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun load(context: Context) {
        if (!active) return
        drop()
        webView = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i(TAG, "${message.messageLevel()}: ${message.message()}")
                    return true
                }
            }
            onResume()
            resumeTimers()
            loadUrl("http://$ADDRESS/")
        }
        Log.i(TAG, "Loading the dialer page")
    }

    private fun drop() {
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
    }
}
