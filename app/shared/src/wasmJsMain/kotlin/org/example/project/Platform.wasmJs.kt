package org.example.project

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@OptIn(kotlin.js.ExperimentalJsExport::class)
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

actual fun openPlatformBrowser(url: String) {}