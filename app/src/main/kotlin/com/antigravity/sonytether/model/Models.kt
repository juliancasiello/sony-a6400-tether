package com.antigravity.sonytether.model

import android.net.Uri

/**
 * Representa una foto transferida desde la Sony a6400
 */
data class TetheredPhoto(
    val id: String,
    val fileName: String,
    val remoteUrl: String,
    val localUri: Uri? = null,
    val fileSizeBytes: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: DownloadStatus = DownloadStatus.PENDING,
    val downloadDurationMs: Long = 0,
    // Para la Fase 2 (subida a servidor)
    val uploadStatus: UploadStatus = UploadStatus.NOT_STARTED
)

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

enum class UploadStatus {
    NOT_STARTED,
    QUEUED,
    UPLOADING,
    COMPLETED,
    FAILED
}

/**
 * Estados de conexión con la cámara
 */
sealed class CameraConnectionState {
    object Disconnected : CameraConnectionState()
    object CheckingWifi : CameraConnectionState()
    data class Connecting(val message: String) : CameraConnectionState()
    data class Connected(val cameraModel: String, val endpoint: String) : CameraConnectionState()
    data class Listening(val message: String = "Esperando disparos...") : CameraConnectionState()
    data class Downloading(val fileName: String, val progress: Float) : CameraConnectionState()
    data class Error(val errorMessage: String) : CameraConnectionState()
}

/**
 * Opciones de calidad de imagen para la transferencia
 */
enum class TransferQuality(val apiValue: String, val label: String) {
    ORIGINAL("Original", "Original (Máxima resolución)"),
    TWO_MEGAPIXEL("2M", "2 MP (Rápido / Vista previa)")
}

/**
 * Información de estado de la cámara
 */
data class CameraStatusInfo(
    val model: String = "Sony a6400",
    val batteryPercentage: String? = null,
    val isCharging: Boolean = false,
    val cameraStatus: String = "Inactivo",
    val storageAvailable: Boolean = true
)
