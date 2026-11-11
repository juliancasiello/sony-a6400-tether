package com.antigravity.sonytether.network

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.antigravity.sonytether.editor.PresetConfig
import com.antigravity.sonytether.model.TetheredPhoto
import com.antigravity.sonytether.storage.PhotoStorageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

private const val TAG = "SonyUsbMtpHelper"
private const val ACTION_USB_PERMISSION = "com.antigravity.sonytether.USB_PERMISSION"
private const val SONY_VENDOR_ID = 0x054C // 1356 (Sony Corp)

// PTP Container Types
private const val PTP_CNT_COMMAND: Short = 1
private const val PTP_CNT_DATA: Short = 2
private const val PTP_CNT_RESPONSE: Short = 3
private const val PTP_CNT_EVENT: Short = 4

// Standard & Sony PTP Opcodes
private const val PTP_OC_GET_DEVICE_INFO = 0x1001
private const val PTP_OC_OPEN_SESSION = 0x1002
private const val PTP_OC_CLOSE_SESSION = 0x1003
private const val PTP_OC_GET_STORAGE_IDS = 0x1004
private const val PTP_OC_GET_OBJECT_HANDLES = 0x1007
private const val PTP_OC_GET_OBJECT_INFO = 0x1008
private const val PTP_OC_GET_OBJECT = 0x1009

private const val PTP_OC_SONY_SDIO_CONNECT = 0x9201
private const val PTP_OC_SONY_GET_EXT_DEVICE_INFO = 0x9202
private const val PTP_OC_SONY_SET_CONTROL_DEVICE_B = 0x9207
private const val PTP_OC_SONY_GET_ALL_DEV_PROP_DATA = 0x9209

// PTP Formats, Response & Event Codes
private const val PTP_OFC_EXIF_JPEG = 0x3801
private const val PTP_RC_OK = 0x2001
private const val PTP_RC_SESSION_ALREADY_OPEN = 0x201E
private const val PTP_EC_OBJECT_ADDED = 0x4002
private const val PTP_EC_SONY_OBJECT_ADDED = 0xC201
private const val PTP_EC_SONY_PROPERTY_CHANGED = 0xC203

// Sony Virtual RAM Handle for Live PC Remote Captures
private const val SONY_RAM_OBJECT_HANDLE = 0xFFFFC001.toInt()

data class PtpObjectInfo(
    val objectFormat: Int,
    val compressedSize: Long,
    val fileName: String
)

