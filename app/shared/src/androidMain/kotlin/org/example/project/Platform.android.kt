package org.example.project

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getCurrentEpochMillis(): Long = System.currentTimeMillis()

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

actual fun openPlatformBrowser(url: String) {}