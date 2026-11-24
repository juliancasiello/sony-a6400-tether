package com.antigravity.sonytether.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "UdpDiscoveryResponder"
const val DEFAULT_UDP_DISCOVERY_PORT = 8081

class UdpDiscoveryResponder(
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socketJob: Job? = null
    private var datagramSocket: DatagramSocket? = null

    fun start(port: Int = DEFAULT_UDP_DISCOVERY_PORT, httpPort: Int = DEFAULT_HTTP_PUSH_PORT) {
        if (socketJob != null) return

        socketJob = scope.launch {
            try {
                val socket = DatagramSocket(port)
                datagramSocket = socket
                onLog("📡 Responder de descubrimiento UDP activo en puerto $port")

                val buffer = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (message.contains("A6400_DISCOVER") || message.contains("SONY_DISCOVER")) {
                        val senderIp = packet.address.hostAddress
                        val senderPort = packet.port
                        val phoneIp = SonyHttpPushServer.getHotspotOrWifiIp()

                        val responseText = "A6400_SERVER_IP:$phoneIp:$httpPort\n"
                        val responseBytes = responseText.toByteArray(Charsets.UTF_8)
                        val responsePacket = DatagramPacket(
                            responseBytes,
                            responseBytes.size,
                            packet.address,
                            senderPort
                        )

                        socket.send(responsePacket)
                        onLog("⚡ Cámara a6400 detectada desde $senderIp. IP del teléfono respondida: $phoneIp:$httpPort")
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error en UdpDiscoveryResponder: ${e.message}", e)
                }
            } finally {
                stopInternal()
            }
        }
    }

    fun stop() {
        socketJob?.cancel()
        socketJob = null
        stopInternal()
    }

    private fun stopInternal() {
        try {
            datagramSocket?.close()
        } catch (_: Exception) {}
        datagramSocket = null
    }
}
