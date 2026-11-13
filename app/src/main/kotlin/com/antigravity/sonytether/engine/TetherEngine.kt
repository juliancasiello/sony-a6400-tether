package com.antigravity.sonytether.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.antigravity.sonytether.editor.ImagePresetProcessor
import com.antigravity.sonytether.editor.PresetConfig
import com.antigravity.sonytether.lumepic.LumepicManager
import com.antigravity.sonytether.model.SonyCameraDatabase
import com.antigravity.sonytether.model.SonyCameraModel
import com.antigravity.sonytether.updater.AppUpdateManager
import com.antigravity.sonytether.model.CameraConnectionState
import com.antigravity.sonytether.model.DownloadStatus
import com.antigravity.sonytether.model.TetheredPhoto
import com.antigravity.sonytether.model.UploadStatus
import com.antigravity.sonytether.momentia.MomentiaManager
import com.antigravity.sonytether.network.SonyUsbMtpHelper
import com.antigravity.sonytether.service.TetherForegroundService
import com.antigravity.sonytether.storage.PhotoStorageManager
import com.antigravity.sonytether.storage.SavedOriginalResult
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TetherEngine"

data class PendingShot(
    val origData: SavedOriginalResult,
    val eventName: String,
    val key: String,
    val uprightPreviewBitmap: Bitmap
)

class TetherEngine(private val context: Context) {

