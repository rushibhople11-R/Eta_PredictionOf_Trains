package org.example.project

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getCurrentEpochMillis(): Long

expect fun startPlatformWebSocket(
    url: String,
    onMessage: (String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit
): () -> Unit

expect fun fetchPlatformHttp(
    url: String,
    method: String = "GET",
    body: String? = null
): String?

expect fun openPlatformBrowser(url: String)