package org.example.project

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun getCurrentEpochMillis(): Long = System.currentTimeMillis()

actual fun startPlatformWebSocket(
    url: String,
    onMessage: (String) -> Unit,
    onConnected: () -> Unit,
    onDisconnected: () -> Unit
): () -> Unit {
    var wsInstance: java.net.http.WebSocket? = null
    try {
        val client = java.net.http.HttpClient.newHttpClient()
        val listener = object : java.net.http.WebSocket.Listener {
            val buffer = StringBuilder()

            override fun onOpen(webSocket: java.net.http.WebSocket?) {
                onConnected()
                webSocket?.request(1)
            }

            override fun onText(webSocket: java.net.http.WebSocket?, data: CharSequence?, last: Boolean): java.util.concurrent.CompletionStage<*>? {
                if (data != null) {
                    buffer.append(data)
                }
                if (last) {
                    val fullMessage = buffer.toString()
                    buffer.setLength(0)
                    onMessage(fullMessage)
                }
                webSocket?.request(1)
                return null
            }

            override fun onClose(webSocket: java.net.http.WebSocket?, statusCode: Int, reason: String?): java.util.concurrent.CompletionStage<*>? {
                onDisconnected()
                return null
            }

            override fun onError(webSocket: java.net.http.WebSocket?, error: Throwable?) {
                onDisconnected()
            }
        }

        val future = client.newWebSocketBuilder()
            .buildAsync(java.net.URI.create(url), listener)

        future.thenAccept { ws ->
            wsInstance = ws
        }.exceptionally {
            onDisconnected()
            null
        }
    } catch (_: Exception) {
        onDisconnected()
    }

    return {
        try {
            wsInstance?.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "Client disconnecting")
        } catch (_: Exception) {}
    }
}

actual fun fetchPlatformHttp(
    url: String,
    method: String,
    body: String?
): String? {
    return try {
        val client = java.net.http.HttpClient.newHttpClient()
        val requestBuilder = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .timeout(java.time.Duration.ofSeconds(2))
        if (method.equals("POST", ignoreCase = true) && body != null) {
            requestBuilder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
        } else {
            requestBuilder.GET()
        }
        val response = client.send(requestBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() in 200..299) response.body() else null
    } catch (_: Exception) {
        null
    }
}

actual fun openPlatformBrowser(url: String) {
    try {
        var targetUrl = url
        if (url.contains("localhost:8080")) {
            val serverActive = try {
                val conn = java.net.URI.create(url).toURL().openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 700
                conn.readTimeout = 700
                conn.responseCode in 200..399
            } catch (_: Exception) {
                false
            }
            if (!serverActive) {
                val candidatePaths = listOf(
                    java.io.File("dist/nashik_rail_map.html"),
                    java.io.File("../dist/nashik_rail_map.html"),
                    java.io.File("c:/Projects/Eta_PredictionOf_Trains/dist/nashik_rail_map.html")
                )
                val existing = candidatePaths.find { it.exists() }
                if (existing != null) {
                    targetUrl = existing.toURI().toString()
                }
            }
        }

        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(targetUrl))
        } else {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") -> Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", targetUrl))
                os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", targetUrl))
                else -> Runtime.getRuntime().exec(arrayOf("xdg-open", targetUrl))
            }
        }
    } catch (_: Exception) {}
}