class SonyUsbMtpHelper(
    private val context: Context,
    private val onCameraUsbReady: (UsbDevice) -> Unit
) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _isUsbCameraConnected = MutableStateFlow(false)
    val isUsbCameraConnected: StateFlow<Boolean> = _isUsbCameraConnected.asStateFlow()

    private val _usbModeDescription = MutableStateFlow("Esperando conexión por cable USB")
    val usbModeDescription: StateFlow<String> = _usbModeDescription.asStateFlow()

    @Volatile
    private var lastPermissionRequestMs = 0L

    @Volatile
    private var isUsbSessionActive = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                when (intent.action) {
                    ACTION_USB_PERMISSION -> {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        val targetDevice = device ?: findSonyCameraDevice()
                        if (granted && targetDevice != null) {
                            Log.i(TAG, "Permiso USB concedido para ${targetDevice.productName}")
                            _isUsbCameraConnected.value = true
                            onCameraUsbReady(targetDevice)
                        } else if (targetDevice != null && hasPermission(targetDevice)) {
                            _isUsbCameraConnected.value = true
                            onCameraUsbReady(targetDevice)
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        checkConnectedCamera()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        isUsbSessionActive = false
                        val cam = findSonyCameraDevice()
                        val connected = (cam != null)
                        _isUsbCameraConnected.value = connected
                        if (!connected) {
                            _usbModeDescription.value = "Cable USB desconectado"
                            Log.w(TAG, "Cable USB desconectado (ACTION_USB_DEVICE_DETACHED)")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error en usbReceiver: ${t.message}", t)
            }
        }
    }

    fun startMonitoring() {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_USB_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
            checkConnectedCamera()
        } catch (t: Throwable) {
            Log.e(TAG, "Error registrando receptor USB: ${t.message}", t)
        }
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Throwable) {}
    }

    fun findSonyCameraDevice(): UsbDevice? {
        return try {
            for (device in usbManager.deviceList.values) {
                if (device.vendorId == SONY_VENDOR_ID) {
                    return device
                }
                for (i in 0 until device.interfaceCount) {
                    val intf = device.getInterface(i)
                    if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                        return device
                    }
                }
            }
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Error buscando cámara USB: ${t.message}")
            null
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return try {
            usbManager.hasPermission(device)
        } catch (_: Throwable) {
            false
        }
    }

    fun checkConnectedCamera(): UsbDevice? {
        return try {
            val device = findSonyCameraDevice()
            if (device == null) {
                _isUsbCameraConnected.value = false
                _usbModeDescription.value = "Esperando conexión por cable USB"
                return null
            }

            val permitted = hasPermission(device)
            _isUsbCameraConnected.value = permitted
            if (permitted) {
                onCameraUsbReady(device)
            } else {
                val now = System.currentTimeMillis()
                if (now - lastPermissionRequestMs > 5000L) {
                    lastPermissionRequestMs = now
                    requestUsbPermissionSafely(device)
                }
            }
            device
        } catch (t: Throwable) {
            Log.e(TAG, "Error en checkConnectedCamera: ${t.message}", t)
            _isUsbCameraConnected.value = false
            null
        }
    }

    private fun requestUsbPermissionSafely(device: UsbDevice) {
        try {
            val explicitIntent = Intent(ACTION_USB_PERMISSION).apply {
                setPackage(context.packageName)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val permissionIntent = PendingIntent.getBroadcast(context, 0, explicitIntent, flags)
            usbManager.requestPermission(device, permissionIntent)
        } catch (t: Throwable) {
            Log.w(TAG, "Fallback a FLAG_IMMUTABLE para permiso USB: ${t.message}")
            try {
                val fallbackIntent = Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = PendingIntent.getBroadcast(context, 0, fallbackIntent, flags)
                usbManager.requestPermission(device, permissionIntent)
            } catch (e2: Throwable) {
                Log.e(TAG, "No se pudo solicitar permiso USB: ${e2.message}", e2)
            }
        }
    }

    suspend fun runUsbCameraSession(
        device: UsbDevice,
        downloadedKeys: MutableSet<String>,
        eventNameProvider: () -> String,
        presetConfigProvider: () -> PresetConfig,
        recentOriginalUris: MutableList<android.net.Uri>,
        storageManager: PhotoStorageManager,
        onStatusUpdate: (String) -> Unit,
        onProgress: (fileName: String, progress: Float) -> Unit,
        onPhotoSaved: (original: TetheredPhoto, edited: TetheredPhoto?, key: String) -> Unit,
        onEditedPhotoReady: ((fileName: String, bytes: ByteArray) -> Unit)? = null,
        onOriginalCaptured: ((savedOriginal: com.antigravity.sonytether.storage.SavedOriginalResult, eventName: String, key: String) -> Unit)? = null,
        onLog: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!hasPermission(device)) {
            checkConnectedCamera()
            onStatusUpdate("⚠️ Aceptá el permiso USB en pantalla para conectar con la cámara.")
            return@withContext
        }

        var ptpInterface: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE) {
                ptpInterface = intf
                break
            }
        }
        if (ptpInterface == null && device.interfaceCount > 0) {
            ptpInterface = device.getInterface(0)
        }

        val intf = ptpInterface ?: return@withContext
        var epOut: UsbEndpoint? = null
        var epIn: UsbEndpoint? = null
        var epInt: UsbEndpoint? = null

        for (i in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
                else if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
            } else if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == UsbConstants.USB_DIR_IN) {
                epInt = ep
            }
        }

        val bulkOut = epOut ?: return@withContext
        val bulkIn = epIn ?: return@withContext

        val conn = try {
            usbManager.openDevice(device)
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo abrir dispositivo USB: ${t.message}")
            null
        } ?: return@withContext

        if (!conn.claimInterface(intf, true)) {
            try {
                conn.close()
            } catch (_: Throwable) {}
            return@withContext
        }

        val session = SonyPtpUsbSession(conn, bulkOut, bulkIn, epInt)
        isUsbSessionActive = true
        try {
            val isPcRemote = session.initializeSonyPcRemote(
                device = device,
                downloadedKeys = downloadedKeys,
                eventNameProvider = eventNameProvider,
                presetConfigProvider = presetConfigProvider,
                recentOriginalUris = recentOriginalUris,
                storageManager = storageManager,
                onStatusUpdate = onStatusUpdate,
                onProgress = onProgress,
                onPhotoSaved = onPhotoSaved,
                onLog = onLog
            )

            if (isPcRemote) {
                _usbModeDescription.value = "Modo PC Remoto activo · Podés disparar normalmente con la cámara"
                onStatusUpdate("🟢 Cámara lista en modo PC Remoto USB. ¡Sacá fotos normalmente con la cámara!")
                onLog("🟢 Sesión USB 'PC remoto' activa: podés disparar libremente con la cámara.")

                var shotCounter = 1
                var idleHeartbeat = 0
                while (currentCoroutineContext().isActive && isUsbSessionActive) {
                    val events = session.pollInterruptEvents(250)
                    var triggerDetected = false

                    for (ev in events) {
                        if (ev.code == PTP_EC_SONY_OBJECT_ADDED || ev.code == PTP_EC_OBJECT_ADDED) {
                            Log.i(TAG, "⚡ Evento PTP ObjectAdded recibido (code=0x${ev.code.toString(16)}, p1=0x${ev.param1.toString(16)})")
                            triggerDetected = true
                            break
                        } else if (ev.code == PTP_EC_SONY_PROPERTY_CHANGED && ev.param1 == 0xD215L) {
                            Log.i(TAG, "⚡ Evento PTP PropertyChanged (0xD215 ObjectInMemory) recibido")
                            triggerDetected = true
                            break
                        }
                    }

                    // Chequeo suave de respaldo cada ~3 segundos si no hubo eventos de interrupción
                    if (!triggerDetected && ++idleHeartbeat >= 12) {
                        idleHeartbeat = 0
                        val status = session.getRawObjectInMemoryStatus()
                        if (status > 0x8000 || status == 1) {
                            Log.d(TAG, "⚡ Heartbeat detectó captura activa o en RAM (status=0x${status.toString(16)})")
                            triggerDetected = true
                        }
                    }

                    if (triggerDetected) {
                        idleHeartbeat = 0
                        onStatusUpdate("📸 Disparo detectado en la cámara, procesando...")
                        val ready = session.waitForImageReady(maxWaitMs = 4500L)
                        if (!ready) {
                            Log.w(TAG, "Tiempo de espera agotado aguardando que la cámara termine el procesado")
                            continue
                        }

                        var drainedInBurst = 0
                        while (currentCoroutineContext().isActive && drainedInBurst < 30) {
                            val objInfo = session.getObjectInfo(SONY_RAM_OBJECT_HANDLE)
                            if (objInfo == null || objInfo.compressedSize <= 0L) {
                                break
                            }

                            val rawName = objInfo.fileName.trim()
                            val isRealDscName = rawName.isNotBlank() &&
                                    !rawName.startsWith("capt", ignoreCase = true) &&
                                    !rawName.equals("image.jpg", ignoreCase = true)

                            val cleanName = if (isRealDscName) {
                                rawName
                            } else {
                                "DSC_${System.currentTimeMillis()}_$shotCounter.JPG"
                            }
                            shotCounter++
                            drainedInBurst++

                            val currentEvent = eventNameProvider()
                            val currentPreset = presetConfigProvider()
                            onStatusUpdate("⚡ Transfiriendo foto por USB: $cleanName...")
                            onProgress(cleanName, 0.1f)

                            if (onOriginalCaptured != null) {
                                val origRes = storageManager.saveOriginalFromUsb(
                                    mtpDevice = device,
                                    objectHandle = SONY_RAM_OBJECT_HANDLE,
                                    fileName = cleanName,
                                    expectedSize = objInfo.compressedSize,
                                    eventName = currentEvent,
                                    recentOriginalUris = recentOriginalUris
                                ) { outputStream ->
                                    session.streamObjectToOutput(
                                        objectHandle = SONY_RAM_OBJECT_HANDLE,
                                        expectedSize = objInfo.compressedSize,
                                        outputStream = outputStream,
                                        onProgress = { p -> onProgress(cleanName, p) }
                                    )
                                }

                                if (origRes.isSuccess) {
                                    val origData = origRes.getOrThrow()
                                    val key = "${cleanName}_${origData.photo.fileSizeBytes}"
                                    if (isRealDscName) {
                                        downloadedKeys.add("NAME_${cleanName.uppercase()}")
                                    }
                                    onOriginalCaptured(origData, currentEvent, key)
                                } else {
                                    onLog("❌ Error guardando foto original $cleanName: ${origRes.exceptionOrNull()?.message}")
                                    break
                                }
                            } else {
                                val saveRes = storageManager.savePhotoFromUsbMtp(
                                    mtpDevice = device,
                                    objectHandle = SONY_RAM_OBJECT_HANDLE,
                                    fileName = cleanName,
                                    expectedSize = objInfo.compressedSize,
                                    eventName = currentEvent,
                                    presetConfig = currentPreset,
                                    recentOriginalUris = recentOriginalUris,
                                    readObjectCallback = { outputStream ->
                                        session.streamObjectToOutput(
                                            objectHandle = SONY_RAM_OBJECT_HANDLE,
                                            expectedSize = objInfo.compressedSize,
                                            outputStream = outputStream,
                                            onProgress = { p -> onProgress(cleanName, p) }
                                        )
                                    },
                                    onEditedPhotoReady = onEditedPhotoReady
                                )

                                if (saveRes.isSuccess) {
                                    val (origPhoto, editedPhoto) = saveRes.getOrThrow()
                                    val key = "${cleanName}_${origPhoto.fileSizeBytes}"
                                    if (isRealDscName) {
                                        downloadedKeys.add("NAME_${cleanName.uppercase()}")
                                    }
                                    onPhotoSaved(origPhoto, editedPhoto, key)
                                } else {
                                    onLog("❌ Error guardando foto en vivo $cleanName: ${saveRes.exceptionOrNull()?.message}")
                                    break
                                }
                            }

                            delay(150)
                            val remaining = session.getRawObjectInMemoryStatus()
                            if (remaining <= 0x8000) break
                        }
                    }
                }
            } else {
                // Modo MTP / Almacenamiento SD por PTP directo
                _usbModeDescription.value = "Modo MTP (Tarjeta SD) · Copiando fotos sacadas sin el celular"
                onLog("📂 Cámara en modo MTP: buscando fotos en la tarjeta SD que falten pasar al celular...")

                val syncedCount = session.syncSdCardObjectsViaPtp(
                    device = device,
                    downloadedKeys = downloadedKeys,
                    eventName = eventNameProvider(),
                    presetConfig = presetConfigProvider(),
                    recentOriginalUris = recentOriginalUris,
                    storageManager = storageManager,
                    onStatusUpdate = onStatusUpdate,
                    onProgress = onProgress,
                    onPhotoSaved = onPhotoSaved,
                    onLog = onLog
                )

                if (syncedCount > 0) {
                    onStatusUpdate("🎉 ¡Se copiaron $syncedCount foto(s) de la tarjeta SD! (Para disparar en vivo, cambiá Conexión USB a 'PC remoto').")
                } else {
                    onStatusUpdate("✅ Todas las fotos de la tarjeta SD ya están en el celular. (Para disparar en vivo, poné Conexión USB: PC remoto).")
                }

                while (currentCoroutineContext().isActive && isUsbSessionActive) {
                    delay(3000)
                    session.syncSdCardObjectsViaPtp(
                        device = device,
                        downloadedKeys = downloadedKeys,
                        eventName = eventNameProvider(),
                        presetConfig = presetConfigProvider(),
                        recentOriginalUris = recentOriginalUris,
                        storageManager = storageManager,
                        onStatusUpdate = onStatusUpdate,
                        onProgress = onProgress,
                        onPhotoSaved = onPhotoSaved,
                        onLog = onLog
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Sesión USB finalizada: ${t.message}")
        } finally {
            isUsbSessionActive = false
            _isUsbCameraConnected.value = false
            _usbModeDescription.value = "Cable USB desconectado"
            Log.w(TAG, "Sesión USB concluida. Estado USB marcado como desconectado.")
            try {
                session.closeSessionCleanly()
            } catch (_: Throwable) {}
            try {
                conn.releaseInterface(intf)
            } catch (_: Throwable) {}
            try {
                conn.close()
            } catch (_: Throwable) {}
        }
    }
}

private data class PtpEvent(
    val code: Int,
    val txId: Int,
    val param1: Long
)

private class SonyPtpUsbSession(
    private val conn: UsbDeviceConnection,
    private val epOut: UsbEndpoint,
    private val epIn: UsbEndpoint,
    private val epInt: UsbEndpoint?
) {
    private var transactionId = 0
    private val sessionId = 1
    private val readBuffer = ByteArray(65536)
    private val intBuffer = ByteArray(64)

    suspend fun initializeSonyPcRemote(
        device: UsbDevice,
        downloadedKeys: MutableSet<String>,
        eventNameProvider: () -> String,
        presetConfigProvider: () -> PresetConfig,
        recentOriginalUris: MutableList<android.net.Uri>,
        storageManager: PhotoStorageManager,
        onStatusUpdate: (String) -> Unit,
        onProgress: (fileName: String, progress: Float) -> Unit,
        onPhotoSaved: (original: TetheredPhoto, edited: TetheredPhoto?, key: String) -> Unit,
        onLog: (String) -> Unit
    ): Boolean {
        // 1. GetDeviceInfo (0x1001)
        executeCommand(PTP_OC_GET_DEVICE_INFO, intArrayOf())

        // 2. OpenSession (0x1002, sessionId = 1)
        val openRc = executeCommand(PTP_OC_OPEN_SESSION, intArrayOf(sessionId)).first
        if (openRc != PTP_RC_OK && openRc != PTP_RC_SESSION_ALREADY_OPEN) {
            Log.w(TAG, "OpenSession devolvió 0x${openRc.toString(16)}")
        }

        // 3. Sony SDIO_Connect Fase 1 (0x9201, params: 1, 0, 0)
        val sdio1 = executeCommand(PTP_OC_SONY_SDIO_CONNECT, intArrayOf(1, 0, 0)).first
        if (sdio1 != PTP_RC_OK) {
            Log.i(TAG, "La cámara está en modo MTP (SDIO_Connect devolvió 0x${sdio1.toString(16)})")
            return false
        }

        // 4. Sony SDIO_Connect Fase 2 (0x9201, params: 2, 0, 0)
        executeCommand(PTP_OC_SONY_SDIO_CONNECT, intArrayOf(2, 0, 0))

        // 5. Sony GetSDIOGetExtDeviceInfo (0x9202, param: 0x012C = 300)
        val extRes = executeCommand(PTP_OC_SONY_GET_EXT_DEVICE_INFO, intArrayOf(0x012C))
        if (extRes.first != PTP_RC_OK) {
            executeCommand(PTP_OC_SONY_GET_EXT_DEVICE_INFO, intArrayOf(0x00C8))
        }

        // 6. Sony SDIO_Connect Fase 3 (0x9201, params: 3, 0, 0)
        val sdio3 = executeCommand(PTP_OC_SONY_SDIO_CONNECT, intArrayOf(3, 0, 0)).first

        // 7. Leer propiedades iniciales (0x9209)
        executeCommand(PTP_OC_SONY_GET_ALL_DEV_PROP_DATA, intArrayOf())

        return sdio3 == PTP_RC_OK
    }

    fun closeSessionCleanly() {
        try {
            executeCommand(PTP_OC_CLOSE_SESSION, intArrayOf())
        } catch (_: Throwable) {}
    }

    suspend fun syncSdCardObjectsViaPtp(
        device: UsbDevice,
        downloadedKeys: MutableSet<String>,
        eventName: String,
        presetConfig: PresetConfig,
        recentOriginalUris: MutableList<android.net.Uri>,
        storageManager: PhotoStorageManager,
        onStatusUpdate: (String) -> Unit,
        onProgress: (fileName: String, progress: Float) -> Unit,
        onPhotoSaved: (original: TetheredPhoto, edited: TetheredPhoto?, key: String) -> Unit,
        onLog: (String) -> Unit
    ): Int {
        try {
            val (rcStorage, storageData) = executeCommand(PTP_OC_GET_STORAGE_IDS, intArrayOf())
            if (rcStorage != PTP_RC_OK || storageData == null || storageData.size < 4) return 0

            val storageBb = ByteBuffer.wrap(storageData).order(ByteOrder.LITTLE_ENDIAN)
            val storageCount = storageBb.int
            if (storageCount <= 0 || storageCount > 16) return 0

            val storageIds = IntArray(minOf(storageCount, (storageData.size - 4) / 4)) {
                storageBb.int
            }

            val pendingHandles = mutableListOf<Pair<Int, PtpObjectInfo>>()
            for (storageId in storageIds) {
                if (storageId == 0) continue

                var (rcHandles, handlesData) = executeCommand(
                    PTP_OC_GET_OBJECT_HANDLES,
                    intArrayOf(storageId, PTP_OFC_EXIF_JPEG, -1)
                )
                if (rcHandles != PTP_RC_OK || handlesData == null || handlesData.size <= 4) {
                    val fallback = executeCommand(
                        PTP_OC_GET_OBJECT_HANDLES,
                        intArrayOf(storageId, 0, -1)
                    )
                    rcHandles = fallback.first
                    handlesData = fallback.second
                }

                if (rcHandles != PTP_RC_OK || handlesData == null || handlesData.size < 4) continue

                val hBb = ByteBuffer.wrap(handlesData).order(ByteOrder.LITTLE_ENDIAN)
                val hCount = hBb.int
                if (hCount <= 0) continue

                val maxHandles = minOf(hCount, (handlesData.size - 4) / 4)
                val handleList = IntArray(maxHandles) { hBb.int }
                handleList.sortDescending()

                for (handle in handleList) {
                    if (!currentCoroutineContext().isActive) break
                    if (handle == 0 || handle == SONY_RAM_OBJECT_HANDLE) continue
                    val info = getObjectInfo(handle) ?: continue
                    val name = info.fileName.trim()
                    if (name.isBlank() || info.compressedSize <= 0L) continue
                    val isJpeg = info.objectFormat == PTP_OFC_EXIF_JPEG ||
                            name.endsWith(".JPG", true) ||
                            name.endsWith(".JPEG", true)
                    if (!isJpeg) continue

                    val key = "${name}_${info.compressedSize}"
                    val nameKey = "NAME_${name.uppercase()}"
                    if (!downloadedKeys.contains(key) && !downloadedKeys.contains(nameKey)) {
                        pendingHandles.add(handle to info)
                    }
                }
            }

            if (pendingHandles.isEmpty()) return 0

            onLog("📦 Detectadas ${pendingHandles.size} foto(s) nuevas en la tarjeta SD. Descargando por USB...")
            var savedCount = 0
            pendingHandles.forEachIndexed { idx, (handle, info) ->
                if (!currentCoroutineContext().isActive) return savedCount
                val name = info.fileName.trim()
                onStatusUpdate("⚡ Copiando de tarjeta SD (${idx + 1}/${pendingHandles.size}): $name...")
                onProgress(name, (idx + 1).toFloat() / pendingHandles.size)

                val saveRes = storageManager.savePhotoFromUsbMtp(
                    mtpDevice = device,
                    objectHandle = handle,
                    fileName = name,
                    expectedSize = info.compressedSize,
                    eventName = eventName,
                    presetConfig = presetConfig,
                    recentOriginalUris = recentOriginalUris,
                    readObjectCallback = { outputStream ->
                        streamObjectToOutput(
                            objectHandle = handle,
                            expectedSize = info.compressedSize,
                            outputStream = outputStream,
                            onProgress = { p -> onProgress(name, p) }
                        )
                    }
                )

                if (saveRes.isSuccess) {
                    val (origPhoto, editedPhoto) = saveRes.getOrThrow()
                    val key = "${name}_${info.compressedSize}"
                    downloadedKeys.add("NAME_${name.uppercase()}")
                    savedCount++
                    onPhotoSaved(origPhoto, editedPhoto, key)
                }
            }
            return savedCount
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.d(TAG, "Aviso al leer tarjeta SD por PTP: ${t.message}")
            return 0
        }
    }

    fun pollInterruptEvents(timeoutMs: Int): List<PtpEvent> {
        val intEp = epInt ?: return emptyList()
        val events = mutableListOf<PtpEvent>()
        val n = conn.bulkTransfer(intEp, intBuffer, intBuffer.size, timeoutMs)
        if (n >= 12) {
            var offset = 0
            while (offset + 12 <= n) {
                val bb = ByteBuffer.wrap(intBuffer, offset, n - offset).order(ByteOrder.LITTLE_ENDIAN)
                val len = bb.int
                if (len < 12 || offset + len > n) {
                    val type = bb.short
                    val code = bb.short.toInt() and 0xFFFF
                    val txId = bb.int
                    val p1 = if (n - offset >= 16) bb.int.toLong() and 0xFFFFFFFFL else 0L
                    if (type == PTP_CNT_EVENT) {
                        events.add(PtpEvent(code, txId, p1))
                    }
                    break
                }
                val type = bb.short
                val code = bb.short.toInt() and 0xFFFF
                val txId = bb.int
                val p1 = if (len >= 16) bb.int.toLong() and 0xFFFFFFFFL else 0L
                if (type == PTP_CNT_EVENT) {
                    events.add(PtpEvent(code, txId, p1))
                }
                offset += len
            }
        }
        return events
    }

    fun getRawObjectInMemoryStatus(): Int {
        val (rc, data) = executeCommand(PTP_OC_SONY_GET_ALL_DEV_PROP_DATA, intArrayOf())
        if (rc != PTP_RC_OK || data == null || data.size < 16) return 0

        val limit = data.size - 10
        for (i in 0..limit) {
            if (data[i] == 0x15.toByte() &&
                data[i + 1] == 0xD2.toByte() &&
                data[i + 2] == 0x04.toByte() &&
                data[i + 3] == 0x00.toByte() &&
                (data[i + 4] == 0.toByte() || data[i + 4] == 1.toByte())
            ) {
                val curLo = data[i + 8].toInt() and 0xFF
                val curHi = data[i + 9].toInt() and 0xFF
                return curLo or (curHi shl 8)
            }
        }
        return 0
    }

    suspend fun waitForImageReady(maxWaitMs: Long = 4500L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            // 1. Revisar si llegó evento ObjectAdded
            val events = pollInterruptEvents(50)
            for (ev in events) {
                if (ev.code == PTP_EC_SONY_OBJECT_ADDED || ev.code == PTP_EC_OBJECT_ADDED) {
                    Log.i(TAG, "⚡ Evento ObjectAdded (0x${ev.code.toString(16)}) detectado con p1=0x${ev.param1.toString(16)}")
                    delay(150)
                    return true
                }
            }

            // 2. Revisar propiedad D215 en la memoria de la cámara
            val status = getRawObjectInMemoryStatus()
            if (status > 0x8000) {
                Log.i(TAG, "⚡ D215 > 0x8000 (0x${status.toString(16)}) -> ¡Foto lista en RAM!")
                delay(150)
                return true
            } else if (status == 1) {
                Log.d(TAG, "📸 Cámara procesando captura (D215=1)... aguardando buffer RAM...")
            }

            delay(150)
        }
        return false
    }

    fun getObjectInfo(objectHandle: Int): PtpObjectInfo? {
        val (rc, data) = executeCommand(PTP_OC_GET_OBJECT_INFO, intArrayOf(objectHandle))
        if (rc != PTP_RC_OK || data == null || data.size < 52) {
            Log.d(TAG, "GetObjectInfo devolvió rc=0x${rc.toString(16)} (size=${data?.size})")
            return null
        }

        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        bb.int
        val objectFormat = bb.short.toInt() and 0xFFFF
        bb.short
        val compressedSize = bb.int.toLong() and 0xFFFFFFFFL

        var fileName = ""
        if (data.size >= 53) {
            val numChars = data[52].toInt() and 0xFF
            val byteCount = numChars * 2
            if (numChars > 0 && 53 + byteCount <= data.size) {
                val rawStr = String(data, 53, byteCount, StandardCharsets.UTF_16LE)
                fileName = rawStr.trimEnd('\u0000').trim()
            }
        }
        Log.i(TAG, "Foto en RAM lista para descarga: '$fileName' ($compressedSize bytes)")
        return PtpObjectInfo(
            objectFormat = objectFormat,
            compressedSize = compressedSize,
            fileName = fileName
        )
    }

    fun streamObjectToOutput(
        objectHandle: Int,
        expectedSize: Long,
        outputStream: OutputStream,
        onProgress: (Float) -> Unit
    ): Long {
        val txId = ++transactionId
        val cmdBuf = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        cmdBuf.putInt(16)
        cmdBuf.putShort(PTP_CNT_COMMAND)
        cmdBuf.putShort(PTP_OC_GET_OBJECT.toShort())
        cmdBuf.putInt(txId)
        cmdBuf.putInt(objectHandle)

        val cmdBytes = cmdBuf.array()
        val sent = conn.bulkTransfer(epOut, cmdBytes, cmdBytes.size, 4000)
        if (sent < 0) return 0L

        val firstRead = conn.bulkTransfer(epIn, readBuffer, readBuffer.size, 6000)
        if (firstRead < 12) return 0L

        val bb = ByteBuffer.wrap(readBuffer, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
        val totalContainerLen = bb.int.toLong() and 0xFFFFFFFFL
        val containerType = bb.short
        val code = bb.short.toInt() and 0xFFFF

        if (containerType == PTP_CNT_RESPONSE) {
            Log.w(TAG, "GetObject devolvió respuesta sin datos: 0x${code.toString(16)}")
            return 0L
        }

        val totalPayloadBytes = if (totalContainerLen > 12L && totalContainerLen != 0xFFFFFFFFL) {
            totalContainerLen - 12L
        } else {
            expectedSize
        }

        var writtenPayloadBytes = 0L
        var lastReportedPercent = 0
        var responseConsumed = false

        if (firstRead > 12) {
            val payloadInFirst = minOf((firstRead - 12).toLong(), totalPayloadBytes).toInt()
            outputStream.write(readBuffer, 12, payloadInFirst)
            writtenPayloadBytes += payloadInFirst
            if (firstRead >= 12 + payloadInFirst + 12) {
                responseConsumed = true
            }
        }

        while (writtenPayloadBytes < totalPayloadBytes) {
            val r = conn.bulkTransfer(epIn, readBuffer, readBuffer.size, 6000)
            if (r <= 0) break

            val remaining = totalPayloadBytes - writtenPayloadBytes
            val toWrite = minOf(r.toLong(), remaining).toInt()
            if (toWrite > 0) {
                outputStream.write(readBuffer, 0, toWrite)
                writtenPayloadBytes += toWrite
            }

            if (totalPayloadBytes > 0) {
                val pct = ((writtenPayloadBytes * 100L) / totalPayloadBytes).toInt()
                if (pct - lastReportedPercent >= 10 || pct == 100) {
                    lastReportedPercent = pct
                    onProgress(pct / 100f)
                }
            }

            if (r > toWrite && (r - toWrite) >= 12) {
                responseConsumed = true
                break
            }
        }

        if (!responseConsumed) {
            var respRead = conn.bulkTransfer(epIn, readBuffer, 512, 2000)
            if (respRead == 0) {
                respRead = conn.bulkTransfer(epIn, readBuffer, 512, 2000)
            }
            Log.d(TAG, "GetObject response container consumido ($respRead bytes)")
        }

        return writtenPayloadBytes
    }

    private fun executeCommand(
        opCode: Int,
        params: IntArray
    ): Pair<Int, ByteArray?> {
        val txId = ++transactionId
        val cmdLen = 12 + params.size * 4
        val cmdBuf = ByteBuffer.allocate(cmdLen).order(ByteOrder.LITTLE_ENDIAN)
        cmdBuf.putInt(cmdLen)
        cmdBuf.putShort(PTP_CNT_COMMAND)
        cmdBuf.putShort(opCode.toShort())
        cmdBuf.putInt(txId)
        for (p in params) {
            cmdBuf.putInt(p)
        }

        val cmdBytes = cmdBuf.array()
        val sent = conn.bulkTransfer(epOut, cmdBytes, cmdBytes.size, 3000)
        if (sent < 0) return Pair(-1, null)

        val firstRead = conn.bulkTransfer(epIn, readBuffer, readBuffer.size, 4000)
        if (firstRead < 12) return Pair(-1, null)

        val headerBb = ByteBuffer.wrap(readBuffer, 0, 12).order(ByteOrder.LITTLE_ENDIAN)
        val totalLen = headerBb.int.toLong() and 0xFFFFFFFFL
        val type = headerBb.short
        val code = headerBb.short.toInt() and 0xFFFF

        if (type == PTP_CNT_RESPONSE) {
            return Pair(code, null)
        }

        if (type == PTP_CNT_DATA) {
            val out = ByteArrayOutputStream()
            val expectedPayload = (totalLen - 12L).coerceAtLeast(0L)
            if (firstRead > 12) {
                val n = minOf((firstRead - 12).toLong(), expectedPayload).toInt()
                out.write(readBuffer, 12, n)
                val extraOffset = 12 + n
                if (firstRead - extraOffset >= 12) {
                    val respBb = ByteBuffer.wrap(readBuffer, extraOffset, firstRead - extraOffset)
                        .order(ByteOrder.LITTLE_ENDIAN)
                    respBb.int
                    val respType = respBb.short
                    val respCode = respBb.short.toInt() and 0xFFFF
                    if (respType == PTP_CNT_RESPONSE) {
                        return Pair(respCode, out.toByteArray())
                    }
                }
            }

            while (out.size().toLong() < expectedPayload) {
                val r = conn.bulkTransfer(epIn, readBuffer, readBuffer.size, 4000)
                if (r <= 0) break
                val rem = expectedPayload - out.size().toLong()
                val n = minOf(r.toLong(), rem).toInt()
                if (n > 0) {
                    out.write(readBuffer, 0, n)
                }
                if (r > n && (r - n) >= 12) {
                    val respBb = ByteBuffer.wrap(readBuffer, n, r - n).order(ByteOrder.LITTLE_ENDIAN)
                    respBb.int
                    val respType = respBb.short
                    val respCode = respBb.short.toInt() and 0xFFFF
                    if (respType == PTP_CNT_RESPONSE) {
                        return Pair(respCode, out.toByteArray())
                    }
                }
            }

            val respRead = conn.bulkTransfer(epIn, readBuffer, 512, 3000)
            if (respRead >= 12) {
                val respBb = ByteBuffer.wrap(readBuffer, 0, respRead).order(ByteOrder.LITTLE_ENDIAN)
                respBb.int
                respBb.short
                val respCode = respBb.short.toInt() and 0xFFFF
                return Pair(respCode, out.toByteArray())
            }
            return Pair(PTP_RC_OK, out.toByteArray())
        }

        return Pair(code, null)
    }
}
