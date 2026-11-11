package com.antigravity.sonytether.momentia

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "MomentiaManager"
private const val PREFS_NAME = "momentia_prefs"
private const val KEY_TOKEN = "token"
private const val KEY_EMAIL = "email"
private const val KEY_NAME = "name"
private const val KEY_EVENT_ID = "selected_event_id"
private const val KEY_EVENT_NAME = "selected_event_name"
private const val KEY_AUTO_UPLOAD = "auto_upload_enabled"

private data class UploadItem(
    val fileName: String,
    val bytes: ByteArray,
    val timestampMs: Long
)

class MomentiaManager(
    private val context: Context,
    private val apiClient: MomentiaApiClient = MomentiaApiClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(MomentiaUploadState())
    val state: StateFlow<MomentiaUploadState> = _state.asStateFlow()

    private val uploadQueue = Channel<UploadItem>(Channel.UNLIMITED)

    var onPhotoUploadFinished: ((fileName: String, success: Boolean) -> Unit)? = null

    init {
        loadSavedState()
        startUploadWorker()
    }

    private fun loadSavedState() {
        val token = prefs.getString(KEY_TOKEN, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val name = prefs.getString(KEY_NAME, null)
        val eventId = prefs.getString(KEY_EVENT_ID, null)
        val eventName = prefs.getString(KEY_EVENT_NAME, null)
        val autoUpload = prefs.getBoolean(KEY_AUTO_UPLOAD, false)

        val isLoggedIn = !token.isNullOrBlank()

        _state.update {
            it.copy(
                isLoggedIn = isLoggedIn,
                token = token,
                userEmail = email,
                userName = name,
                selectedEventId = eventId,
                selectedEventName = eventName,
                isAutoUploadEnabled = autoUpload
            )
        }

        if (isLoggedIn) {
            refreshEvents()
        }
    }

    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            val res = apiClient.login(email, pass)
            if (res.isSuccess) {
                val (token, user) = res.getOrThrow()
                prefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_EMAIL, user.email)
                    .putString(KEY_NAME, user.name)
                    .apply()

                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        token = token,
                        userEmail = user.email,
                        userName = user.name,
                        lastError = null
                    )
                }

                refreshEvents()
                onResult(true, null)
            } else {
                val msg = res.exceptionOrNull()?.message ?: "Error desconocido"
                _state.update { it.copy(lastError = msg) }
                onResult(false, msg)
            }
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _state.update {
            MomentiaUploadState()
        }
    }

    fun refreshEvents() {
        val token = _state.value.token ?: return
        scope.launch {
            val res = apiClient.getUploadEvents(token)
            if (res.isSuccess) {
                val events = res.getOrThrow()
                val currentSelectedId = _state.value.selectedEventId
                val matchedEvent = events.firstOrNull { it.id == currentSelectedId }
                val selectedName = matchedEvent?.name ?: _state.value.selectedEventName

                _state.update {
                    it.copy(
                        availableEvents = events,
                        selectedEventName = selectedName
                    )
                }
            } else {
                Log.w(TAG, "No se pudieron refrescar eventos de Momentia: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun selectEvent(event: MomentiaEvent) {
        prefs.edit()
            .putString(KEY_EVENT_ID, event.id)
            .putString(KEY_EVENT_NAME, event.name)
            .apply()

        _state.update {
            it.copy(
                selectedEventId = event.id,
                selectedEventName = event.name
            )
        }
    }

    fun setAutoUploadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPLOAD, enabled).apply()
        _state.update { it.copy(isAutoUploadEnabled = enabled) }
    }

    /**
     * Encola una foto editada para subirse a Momentia en segundo plano.
     */
    fun enqueuePhotoUpload(fileName: String, imageBytes: ByteArray) {
        if (imageBytes.isEmpty()) {
            Log.w(TAG, "Subida ignorada: archivo vacío ($fileName)")
            return
        }

        val currentState = _state.value
        if (!currentState.isLoggedIn || currentState.selectedEventId.isNullOrBlank() || !currentState.isAutoUploadEnabled) {
            Log.d(TAG, "Subida omitida (logueado=${currentState.isLoggedIn}, evento=${currentState.selectedEventId}, auto=${currentState.isAutoUploadEnabled})")
            return
        }

        uploadQueue.trySend(UploadItem(fileName, imageBytes, System.currentTimeMillis()))
    }

    private fun startUploadWorker() {
        scope.launch {
            for (item in uploadQueue) {
                val fileName = item.fileName
                val bytes = item.bytes
                val timestampMs = item.timestampMs

                val currentState = _state.value
                val token = currentState.token
                val eventId = currentState.selectedEventId

                if (token.isNullOrBlank() || eventId.isNullOrBlank()) {
                    continue
                }

                _state.update {
                    it.copy(
                        isUploading = true,
                        currentUploadingFile = fileName,
                        uploadProgress = 0.1f
                    )
                }

                try {
                    // 1. Pedir URL prefirmada
                    val presignedRes = apiClient.requestUploadUrl(
                        token = token,
                        eventId = eventId,
                        filename = fileName,
                        fileSizeBytes = bytes.size.toLong(),
                        timestampMs = timestampMs
                    )

                    if (presignedRes.isFailure) {
                        throw presignedRes.exceptionOrNull() ?: Exception("Error solicitando URL")
                    }

                    val presigned = presignedRes.getOrThrow()

                    if (presigned.alreadyUploaded) {
                        Log.i(TAG, "La foto $fileName ya estaba subida en Momentia.")
                        _state.update {
                            it.copy(
                                isUploading = false,
                                currentUploadingFile = null,
                                uploadedCount = it.uploadedCount + 1
                            )
                        }
                        onPhotoUploadFinished?.invoke(fileName, true)
                        delay(400)
                        continue
                    }

                    val s3Url = presigned.uploadUrl ?: throw Exception("URL de S3 no provista por el servidor")
                    val s3Key = presigned.s3Key ?: throw Exception("s3_key no provista por el servidor")

                    // 2. Subida binaria directa a AWS S3
                    _state.update { it.copy(uploadProgress = 0.5f) }
                    val uploadRes = apiClient.uploadToS3(s3Url, bytes) { p ->
                        _state.update { it.copy(uploadProgress = 0.5f + p * 0.4f) }
                    }

                    if (uploadRes.isFailure) {
                        throw uploadRes.exceptionOrNull() ?: Exception("Error al subir archivo a almacenamiento")
                    }

                    // 3. Confirmar subida en Momentia (idéntico al frontend web)
                    val confirmRes = apiClient.confirmUpload(
                        token = token,
                        eventId = eventId,
                        fileId = presigned.fileId,
                        s3Key = s3Key,
                        filename = fileName,
                        clientHash = presigned.clientHash
                    )

                    if (confirmRes.isFailure) {
                        throw confirmRes.exceptionOrNull() ?: Exception("Error confirmando subida")
                    }

                    Log.i(TAG, "Foto $fileName subida exitosamente a Momentia")
                    _state.update {
                        it.copy(
                            isUploading = false,
                            currentUploadingFile = null,
                            uploadProgress = 1.0f,
                            uploadedCount = it.uploadedCount + 1,
                            lastError = null
                        )
                    }
                    onPhotoUploadFinished?.invoke(fileName, true)

                    // Pausa de cortesía para no saturar conexiones ni disparar límites
                    delay(600)
                } catch (t: Throwable) {
                    Log.e(TAG, "Error en subida de $fileName a Momentia: ${t.message}", t)
                    _state.update {
                        it.copy(
                            isUploading = false,
                            currentUploadingFile = null,
                            failedCount = it.failedCount + 1,
                            lastError = t.message
                        )
                    }
                    onPhotoUploadFinished?.invoke(fileName, false)
                    delay(1000)
                }
            }
        }
    }
}
