package com.antigravity.sonytether.lumepic

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

private const val TAG = "LumepicManager"
private const val PREFS_NAME = "lumepic_prefs"
private const val KEY_COOKIES = "cookies"
private const val KEY_NAME = "user_name"
private const val KEY_EMAIL = "user_email"
private const val KEY_ALBUM_ID = "selected_album_id"
private const val KEY_ALBUM_NAME = "selected_album_name"
private const val KEY_AUTO_UPLOAD = "auto_upload_enabled"

private data class LumepicUploadItem(
    val fileName: String,
    val bytes: ByteArray,
    val timestampMs: Long
)

class LumepicManager(
    private val context: Context,
    private val apiClient: LumepicApiClient = LumepicApiClient()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LumepicUploadState())
    val state: StateFlow<LumepicUploadState> = _state.asStateFlow()

    private val uploadQueue = Channel<LumepicUploadItem>(Channel.UNLIMITED)
    private val failedItems = java.util.Collections.synchronizedList(mutableListOf<LumepicUploadItem>())

    var isWifiOnlyProvider: () -> Boolean = { false }
    var isWifiConnectedProvider: () -> Boolean = { true }

    var onPhotoUploadFinished: ((fileName: String, success: Boolean) -> Unit)? = null

    init {
        loadSavedState()
        startUploadWorker()
    }

    private fun loadSavedState() {
        val cookies = prefs.getString(KEY_COOKIES, null)
        val name = prefs.getString(KEY_NAME, null)
        val email = prefs.getString(KEY_EMAIL, null)
        val albumId = prefs.getString(KEY_ALBUM_ID, null)
        val albumName = prefs.getString(KEY_ALBUM_NAME, null)
        val autoUpload = prefs.getBoolean(KEY_AUTO_UPLOAD, false)

        val isLoggedIn = !cookies.isNullOrBlank()

        _state.update {
            it.copy(
                isLoggedIn = isLoggedIn,
                cookies = cookies,
                userName = name,
                userEmail = email,
                selectedAlbumId = albumId,
                selectedAlbumName = albumName,
                isAutoUploadEnabled = autoUpload
            )
        }

        if (isLoggedIn && !cookies.isNullOrBlank()) {
            refreshAlbums()
        }
    }

    fun setCookies(cookies: String, userName: String? = null) {
        prefs.edit()
            .putString(KEY_COOKIES, cookies)
            .apply()

        var resolvedName = userName
        var resolvedEmail = _state.value.userEmail

        val userJson = LumepicApiClient.extractCookie(cookies, "lumepic-user")
        if (!userJson.isNullOrBlank()) {
            try {
                val obj = org.json.JSONObject(userJson)
                val email = obj.optString("email", null)
                val name = obj.optString("name", obj.optString("firstName", null))
                if (!email.isNullOrBlank()) {
                    resolvedEmail = email
                    prefs.edit().putString(KEY_EMAIL, email).apply()
                }
                if (!name.isNullOrBlank()) {
                    resolvedName = name
                    prefs.edit().putString(KEY_NAME, name).apply()
                }
            } catch (_: Exception) {}
        }

        if (!resolvedName.isNullOrBlank()) {
            prefs.edit().putString(KEY_NAME, resolvedName).apply()
        }

        _state.update {
            it.copy(
                isLoggedIn = true,
                cookies = cookies,
                userName = resolvedName ?: it.userName,
                userEmail = resolvedEmail ?: it.userEmail,
                lastError = null
            )
        }

        refreshAlbums()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _state.update { LumepicUploadState() }
    }

    fun refreshAlbums() {
        val cookies = _state.value.cookies ?: return
        scope.launch {
            val res = apiClient.getAlbums(cookies)
            if (res.isSuccess) {
                val albums = res.getOrThrow()
                val currentSelectedId = _state.value.selectedAlbumId
                val matched = albums.firstOrNull { it.id == currentSelectedId }
                val selectedName = matched?.description ?: _state.value.selectedAlbumName

                _state.update {
                    it.copy(
                        isLoggedIn = true,
                        availableAlbums = albums,
                        selectedAlbumName = selectedName,
                        lastError = null
                    )
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Error al refrescar álbumes"
                Log.w(TAG, "No se pudieron refrescar álbumes de Lumepic: $err")
                _state.update { it.copy(lastError = err) }
            }
        }
    }

    fun selectAlbum(album: LumepicAlbum) {
        prefs.edit()
            .putString(KEY_ALBUM_ID, album.id)
            .putString(KEY_ALBUM_NAME, album.description)
            .apply()

        _state.update {
            it.copy(
                selectedAlbumId = album.id,
                selectedAlbumName = album.description
            )
        }
    }

    fun setAutoUploadEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPLOAD, enabled).apply()
        _state.update { it.copy(isAutoUploadEnabled = enabled) }
    }

    /**
     * Encola una foto para subirse a Lumepic en segundo plano.
     */
    fun enqueuePhotoUpload(fileName: String, imageBytes: ByteArray) {
        if (imageBytes.isEmpty()) {
            Log.w(TAG, "Subida ignorada: archivo vacío ($fileName)")
            return
        }

        val currentState = _state.value
        if (!currentState.isLoggedIn || currentState.selectedAlbumId.isNullOrBlank() || !currentState.isAutoUploadEnabled) {
            Log.d(TAG, "Subida a Lumepic omitida (logueado=${currentState.isLoggedIn}, album=${currentState.selectedAlbumId}, auto=${currentState.isAutoUploadEnabled})")
            return
        }

        _state.update { it.copy(queueSize = it.queueSize + 1) }
        uploadQueue.trySend(LumepicUploadItem(fileName, imageBytes, System.currentTimeMillis()))
    }

    /**
     * Reintenta las fotos fallidas de Lumepic tras cortes de red.
     */
    fun retryFailedUploads() {
        val toRetry = synchronized(failedItems) {
            val list = failedItems.toList()
            failedItems.clear()
            list
        }
        if (toRetry.isEmpty()) return

        _state.update { it.copy(failedCount = 0, lastError = null) }
        for (item in toRetry) {
            enqueuePhotoUpload(item.fileName, item.bytes)
        }
    }

    private fun startUploadWorker() {
        scope.launch {
            for (item in uploadQueue) {
                while (isWifiOnlyProvider() && !isWifiConnectedProvider()) {
                    delay(4000)
                }

                val fileName = item.fileName
                val bytes = item.bytes

                val currentState = _state.value
                val cookies = currentState.cookies
                val albumId = currentState.selectedAlbumId

                if (cookies.isNullOrBlank() || albumId.isNullOrBlank()) {
                    _state.update { it.copy(queueSize = maxOf(0, it.queueSize - 1)) }
                    continue
                }

                _state.update {
                    it.copy(
                        isUploading = true,
                        currentUploadingFile = fileName,
                        uploadProgress = 0.1f
                    )
                }

                var success = false
                var attempts = 0
                val maxAttempts = 3

                while (!success && attempts < maxAttempts) {
                    attempts++
                    try {
                        // 1. Registrar fotografía en Lumepic (batch-create)
                        _state.update { it.copy(uploadProgress = 0.2f) }
                        val createRes = apiClient.batchCreatePhotograph(
                            cookies = cookies,
                            albumId = albumId,
                            fileName = fileName,
                            imageBytes = bytes
                        )

                        if (createRes.isFailure) {
                            throw createRes.exceptionOrNull() ?: Exception("Error en batch-create de Lumepic")
                        }

                        val photographId = createRes.getOrThrow()

                        // 2. Obtener URL prefirmada de S3 (batch-presign)
                        _state.update { it.copy(uploadProgress = 0.4f) }
                        val presignRes = apiClient.batchPresignPhotograph(
                            cookies = cookies,
                            albumId = albumId,
                            photographId = photographId
                        )

                        if (presignRes.isFailure) {
                            throw presignRes.exceptionOrNull() ?: Exception("Error en batch-presign de Lumepic")
                        }

                        val uploadUrl = presignRes.getOrThrow()

                        // 3. Subida binaria directa a S3 de Lumepic
                        val uploadRes = apiClient.uploadToS3(uploadUrl, bytes) { p ->
                            _state.update { it.copy(uploadProgress = 0.4f + p * 0.55f) }
                        }

                        if (uploadRes.isFailure) {
                            throw uploadRes.exceptionOrNull() ?: Exception("Error en subida binaria a S3")
                        }

                        Log.i(TAG, "Foto $fileName subida exitosamente a Lumepic (Álbum $albumId, Intento $attempts)")
                        success = true
                        _state.update {
                            it.copy(
                                isUploading = false,
                                currentUploadingFile = null,
                                uploadProgress = 1.0f,
                                uploadedCount = it.uploadedCount + 1,
                                queueSize = maxOf(0, it.queueSize - 1),
                                lastError = null
                            )
                        }

                        onPhotoUploadFinished?.invoke(fileName, true)

                        // Pausa de cortesía para respetar Rate Limits de Lumepic
                        delay(600)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Intento $attempts/$maxAttempts fallido para $fileName en Lumepic: ${t.message}")
                        if (attempts < maxAttempts) {
                            delay(1500L * attempts)
                        } else {
                            Log.e(TAG, "Subida fallida definitiva de $fileName a Lumepic tras $maxAttempts intentos", t)
                            failedItems.add(item)
                            _state.update {
                                it.copy(
                                    isUploading = false,
                                    currentUploadingFile = null,
                                    failedCount = it.failedCount + 1,
                                    queueSize = maxOf(0, it.queueSize - 1),
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
    }
}
