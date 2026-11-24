package com.antigravity.sonytether.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SonyHttpPushServer"
const val DEFAULT_HTTP_PUSH_PORT = 8080

data class HttpPushServerState(
    val isRunning: Boolean = false,
    val localIp: String = "192.168.43.1",
    val port: Int = DEFAULT_HTTP_PUSH_PORT,
    val photosReceivedCount: Int = 0,
    val lastReceivedFileName: String? = null,
    val lastReceivedBytes: Long = 0,
    val errorMessage: String? = null
)

class SonyHttpPushServer(
    private val context: Context,
    private val onPhotoReceived: (file: File, fileName: String) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    // Cache en memoria de nombres procesados con éxito para garantizar idempotencia
    private val processedFiles = ConcurrentHashMap.newKeySet<String>()

    private val _state = MutableStateFlow(HttpPushServerState())
    val state: StateFlow<HttpPushServerState> = _state.asStateFlow()

    fun start(port: Int = DEFAULT_HTTP_PUSH_PORT) {
        if (_state.value.isRunning) return

        val detectedIp = getHotspotOrWifiIp()
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _state.value = _state.value.copy(
                    isRunning = true,
                    localIp = detectedIp,
                    port = port,
                    errorMessage = null
                )
                onLog("📡 Servidor HTTP Push activo en http://$detectedIp:$port/upload")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error en SonyHttpPushServer: ${e.message}", e)
                    _state.value = _state.value.copy(
                        isRunning = false,
                        errorMessage = e.message
                    )
                    onLog("⚠️ Error en servidor HTTP Push: ${e.message}")
                }
            } finally {
                stopInternal()
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        _state.value = _state.value.copy(isRunning = false)
        onLog("📡 Servidor HTTP Push detenido")
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "Desconocido"
        try {
            clientSocket.soTimeout = 25000 // 25s timeout para transferencias de 15 MB
            val rawIn = clientSocket.getInputStream()
            val rawOut = clientSocket.getOutputStream()

            val inStream = BufferedInputStream(rawIn, 64 * 1024)
            val outStream = BufferedOutputStream(rawOut)

            // 1. Leer línea de petición HTTP (ej: POST /upload?filename=DSC01234.JPG HTTP/1.1)
            val requestLine = readLine(inStream)
            if (requestLine.isNullOrBlank()) {
                sendResponse(outStream, 400, "Bad Request: Petición vacía")
                return@withContext
            }

            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) {
                sendResponse(outStream, 400, "Bad Request: Formato inválido")
                return@withContext
            }

            val method = parts[0].uppercase()
            val uri = parts[1]

            if (method != "POST") {
                sendResponse(outStream, 405, "Method Not Allowed")
                return@withContext
            }

            // 2. Parsear cabeceras HTTP
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(inStream) ?: break
                if (line.isEmpty()) break // Fin de cabeceras (\r\n)
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
            var fileName = headers["x-filename"]

            // Si no vino en cabecera, buscar en query string (?filename=DSC01234.JPG)
            if (fileName.isNullOrBlank() && uri.contains("filename=")) {
                fileName = uri.substringAfter("filename=").substringBefore("&").substringBefore(" ")
            }

            if (fileName.isNullOrBlank()) {
                fileName = "DSC_${System.currentTimeMillis()}.JPG"
            }

            // Sanitizar nombre de archivo (solo letras, números, puntos y guiones)
            val cleanFileName = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "_")

            // 3. IDEMPOTENCIA: Si ya fue procesado con éxito, confirmar sin reprocesar
            if (processedFiles.contains(cleanFileName)) {
                onLog("🔁 Archivo ya recibido previamente ($cleanFileName). Confirmando idempotencia.")
                skipBytes(inStream, contentLength)
                sendResponse(outStream, 200, "OK (Already Received)")
                return@withContext
            }

            if (contentLength <= 0) {
                sendResponse(outStream, 411, "Length Required")
                return@withContext
            }

            // 4. ESCRITURA ATÓMICA Y STREAMING DIRECTO A DISCO (Sin cargar 15 MB en RAM)
            val incomingDir = File(context.cacheDir, "incoming_http").apply { mkdirs() }
            val tempFile = File(incomingDir, "${cleanFileName}_${System.currentTimeMillis()}.tmp")
            val targetFile = File(incomingDir, cleanFileName)

            var bytesReadTotal = 0L
            val buffer = ByteArray(64 * 1024) // Búfer de streaming de 64 KB

            try {
                FileOutputStream(tempFile).use { fileOut ->
                    while (bytesReadTotal < contentLength) {
                        val needed = (contentLength - bytesReadTotal).coerceAtMost(buffer.size.toLong()).toInt()
                        val read = inStream.read(buffer, 0, needed)
                        if (read == -1) break
                        fileOut.write(buffer, 0, read)
                        bytesReadTotal += read
                    }
                    fileOut.flush()
                }

                // Verificar que se haya transferido la totalidad indicada en Content-Length
                if (bytesReadTotal < contentLength) {
                    tempFile.delete()
                    sendResponse(outStream, 400, "Incomplete Transfer")
                    onLog("⚠️ Transferencia incompleta de $cleanFileName ($bytesReadTotal de $contentLength bytes)")
                    return@withContext
                }

                // Renombrado atómico de .tmp a .jpg
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                val finalFile = if (renamed) targetFile else tempFile

                // Marcar como procesado para idempotencia
                processedFiles.add(cleanFileName)

                val sizeMb = String.format("%.2f", bytesReadTotal / (1024.0 * 1024.0))
                onLog("📥 Foto recibida vía Wi-Fi Hotspot: $cleanFileName ($sizeMb MB desde $clientIp)")

                _state.value = _state.value.copy(
                    photosReceivedCount = _state.value.photosReceivedCount + 1,
                    lastReceivedFileName = cleanFileName,
                    lastReceivedBytes = bytesReadTotal
                )

                // Responder 200 OK a la cámara Sony
                sendResponse(outStream, 200, "OK")

                // Enviar al pipeline de procesamiento y subida
                onPhotoReceived(finalFile, cleanFileName)

            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }

        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.w(TAG, "Error procesando petición HTTP de $clientIp: ${e.message}")
            }
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                break
            }
            if (b != '\r'.code) {
                sb.append(b.toChar())
            }
        }
        return sb.toString()
    }

    private fun skipBytes(input: InputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(16 * 1024)
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            remaining -= read
        }
    }

    private fun sendResponse(output: OutputStream, statusCode: Int, message: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            405 -> "Method Not Allowed"
            411 -> "Length Required"
            else -> "Response"
        }
        val bodyBytes = message.toByteArray(Charsets.UTF_8)
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: ${bodyBytes.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(response.toByteArray(Charsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    companion object {
        fun getHotspotOrWifiIp(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            // Priorizar interfaces típicas de Hotspot Android (192.168.43.x) o LAN privada
                            if (host.startsWith("192.168.43.") || host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                                return host
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return "192.168.43.1"
        }
    }
}