    companion object {
        @Volatile
        private var instance: TetherEngine? = null

        fun getInstance(context: Context): TetherEngine {
            return instance ?: synchronized(this) {
                instance ?: TetherEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = context.getSharedPreferences("sony_tether_presets_v2", Context.MODE_PRIVATE)

    // Modelo de cámara seleccionado manualmente o detectado por hardware
    private val _selectedCameraId = MutableStateFlow(prefs.getString("selected_camera_id", "a6400") ?: "a6400")
    val selectedCameraId: StateFlow<String> = _selectedCameraId.asStateFlow()

    private val _detectedCameraModelName = MutableStateFlow<String?>(null)
    val detectedCameraModelName: StateFlow<String?> = _detectedCameraModelName.asStateFlow()

    val currentCameraModel: StateFlow<SonyCameraModel> = combine(
        _selectedCameraId,
        _detectedCameraModelName
    ) { selectedId, detected ->
        if (detected != null) {
            SonyCameraDatabase.matchFromProductName(detected) ?: SonyCameraDatabase.findById(selectedId)
        } else {
            SonyCameraDatabase.findById(selectedId)
        }
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        SonyCameraDatabase.findById("a6400")
    )

    fun selectCameraModel(id: String) {
        _selectedCameraId.value = id
        prefs.edit().putString("selected_camera_id", id).apply()
        val cam = SonyCameraDatabase.findById(id)
        addLog("📷 Cámara seleccionada: ${cam.displayName}")
    }

    val storageManager = PhotoStorageManager(context)
    val momentiaManager = MomentiaManager(context)
    val lumepicManager = LumepicManager(context)
    val updateManager = AppUpdateManager(context)

    val ftpServer = com.antigravity.sonytether.network.SonyFtpServer(
        context = context,
        onPhotoReceived = { fileName, bytes ->
            processWifiPhoto(fileName, bytes)
        },
        onLog = { addLog(it) }
    )
    val ftpServerState: StateFlow<com.antigravity.sonytether.network.FtpServerState> = ftpServer.state

    fun toggleFtpServer(port: Int = com.antigravity.sonytether.network.DEFAULT_FTP_PORT) {
        if (ftpServer.state.value.isRunning) {
            ftpServer.stop()
        } else {
            ftpServer.start(port)
        }
    }

    private fun processWifiPhoto(fileName: String, originalBytes: ByteArray) {
        scope.launch(Dispatchers.IO) {
            val eventName = _activeEventName.value
            val saveRes = storageManager.saveOriginalFromBytes(
                fileName = fileName,
                originalBytes = originalBytes,
                eventName = eventName,
                recentOriginalUris = recentOriginalUris
            )
            if (saveRes.isSuccess) {
                val origData = saveRes.getOrThrow()
                handleOriginalCaptured(origData, eventName, "wifi://$fileName")
            } else {
                addLog("❌ Error guardando foto WiFi $fileName: ${saveRes.exceptionOrNull()?.message}")
            }
        }
    }

    val onNewMomentiaSale = kotlinx.coroutines.flow.MutableSharedFlow<com.antigravity.sonytether.momentia.MomentiaSaleOrder>(extraBufferCapacity = 10)
    val usbMtpHelper = SonyUsbMtpHelper(context) { usbDevice ->
        val prodName = usbDevice.productName
        if (!prodName.isNullOrBlank()) {
            _detectedCameraModelName.value = prodName
        }
        val camName = prodName ?: currentCameraModel.value.displayName
        addLog("⚡ Cámara Sony conectada por USB ($camName). Iniciando sesión...")
        startUsbTethering()
    }
    val isUsbCameraConnected: StateFlow<Boolean> = usbMtpHelper.isUsbCameraConnected

    private val _connectionState =
        MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    val connectionState: StateFlow<CameraConnectionState> = _connectionState.asStateFlow()

    private val _photos = MutableStateFlow<List<TetheredPhoto>>(emptyList())
    val photos: StateFlow<List<TetheredPhoto>> = _photos.asStateFlow()

    private val _latestPhoto = MutableStateFlow<TetheredPhoto?>(null)
    val latestPhoto: StateFlow<TetheredPhoto?> = _latestPhoto.asStateFlow()

    private val _latestOriginalPhoto = MutableStateFlow<TetheredPhoto?>(null)
    val latestOriginalPhoto: StateFlow<TetheredPhoto?> = _latestOriginalPhoto.asStateFlow()

    // Vista previa en vivo del Bitmap procesado con el Preset actual
    private val _heroPreviewBitmap = MutableStateFlow<Bitmap?>(null)
    val heroPreviewBitmap: StateFlow<Bitmap?> = _heroPreviewBitmap.asStateFlow()

    // Contador regresivo de 30 segundos para la última foto pendiente
    private val _pendingCountdownSeconds = MutableStateFlow<Int?>(null)
    val pendingCountdownSeconds: StateFlow<Int?> = _pendingCountdownSeconds.asStateFlow()

    // Intervalo de aviso periódico para Smartwatch / Garmin (en minutos, 0 = desactivado)
    private val _garminHeartbeatMinutes = MutableStateFlow(5)
    val garminHeartbeatMinutes: StateFlow<Int> = _garminHeartbeatMinutes.asStateFlow()

    // Toggle para subir copia en blanco y negro (original + copia B&N)
    private val _isBwCopyEnabled = MutableStateFlow(false)
    val isBwCopyEnabled: StateFlow<Boolean> = _isBwCopyEnabled.asStateFlow()

    // Toggle para subir únicamente conectado a Wi-Fi (ahorro de datos móviles)
    private val _isWifiOnlyUpload = MutableStateFlow(false)
    val isWifiOnlyUpload: StateFlow<Boolean> = _isWifiOnlyUpload.asStateFlow()

    fun setWifiOnlyUpload(enabled: Boolean) {
        _isWifiOnlyUpload.value = enabled
        prefs.edit().putBoolean("wifi_only_upload", enabled).apply()
        addLog(if (enabled) "📶 Subida configurada en: SOLO WI-FI (ahorro de datos móviles)" else "📶 Subida configurada en: WI-FI Y DATOS MÓVILES")
        if (!enabled || isWifiConnected()) {
            momentiaManager.retryFailedUploads()
            lumepicManager.retryFailedUploads()
        }
    }

    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _syncStatusMessage = MutableStateFlow<String?>(
        "Enchufá el cable USB con la cámara en modo 'PC remoto' para disparar y recibir las fotos al instante."
    )
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    private val defaultEventName = "Evento_${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
    private val _activeEventName = MutableStateFlow(defaultEventName)
    val activeEventName: StateFlow<String> = _activeEventName.asStateFlow()

    private val _recentEvents = MutableStateFlow<List<String>>(emptyList())
    val recentEvents: StateFlow<List<String>> = _recentEvents.asStateFlow()

    // Configuración del Preset en vivo (estilo Lightroom)
    private val _presetConfig = MutableStateFlow(PresetConfig())
    val presetConfig: StateFlow<PresetConfig> = _presetConfig.asStateFlow()

    private val recentOriginalUris = java.util.Collections.synchronizedList(mutableListOf<android.net.Uri>())
    private var usbSessionJob: Job? = null
    private val downloadedOriginalKeys = ConcurrentHashMap.newKeySet<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Buffer de la última foto tomada que está en espera de ajuste o disparo siguiente
    private var pendingShot: PendingShot? = null
    private var pendingCountdownJob: Job? = null

    // Bitmap base sin procesar de la foto actualmente en pantalla para renderizado en vivo
    private var activeBasePreviewBitmap: Bitmap? = null
    private var activePreviewJob: Job? = null
    private val _isComparingOriginal = MutableStateFlow(false)
    val isComparingOriginal: StateFlow<Boolean> = _isComparingOriginal.asStateFlow()

    init {
        prefs.getStringSet("downloaded_keys", emptySet())?.let { saved ->
            downloadedOriginalKeys.addAll(saved)
        }
        val savedEvent = prefs.getString("active_event_name", null)?.takeIf { it.isNotBlank() } ?: defaultEventName
        _activeEventName.value = savedEvent
        val savedRecent = prefs.getStringSet("recent_events", emptySet())?.toList().orEmpty()
        _recentEvents.value = (listOf(savedEvent) + savedRecent).distinct().take(10)

        // Cargar preset guardado completo estilo Lightroom
        _presetConfig.value = PresetConfig(
            exposure = prefs.getFloat("p_exposure", 0.15f),
            highlights = prefs.getFloat("p_highlights", -0.20f),
            shadows = prefs.getFloat("p_shadows", 0.25f),
            whites = prefs.getFloat("p_whites", 0.10f),
            blacks = prefs.getFloat("p_blacks", -0.10f),
            contrast = prefs.getFloat("p_contrast", 0.10f),
            texture = prefs.getFloat("p_texture", 0.15f),
            dehaze = prefs.getFloat("p_dehaze", 0.10f),
            temperature = prefs.getFloat("p_temperature", 0.08f),
            saturation = prefs.getFloat("p_saturation", 1.15f)
        )

        _garminHeartbeatMinutes.value = prefs.getInt("garmin_heartbeat_minutes", 5)
        _isBwCopyEnabled.value = prefs.getBoolean("bw_copy_enabled", false)
        _isWifiOnlyUpload.value = prefs.getBoolean("wifi_only_upload", false)

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: android.net.NetworkCapabilities) {
                    if (networkCapabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) && _isWifiOnlyUpload.value) {
                        addLog("📶 Wi-Fi detectado: reanudando subidas a la nube...")
                        momentiaManager.retryFailedUploads()
                        lumepicManager.retryFailedUploads()
                    }
                }
            })
        } catch (_: Exception) {}

        momentiaManager.isWifiOnlyProvider = { _isWifiOnlyUpload.value }
        momentiaManager.isWifiConnectedProvider = { isWifiConnected() }
        lumepicManager.isWifiOnlyProvider = { _isWifiOnlyUpload.value }
        lumepicManager.isWifiConnectedProvider = { isWifiConnected() }

        momentiaManager.onNewSaleDetected = { sale ->
            addLog("💰 ¡VENTA EN MOMENTIA! ${sale.photosCount} foto(s) - $${sale.photographerEarnings.toInt()} ${sale.currency}")
            onNewMomentiaSale.tryEmit(sale)
        }

        momentiaManager.onPhotoUploadFinished = { fileName, success ->
            _photos.update { list ->
                list.map { photo ->
                    if (photo.fileName == fileName) {
                        photo.copy(uploadStatus = if (success) UploadStatus.COMPLETED else UploadStatus.FAILED)
                    } else photo
                }
            }
            if (_latestPhoto.value?.fileName == fileName) {
                _latestPhoto.update { it?.copy(uploadStatus = if (success) UploadStatus.COMPLETED else UploadStatus.FAILED) }
            }
            if (success) {
                addLog("☁️ Subida completada a Momentia: $fileName")
            } else {
                addLog("❌ Falló subida a Momentia: $fileName")
            }
        }

        lumepicManager.onPhotoUploadFinished = { fileName, success ->
            _photos.update { list ->
                list.map { photo ->
                    if (photo.fileName == fileName) {
                        photo.copy(uploadStatus = if (success) UploadStatus.COMPLETED else UploadStatus.FAILED)
                    } else photo
                }
            }
            if (_latestPhoto.value?.fileName == fileName) {
                _latestPhoto.update { it?.copy(uploadStatus = if (success) UploadStatus.COMPLETED else UploadStatus.FAILED) }
            }
            if (success) {
                addLog("☁️ Subida completada a Lumepic: $fileName")
            } else {
                addLog("❌ Falló subida a Lumepic: $fileName")
            }
        }

        addLog("Iniciando recepción USB directa (Evento: $savedEvent)...")
        usbMtpHelper.startMonitoring()
        startUsbTethering()

        scope.launch {
            try {
                delay(3000)
                updateManager.checkForUpdates(silent = true)
            } catch (_: Exception) {}
        }
    }

    fun shutdownAndExit(context: Context) {
        addLog("🛑 Deteniendo servicio de Tethering y cerrando aplicación...")
        usbSessionJob?.cancel()
        usbSessionJob = null
        usbMtpHelper.stopMonitoring()
        TetherForegroundService.stop(context)
        val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notifManager?.cancelAll()
    }

    fun setGarminHeartbeatMinutes(minutes: Int) {
        val safeMinutes = minutes.coerceIn(0, 60)
        _garminHeartbeatMinutes.value = safeMinutes
        prefs.edit().putInt("garmin_heartbeat_minutes", safeMinutes).apply()
        addLog("⌚ Avisos a Garmin configurados: ${if (safeMinutes > 0) "cada $safeMinutes min" else "desactivados"}")
    }

    fun setBwCopyEnabled(enabled: Boolean) {
        _isBwCopyEnabled.value = enabled
        prefs.edit().putBoolean("bw_copy_enabled", enabled).apply()
        addLog("🌓 Copia en Blanco y Negro (B&N): ${if (enabled) "ACTIVADA (Original + Copia B&N)" else "DESACTIVADA (Solo Original)"}")
    }

    fun toggleBwCopy() {
        setBwCopyEnabled(!_isBwCopyEnabled.value)
    }

    fun setComparingOriginal(comparing: Boolean) {
        _isComparingOriginal.value = comparing
        if (comparing) {
            val base = activeBasePreviewBitmap ?: pendingShot?.uprightPreviewBitmap
            if (base != null && !base.isRecycled) {
                _heroPreviewBitmap.value = base
            }
        } else {
            renderLivePreview()
        }
    }

    fun renderLivePreview(config: PresetConfig = _presetConfig.value) {
        if (_isComparingOriginal.value) return
        val base = activeBasePreviewBitmap ?: pendingShot?.uprightPreviewBitmap ?: return
        if (base.isRecycled) return

        activePreviewJob?.cancel()
        activePreviewJob = scope.launch(Dispatchers.Default) {
            try {
                val preview = ImagePresetProcessor.applyPresetToBitmap(base, config)
                _heroPreviewBitmap.value = preview
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando vista previa en vivo: ${e.message}")
            }
        }
    }

    fun setActivePreviewPhoto(photo: TetheredPhoto?) {
        if (photo == null) {
            activeBasePreviewBitmap = null
            _heroPreviewBitmap.value = null
            return
        }

        val pending = pendingShot
        if (pending != null && (pending.origData.fileName == photo.fileName || pending.key == photo.remoteUrl)) {
            activeBasePreviewBitmap = pending.uprightPreviewBitmap
            renderLivePreview()
            return
        }

        val uri = photo.localUri ?: return
        scope.launch(Dispatchers.IO) {
            val base = loadBasePreview(uri)
            if (base != null) {
                activeBasePreviewBitmap = base
                renderLivePreview()
            }
        }
    }

    private fun loadBasePreview(uri: android.net.Uri): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = kotlin.math.max(boundsOpts.outWidth, boundsOpts.outHeight)
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= 1200) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
            }
            val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val rotation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = android.media.ExifInterface(stream)
                    when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (_: Exception) { 0 }

            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rot = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                if (rot != raw) raw.recycle()
                rot
            } else {
                raw
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cargando base preview desde URI: ${e.message}")
            null
        }
    }

    fun updatePreset(config: PresetConfig) {
        _presetConfig.value = config
        prefs.edit()
            .putFloat("p_exposure", config.exposure)
            .putFloat("p_highlights", config.highlights)
            .putFloat("p_shadows", config.shadows)
            .putFloat("p_whites", config.whites)
            .putFloat("p_blacks", config.blacks)
            .putFloat("p_contrast", config.contrast)
            .putFloat("p_texture", config.texture)
            .putFloat("p_dehaze", config.dehaze)
            .putFloat("p_temperature", config.temperature)
            .putFloat("p_saturation", config.saturation)
            .apply()

        // Renderizar en vivo inmediatamente para la foto activa
        renderLivePreview(config)
    }

    /**
     * Finaliza inmediatamente la foto pendiente sin esperar a que transcurran los 30 segundos.
     */
    fun finalizePendingPhotoNow() {
        val shot = pendingShot ?: return
        pendingCountdownJob?.cancel()
        pendingCountdownJob = null
        pendingShot = null
        _pendingCountdownSeconds.value = null
        scope.launch {
            finalizeAndUploadPendingShot(shot, _presetConfig.value)
        }
    }

    private suspend fun finalizeAndUploadPendingShot(shot: PendingShot, config: PresetConfig) = withContext(Dispatchers.IO) {
        try {
            val baseName = shot.origData.fileName
            addLog("🎨 Aplicando preset definitivo y guardando editada: $baseName...")

            val editRes = storageManager.saveEditedPhoto(
                originalBytes = shot.origData.originalBytes,
                baseFileName = baseName,
                rotationDegrees = shot.origData.rotationDegrees,
                eventName = shot.eventName,
                presetConfig = config,
                onEditedPhotoReady = { editedFileName, bytes ->
                    if (momentiaManager.state.value.isAutoUploadEnabled) {
                        addLog("☁️ Subiendo $editedFileName a Momentia...")
                        momentiaManager.enqueuePhotoUpload(editedFileName, bytes)
                    }
                }
            )

            if (editRes.isSuccess) {
                val editedPhoto = editRes.getOrThrow().copy(
                    uploadStatus = if (momentiaManager.state.value.isAutoUploadEnabled) UploadStatus.UPLOADING else UploadStatus.NOT_STARTED
                )

                _photos.update { list ->
                    list.map {
                        if (it.fileName == baseName || it.fileName == editedPhoto.fileName) editedPhoto else it
                    }
                }
                if (_latestPhoto.value?.fileName == baseName) {
                    _latestPhoto.value = editedPhoto
                }
                addLog("✅ Editada guardada en cel y encolada a Momentia: ${editedPhoto.fileName}")
            } else {
                addLog("❌ Error guardando editada de $baseName: ${editRes.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            addLog("❌ Excepción finalizando foto pendiente: ${e.message}")
        }
    }

    private fun handleOriginalCaptured(
        origData: SavedOriginalResult,
        eventName: String,
        key: String
    ) {
        downloadedOriginalKeys.add(key)
        prefs.edit().putStringSet("downloaded_keys", HashSet(downloadedOriginalKeys)).apply()
        vibrateShort()

        val isMomentiaAuto = momentiaManager.state.value.isAutoUploadEnabled
        val isLumepicAuto = lumepicManager.state.value.isAutoUploadEnabled
        val isAnyAutoUpload = isMomentiaAuto || isLumepicAuto

        val photo = origData.photo.copy(
            status = DownloadStatus.COMPLETED,
            uploadStatus = if (isAnyAutoUpload) UploadStatus.UPLOADING else UploadStatus.NOT_STARTED
        )

        _latestPhoto.value = photo
        _latestOriginalPhoto.value = photo
        _photos.update { list ->
            listOf(photo) + list.filter { it.fileName != origData.fileName }
        }

        val mb = origData.photo.fileSizeBytes / (1024f * 1024f)
        val destinations = buildList {
            if (isMomentiaAuto) add("Momentia")
            if (isLumepicAuto) add("Lumepic")
        }
        val destMsg = if (destinations.isNotEmpty()) " · Subiendo a ${destinations.joinToString(" + ")}" else ""
        val msg = "⚡ Foto recibida: ${origData.fileName} (%.2f MB)$destMsg".format(mb)
        _syncStatusMessage.value = msg
        addLog(msg)

        if (isMomentiaAuto) {
            scope.launch(Dispatchers.IO) {
                addLog("☁️ Subiendo ${origData.fileName} a Momentia...")
                momentiaManager.enqueuePhotoUpload(origData.fileName, origData.originalBytes)
            }
        }

        if (isLumepicAuto) {
            scope.launch(Dispatchers.IO) {
                addLog("☁️ Subiendo ${origData.fileName} a Lumepic...")
                lumepicManager.enqueuePhotoUpload(origData.fileName, origData.originalBytes)
            }
        }

        // Subir copia en blanco y negro si el toggle está activo (original + B&N)
        if (_isBwCopyEnabled.value) {
            scope.launch(Dispatchers.IO) {
                try {
                    addLog("🖤 Generando copia B&N de ${origData.fileName}...")
                    val bwRes = storageManager.saveBwCopy(
                        originalBytes = origData.originalBytes,
                        baseFileName = origData.fileName,
                        rotationDegrees = origData.rotationDegrees,
                        eventName = eventName
                    )
                    if (bwRes.isSuccess) {
                        val savedBw = bwRes.getOrThrow()
                        val bwPhoto = savedBw.photo.copy(
                            status = DownloadStatus.COMPLETED,
                            uploadStatus = if (isAnyAutoUpload) UploadStatus.UPLOADING else UploadStatus.NOT_STARTED
                        )
                        _photos.update { list ->
                            val withoutBoth = list.filter { it.fileName != photo.fileName && it.fileName != bwPhoto.fileName }
                            listOf(photo, bwPhoto) + withoutBoth
                        }
                        addLog("✅ Copia B&N guardada: ${bwPhoto.fileName}")
                        if (isMomentiaAuto) {
                            addLog("☁️ Subiendo copia B&N ${bwPhoto.fileName} a Momentia...")
                            momentiaManager.enqueuePhotoUpload(bwPhoto.fileName, savedBw.bwBytes)
                        }
                        if (isLumepicAuto) {
                            addLog("☁️ Subiendo copia B&N ${bwPhoto.fileName} a Lumepic...")
                            lumepicManager.enqueuePhotoUpload(bwPhoto.fileName, savedBw.bwBytes)
                        }
                    } else {
                        addLog("⚠️ Error generando copia B&N: ${bwRes.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    addLog("⚠️ Error en proceso B&N: ${e.message}")
                }
            }
        }
    }

    private fun vibrateShort() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(120)
            }
        } catch (_: Exception) {}
    }

    private fun addLog(message: String) {
        val entry = "[${timeFormat.format(Date())}] $message"
        Log.i(TAG, entry)
        _logs.value = (listOf(entry) + _logs.value).take(40)
    }

    @Synchronized
    fun startUsbTethering() {
        if (usbSessionJob?.isActive == true) return

        usbSessionJob = scope.launch {
            _connectionState.value = CameraConnectionState.Listening("Esperando conexión por Cable USB")

            while (isActive) {
                try {
                    val usbDevice = usbMtpHelper.findSonyCameraDevice()
                    if (usbDevice != null) {
                        if (!usbMtpHelper.hasPermission(usbDevice)) {
                            usbMtpHelper.checkConnectedCamera()
                            _syncStatusMessage.value = "⚠️ Aceptá el permiso USB en pantalla para conectar con la cámara."
                            delay(2000)
                            continue
                        }

                        _connectionState.value = CameraConnectionState.Connected(
                            "ILCE-6400 por USB",
                            "USB Directo"
                        )

                        usbMtpHelper.runUsbCameraSession(
                            device = usbDevice,
                            downloadedKeys = downloadedOriginalKeys,
                            eventNameProvider = { _activeEventName.value },
                            presetConfigProvider = { _presetConfig.value },
                            recentOriginalUris = recentOriginalUris,
                            storageManager = storageManager,
                            onStatusUpdate = { msg ->
                                _syncStatusMessage.value = msg
                            },
                            onProgress = { fileName, progress ->
                                _connectionState.value = CameraConnectionState.Downloading(fileName, progress)
                            },
                            onPhotoSaved = { orig, edit, k ->
                                // Legacy fallback
                            },
                            onOriginalCaptured = { origData, eventName, key ->
                                handleOriginalCaptured(origData, eventName, key)
                            },
                            onLog = { logMsg ->
                                addLog(logMsg)
                            }
                        )
                    } else {
                        if (_connectionState.value !is CameraConnectionState.Listening) {
                            _connectionState.value = CameraConnectionState.Listening("Esperando conexión por Cable USB")
                        }
                    }
                    delay(1500)
                } catch (e: Exception) {
                    if (isActive) {
                        delay(2000)
                    }
                }
            }
        }
    }

    fun setActiveEventName(newName: String) {
        val clean = PhotoStorageManager.sanitizeFolderName(newName).ifBlank { defaultEventName }
        _activeEventName.value = clean
        val updatedRecent = (listOf(clean) + _recentEvents.value).distinct().take(10)
        _recentEvents.value = updatedRecent
        prefs.edit()
            .putString("active_event_name", clean)
            .putStringSet("recent_events", updatedRecent.toSet())
            .apply()
        addLog("📁 Evento activo: Pictures/${PhotoStorageManager.BASE_FOLDER}/$clean (Originales / Editadas)")
        _syncStatusMessage.value = "📁 Guardando fotos en: ${PhotoStorageManager.BASE_FOLDER}/$clean"
    }

    fun clearDownloadedHistory() {
        downloadedOriginalKeys.clear()
        prefs.edit().remove("downloaded_keys").apply()
        addLog("🔄 Historial de fotos reiniciado")
        _syncStatusMessage.value = "🔄 Historial de fotos reiniciado."
    }

    suspend fun deleteEventPhotos(eventName: String): Int {
        val deleted = storageManager.deleteEventPhotos(eventName)
        if (deleted > 0) {
            _photos.value = emptyList()
            _latestPhoto.value = null
            _latestOriginalPhoto.value = null
            _heroPreviewBitmap.value = null
            activeBasePreviewBitmap = null
            pendingShot = null
            _pendingCountdownSeconds.value = null
            addLog("🗑️ Se eliminaron $deleted fotos del evento $eventName del celular")
            _syncStatusMessage.value = "🗑️ Se eliminaron $deleted fotos del evento $eventName."
        }
        return deleted
    }

    fun cleanUp() {
        finalizePendingPhotoNow()
        usbSessionJob?.cancel()
        usbSessionJob = null
        usbMtpHelper.stopMonitoring()
        ftpServer.stop()
    }
}
