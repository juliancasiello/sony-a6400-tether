package com.antigravity.sonytether.lumepic

data class LumepicAlbum(
    val id: String,
    val description: String,
    val takenDate: String? = null,
    val status: String = "PUBLISHED",
    val coverPhotographSrc: String? = null,
    val photographCount: Int = 0,
    val spotName: String? = null
) {
    val name: String get() = description
    val date: String? get() = takenDate
    val photosCount: Int get() = photographCount
}

data class LumepicExtraInfo(
    val key: String,
    val value: String
)

data class LumepicUploadState(
    val isLoggedIn: Boolean = false,
    val cookies: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val selectedAlbumId: String? = null,
    val selectedAlbumName: String? = null,
    val availableAlbums: List<LumepicAlbum> = emptyList(),
    val isAutoUploadEnabled: Boolean = false,
    val isUploading: Boolean = false,
    val currentUploadingFile: String? = null,
    val uploadProgress: Float = 0f,
    val uploadedCount: Int = 0,
    val failedCount: Int = 0,
    val queueSize: Int = 0,
    val lastError: String? = null
)
