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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

private const val TAG = "SonyFtpServer"
const val DEFAULT_FTP_PORT = 2121
const val DEFAULT_FTP_USER = "sony"
const val DEFAULT_FTP_PASS = "alpha"

data class FtpServerState(
    val isRunning: Boolean = false,
    val localIp: String = "192.168.43.1",
    val port: Int = DEFAULT_FTP_PORT,
    val username: String = DEFAULT_FTP_USER,
    val password: String = DEFAULT_FTP_PASS,
    val connectedClientsCount: Int = 0,
    val lastReceivedFileName: String? = null,
    val totalReceivedCount: Int = 0,
    val errorMessage: String? = null
)

class SonyFtpServer(
    private val context: Context,
    private val onPhotoReceived: (fileName: String, bytes: ByteArray) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    private val _state = MutableStateFlow(FtpServerState())
    val state: StateFlow<FtpServerState> = _state.asStateFlow()

    fun start(port: Int = DEFAULT_FTP_PORT) {
        if (_state.value.isRunning) return

        val detectedIp = getLocalWifiIpAddress()
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                _state.value = _state.value.copy(
                    isRunning = true,
                    localIp = detectedIp,
                    port = port,
                    errorMessage = null
                )
                onLog("📡 Servidor FTP iniciado en $detectedIp:$port (Usuario: $DEFAULT_FTP_USER)")

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error en el servidor FTP: ${e.message}", e)
                    _state.value = _state.value.copy(
                        isRunning = false,
                        errorMessage = e.message
                    )
                    onLog("⚠️ Error en servidor FTP: ${e.message}")
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
        _state.value = _state.value.copy(
            isRunning = false,
            connectedClientsCount = 0
        )
        onLog("📡 Servidor FTP detenido")
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(connectedClientsCount = _state.value.connectedClientsCount + 1)
        val clientIp = clientSocket.inetAddress?.hostAddress ?: "Desconocido"
        onLog("📷 Cámara Sony conectada por WiFi (FTP) desde $clientIp")

        var pasvServerSocket: ServerSocket? = null

        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(OutputStreamWriter(clientSocket.getOutputStream(), Charsets.UTF_8), true)

            // Saludo inicial FTP
            writer.print("220 Sony Alpha Tether FTP Ready\r\n")
            writer.flush()

            while (isActive && !clientSocket.isClosed) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val parts = trimmed.split(" ", limit = 2)
                val command = parts[0].uppercase()
                val argument = if (parts.size > 1) parts[1] else ""

                when (command) {
                    "USER" -> {
                        writer.print("331 User name okay, need password.\r\n")
                        writer.flush()
                    }
                    "PASS" -> {
                        writer.print("230 User logged in, proceed.\r\n")
                        writer.flush()
                    }
                    "SYST" -> {
                        writer.print("215 UNIX Type: L8\r\n")
                        writer.flush()
                    }
                    "FEAT" -> {
                        writer.print("211-Features:\r\n PASV\r\n UTF8\r\n SIZE\r\n211 End\r\n")
                        writer.flush()
                    }
                    "OPTS" -> {
                        writer.print("200 Command okay.\r\n")
                        writer.flush()
                    }
                    "PWD" -> {
                        writer.print("257 \"/\" is current directory.\r\n")
                        writer.flush()
                    }
                    "TYPE" -> {
                        writer.print("200 Type set to $argument.\r\n")
                        writer.flush()
                    }
                    "CWD" -> {
                        writer.print("250 Directory changed to /.\r\n")
                        writer.flush()
                    }
                    "MKD" -> {
                        writer.print("257 Directory created.\r\n")
                        writer.flush()
                    }
                    "NOOP" -> {
                        writer.print("200 OK.\r\n")
                        writer.flush()
                    }
                    "PASV" -> {
                        try {
                            pasvServerSocket?.close()
                        } catch (_: Exception) {}

                        val pasv = ServerSocket(0)
                        pasvServerSocket = pasv
                        pasv.soTimeout = 15000 // 15s timeout para recibir datos

                        val localIp = clientSocket.localAddress.hostAddress ?: getLocalWifiIpAddress()
                        val ipParts = localIp.replace(".", ",").split(",")
                        val port = pasv.localPort
                        val p1 = port / 256
                        val p2 = port % 256

                        val pasvResponse = "227 Entering Passive Mode (${ipParts[0]},${ipParts[1]},${ipParts[2]},${ipParts[3]},$p1,$p2)\r\n"
                        writer.print(pasvResponse)
                        writer.flush()
                    }
                    "STOR" -> {
                        val fileName = argument.substringAfterLast("/").substringAfterLast("\\").ifBlank { "DSC_WIFI_${System.currentTimeMillis()}.JPG" }
                        writer.print("150 Opening BINARY mode data connection for $fileName.\r\n")
                        writer.flush()

                        val pasv = pasvServerSocket
                        if (pasv != null) {
                            try {
                                val dataSocket = pasv.accept()
                                dataSocket.use { ds ->
                                    val dataIn = ds.getInputStream()
                                    val baos = ByteArrayOutputStream(4 * 1024 * 1024)
                                    val buffer = ByteArray(32 * 1024)
                                    var read: Int
                                    while (dataIn.read(buffer).also { read = it } != -1) {
                                        baos.write(buffer, 0, read)
                                    }
                                    baos.flush()

                                    val photoBytes = baos.toByteArray()
                                    val sizeMb = String.format("%.2f", photoBytes.size / (1024.0 * 1024.0))
                                    onLog("📥 Foto recibida por WiFi (FTP): $fileName ($sizeMb MB)")

                                    _state.value = _state.value.copy(
                                        lastReceivedFileName = fileName,
                                        totalReceivedCount = _state.value.totalReceivedCount + 1
                                    )

                                    // Notificar al motor de procesamiento
                                    onPhotoReceived(fileName, photoBytes)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error recibiendo datos en STOR: ${e.message}", e)
                                onLog("⚠️ Error recibiendo datos de foto: ${e.message}")
                            } finally {
                                try { pasv.close() } catch (_: Exception) {}
                                pasvServerSocket = null
                            }
                        }

                        writer.print("226 Transfer complete.\r\n")
                        writer.flush()
                    }
                    "SIZE" -> {
                        writer.print("550 File not found.\r\n")
                        writer.flush()
                    }
                    "QUIT" -> {
                        writer.print("221 Goodbye.\r\n")
                        writer.flush()
                        break
                    }
                    else -> {
                        // Responder 200 o 502
                        writer.print("200 OK\r\n")
                        writer.flush()
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.w(TAG, "Cliente desconectado: ${e.message}")
            }
        } finally {
            try { pasvServerSocket?.close() } catch (_: Exception) {}
            try { clientSocket.close() } catch (_: Exception) {}
            _state.value = _state.value.copy(
                connectedClientsCount = (_state.value.connectedClientsCount - 1).coerceAtLeast(0)
            )
        }
    }

    companion object {
        fun getLocalWifiIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            // Preferir IPs de Hotspot o LAN (192.168.x.x o 10.x.x.x o 172.x)
                            if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
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
