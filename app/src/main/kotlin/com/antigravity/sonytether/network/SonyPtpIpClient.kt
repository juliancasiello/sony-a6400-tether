package com.antigravity.sonytether.network

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val TAG = "SonyPtpIpClient"
const val PTP_IP_PORT = 15740

// PTP/IP Packet Types
private const val PTPIP_INIT_COMMAND_REQ = 1
private const val PTPIP_INIT_COMMAND_ACK = 2
private const val PTPIP_INIT_EVENT_REQ = 3
private const val PTPIP_INIT_EVENT_ACK = 4
private const val PTPIP_CMD_REQ = 6
private const val PTPIP_CMD_RESP = 7
private const val PTPIP_EVENT = 8
private const val PTPIP_START_DATA = 9
private const val PTPIP_DATA = 10
private const val PTPIP_END_DATA = 12

// PTP Standard & Sony Opcodes
private const val PTP_OC_OPEN_SESSION = 0x1002
private const val PTP_OC_CLOSE_SESSION = 0x1003
private const val PTP_OC_GET_OBJECT_INFO = 0x1008
private const val PTP_OC_GET_OBJECT = 0x1009
private const val PTP_OC_SONY_SDIO_CONNECT = 0x9201
private const val PTP_OC_SONY_GET_EXT_DEVICE_INFO = 0x9202
private const val PTP_OC_SONY_GET_ALL_DEV_PROP_DATA = 0x9209

// Event codes
private const val PTP_EC_OBJECT_ADDED = 0x4002
private const val PTP_EC_SONY_OBJECT_ADDED = 0xC201
private const val SONY_RAM_OBJECT_HANDLE = 0xFFFFC001.toInt()

data class PtpIpClientState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val cameraIp: String = "",
    val cameraName: String? = null,
    val errorMessage: String? = null,
    val photosReceivedCount: Int = 0
)

