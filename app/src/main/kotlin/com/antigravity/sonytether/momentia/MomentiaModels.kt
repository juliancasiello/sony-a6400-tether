package com.antigravity.sonytether.momentia

data class MomentiaUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String
)

data class MomentiaEvent(
    val id: String,
    val name: String,
    val date: String? = null
)

data class MomentiaPresignedUrl(
    val fileId: String,
    val filename: String,
    val uploadUrl: String?,
    val s3Key: String?,
    val alreadyUploaded: Boolean,
    val clientHash: String
)

data class MomentiaSaleOrder(
    val orderId: String,
    val eventId: String? = null,
    val eventName: String? = null,
    val createdAt: String? = null,
    val totalAmount: Double = 0.0,
    val photographerEarnings: Double = 0.0,
    val photosCount: Int = 1,
    val thumbnailUrl: String? = null,
    val currency: String = "ARS",
    val status: String = "PAID"
)

data class MomentiaSalesSummary(
    val totalOrders: Int = 0,
    val totalPhotos: Int = 0,
    val totalEarnings: Double = 0.0,
    val currency: String = "ARS",
    val recentOrders: List<MomentiaSaleOrder> = emptyList(),
    val lastCheckedTimestamp: Long = 0L,
    val isMonitoring: Boolean = true
)

data class MomentiaUploadState(
    val isLoggedIn: Boolean = false,
    val token: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val selectedEventId: String? = null,
    val selectedEventName: String? = null,
    val availableEvents: List<MomentiaEvent> = emptyList(),
    val isAutoUploadEnabled: Boolean = false,
    val isUploading: Boolean = false,
    val currentUploadingFile: String? = null,
    val uploadProgress: Float = 0f,
    val uploadedCount: Int = 0,
    val failedCount: Int = 0,
    val queueSize: Int = 0,
    val lastError: String? = null,
    val salesSummary: MomentiaSalesSummary = MomentiaSalesSummary(),
    val latestSaleAlert: MomentiaSaleOrder? = null
)
