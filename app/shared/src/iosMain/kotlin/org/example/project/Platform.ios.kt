package org.example.project

import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getCurrentEpochMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0).toLong()

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