class SonyPtpIpClient(
    private val onPhotoReceived: (fileName: String, bytes: ByteArray) -> Unit,
    private val onLog: (String) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionJob: Job? = null

    private var cmdSocket: Socket? = null
    private var eventSocket: Socket? = null
    private var transactionId = 1

    private val _state = MutableStateFlow(PtpIpClientState())
    val state: StateFlow<PtpIpClientState> = _state.asStateFlow()

    fun connect(cameraIp: String) {
        if (_state.value.isConnected || _state.value.isConnecting) return

        sessionJob = scope.launch {
            _state.value = _state.value.copy(
                isConnecting = true,
                cameraIp = cameraIp,
                errorMessage = null
            )
            onLog("📡 Conectando a cámara Sony por WiFi PTP/IP ($cameraIp:15740)...")

            try {
                // 1. Establecer conexión de comandos
                val cmd = Socket()
                cmd.connect(InetSocketAddress(cameraIp, PTP_IP_PORT), 8000)
                cmdSocket = cmd
                val cmdIn = cmd.getInputStream()
                val cmdOut = cmd.getOutputStream()

                // 2. Enviar Init_Command_Request
                val guid = UUID.randomUUID()
                val guidBytes = ByteBuffer.allocate(16).apply {
                    putLong(guid.mostSignificantBits)
                    putLong(guid.leastSignificantBits)
                }.array()
                val friendlyName = "SonyAlphaTether\u0000".toByteArray(Charsets.UTF_16LE)
                val initReqLen = 4 + 4 + 16 + friendlyName.size + 4
                val initReqBuf = ByteBuffer.allocate(initReqLen).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(initReqLen)
                    putInt(PTPIP_INIT_COMMAND_REQ)
                    put(guidBytes)
                    put(friendlyName)
                    putInt(0x00010000) // Versión 1.0
                }
                cmdOut.write(initReqBuf.array())
                cmdOut.flush()

                // 3. Leer Init_Command_Ack
                val ackHeader = readExact(cmdIn, 8) ?: throw Exception("Respuesta Init_Command vacía")
                val ackLen = ByteBuffer.wrap(ackHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                val ackType = ByteBuffer.wrap(ackHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
                if (ackType != PTPIP_INIT_COMMAND_ACK) {
                    throw Exception("Respuesta inesperada de Init_Command: tipo $ackType")
                }
                val ackPayload = readExact(cmdIn, ackLen - 8) ?: throw Exception("Payload de Ack incompleto")
                val ackBuf = ByteBuffer.wrap(ackPayload).order(ByteOrder.LITTLE_ENDIAN)
                val connectionNumber = ackBuf.int
                // Leer nombre amigable de la cámara
                var camName: String? = null
                try {
                    val remainingBytes = ackPayload.copyOfRange(20, ackPayload.size)
                    camName = String(remainingBytes, Charsets.UTF_16LE).trim('\u0000', ' ')
                } catch (_: Exception) {}

                onLog("⚡ Handshake PTP/IP exitoso con ${camName ?: "Sony Alpha"} (Conexión #$connectionNumber)")

                // 4. Establecer conexión de Eventos
                val evt = Socket()
                evt.connect(InetSocketAddress(cameraIp, PTP_IP_PORT), 8000)
                eventSocket = evt
                val evtIn = evt.getInputStream()
                val evtOut = evt.getOutputStream()

                // Enviar Init_Event_Request
                val evtReqBuf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(12)
                    putInt(PTPIP_INIT_EVENT_REQ)
                    putInt(connectionNumber)
                }
                evtOut.write(evtReqBuf.array())
                evtOut.flush()

                // Leer Init_Event_Ack
                val evtAckHeader = readExact(evtIn, 8) ?: throw Exception("Respuesta Init_Event vacía")
                val evtAckType = ByteBuffer.wrap(evtAckHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(4)
                if (evtAckType != PTPIP_INIT_EVENT_ACK) {
                    throw Exception("Respuesta inesperada de Init_Event: tipo $evtAckType")
                }
                onLog("⚡ Canal de eventos PTP/IP activado correctamente")

                // 5. Iniciar Sesión Sony PTP
                transactionId = 1
                executePtpOperation(cmdIn, cmdOut, PTP_OC_OPEN_SESSION, intArrayOf(1))

                // SDIO Connect Fase 1
                executePtpOperation(cmdIn, cmdOut, PTP_OC_SONY_SDIO_CONNECT, intArrayOf(1, 0, 0))
                // SDIO Connect Fase 2
                executePtpOperation(cmdIn, cmdOut, PTP_OC_SONY_SDIO_CONNECT, intArrayOf(2, 0, 0))
                // Get Ext Device Info
                executePtpOperation(cmdIn, cmdOut, PTP_OC_SONY_GET_EXT_DEVICE_INFO, intArrayOf(0x012C))
                // SDIO Connect Fase 3
                executePtpOperation(cmdIn, cmdOut, PTP_OC_SONY_SDIO_CONNECT, intArrayOf(3, 0, 0))
                // GetAllDevPropData
                executePtpOperation(cmdIn, cmdOut, PTP_OC_SONY_GET_ALL_DEV_PROP_DATA, intArrayOf())

                _state.value = _state.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    cameraName = camName ?: "Sony Alpha (PTP/IP)",
                    errorMessage = null
                )
                onLog("📷 Sesión de disparo remoto inalámbrico (PTP/IP) LISTA. Dispará libremente.")

                // 6. Bucle de eventos en segundo plano
                while (isActive && !evt.isClosed) {
                    val evHeader = readExact(evtIn, 8) ?: break
                    val evLen = ByteBuffer.wrap(evHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                    val evType = ByteBuffer.wrap(evHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(4)

                    if (evLen > 8) {
                        val evPayload = readExact(evtIn, evLen - 8) ?: break
                        if (evType == PTPIP_EVENT) {
                            val evBuf = ByteBuffer.wrap(evPayload).order(ByteOrder.LITTLE_ENDIAN)
                            val eventCode = evBuf.short.toInt() and 0xFFFF
                            val transId = evBuf.int
                            val handle = if (evPayload.size >= 10) evBuf.int else 0

                            if (eventCode == PTP_EC_SONY_OBJECT_ADDED || eventCode == PTP_EC_OBJECT_ADDED) {
                                val targetHandle = if (handle != 0) handle else SONY_RAM_OBJECT_HANDLE
                                onLog("📸 ¡Disparo detectado por WiFi PTP/IP! (Handle 0x${targetHandle.toString(16)})")
                                downloadPtpObject(cmdIn, cmdOut, targetHandle)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error en sesión PTP/IP: ${e.message}", e)
                    _state.value = _state.value.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = e.message
                    )
                    onLog("⚠️ Error de conexión WiFi PTP/IP: ${e.message}")
                }
            } finally {
                disconnectInternal()
            }
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        disconnectInternal()
    }

    private fun disconnectInternal() {
        try {
            cmdSocket?.close()
        } catch (_: Exception) {}
        try {
            eventSocket?.close()
        } catch (_: Exception) {}
        cmdSocket = null
        eventSocket = null
        _state.value = _state.value.copy(
            isConnected = false,
            isConnecting = false
        )
        onLog("📡 Desconectado de sesión WiFi PTP/IP")
    }

    private fun executePtpOperation(
        input: InputStream,
        output: OutputStream,
        opCode: Int,
        params: IntArray
    ): Pair<Int, ByteArray?> {
        val tid = transactionId++
        val paramCount = params.size
        val packetLen = 4 + 4 + 4 + 2 + 4 + (paramCount * 4)

        val buf = ByteBuffer.allocate(packetLen).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(packetLen)
            putInt(PTPIP_CMD_REQ)
            putInt(3) // Data phase: 3 = No data (or 1/2)
            putShort(opCode.toShort())
            putInt(tid)
            for (p in params) {
                putInt(p)
            }
        }
        output.write(buf.array())
        output.flush()

        // Leer respuesta de la cámara
        val respHeader = readExact(input, 8) ?: return Pair(-1, null)
        val respLen = ByteBuffer.wrap(respHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        val respType = ByteBuffer.wrap(respHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(4)

        val payload = if (respLen > 8) readExact(input, respLen - 8) else null
        var respCode = 0
        if (respType == PTPIP_CMD_RESP && payload != null && payload.size >= 2) {
            respCode = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        }
        return Pair(respCode, payload)
    }

    private fun downloadPtpObject(
        input: InputStream,
        output: OutputStream,
        handle: Int
    ) {
        try {
            val tid = transactionId++
            // Solicitar objeto completo
            val buf = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(26)
                putInt(PTPIP_CMD_REQ)
                putInt(2) // Data phase: 2 = Data In
                putShort(PTP_OC_GET_OBJECT.toShort())
                putInt(tid)
                putInt(handle)
            }
            output.write(buf.array())
            output.flush()

            val baos = ByteArrayOutputStream(4 * 1024 * 1024)

            // Leer paquetes de datos hasta End_Data_Packet
            var readingData = true
            while (readingData) {
                val header = readExact(input, 8) ?: break
                val len = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
                val type = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4)

                val dataSize = len - 8
                if (dataSize > 0) {
                    val chunk = readExact(input, dataSize) ?: break
                    when (type) {
                        PTPIP_START_DATA -> {
                            // Saltear transactionId inicial (4 bytes)
                            if (chunk.size > 12) {
                                baos.write(chunk, 12, chunk.size - 12)
                            }
                        }
                        PTPIP_DATA -> {
                            // Saltear transactionId inicial (4 bytes)
                            if (chunk.size > 4) {
                                baos.write(chunk, 4, chunk.size - 4)
                            }
                        }
                        PTPIP_END_DATA -> {
                            if (chunk.size > 4) {
                                baos.write(chunk, 4, chunk.size - 4)
                            }
                            readingData = false
                        }
                        PTPIP_CMD_RESP -> {
                            readingData = false
                        }
                    }
                } else if (type == PTPIP_CMD_RESP) {
                    readingData = false
                }
            }

            val photoBytes = baos.toByteArray()
            if (photoBytes.isNotEmpty()) {
                val fileName = "DSC_PTPIP_${System.currentTimeMillis()}.JPG"
                val sizeMb = String.format("%.2f", photoBytes.size / (1024.0 * 1024.0))
                onLog("📥 Foto PTP/IP descargada: $fileName ($sizeMb MB)")

                _state.value = _state.value.copy(
                    photosReceivedCount = _state.value.photosReceivedCount + 1
                )
                onPhotoReceived(fileName, photoBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando objeto PTP/IP: ${e.message}", e)
            onLog("⚠️ Error descargando foto PTP/IP: ${e.message}")
        }
    }

    private fun readExact(stream: InputStream, count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var totalRead = 0
        while (totalRead < count) {
            val read = stream.read(buffer, totalRead, count - totalRead)
            if (read == -1) {
                return if (totalRead == 0) null else buffer.copyOf(totalRead)
            }
            totalRead += read
        }
        return buffer
    }

    companion object {
        suspend fun scanSubnetForCamera(baseIp: String): String? = withContext(Dispatchers.IO) {
            val prefix = baseIp.substringBeforeLast(".")
            for (i in 2..254) {
                val targetIp = "$prefix.$i"
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, PTP_IP_PORT), 150)
                        return@withContext targetIp
                    }
                } catch (_: Exception) {}
            }
            null
        }
    }
}
