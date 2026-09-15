package org.example.project

import web.navigator.navigator

class JsPlatform: Platform {
    private val userAgent = navigator.userAgent
    private val browserList = listOf("Chrome", "Firefox", "Safari", "Edge")

    override val name: String = userAgent.findAnyOf(browserList, ignoreCase = true)
            ?.let { (startIndex) -> userAgent.substring(startIndex).substringBefore(" ") }
            ?: "Unknown"
}

actual fun getPlatform(): Platform = JsPlatform()

actual fun getCurrentEpochMillis(): Long = js("Date.now()").unsafeCast<Double>().toLong()

actual fun startPlatformWebSocket(
    url: String,
    onMessage: (String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit
): () -> Unit = {}

actual fun fetchPlatformHttp(
    url: String,
    method: String,
    body: String?
): String? = null

actual fun openPlatformBrowser(url: String) {
    try {
        js("window.open(url, '_blank')")
    } catch (_: Exception) {}
}