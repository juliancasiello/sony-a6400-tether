package com.antigravity.sonytether.ui.tether

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.antigravity.sonytether.editor.PresetConfig
import com.antigravity.sonytether.engine.TetherEngine
import com.antigravity.sonytether.lumepic.LumepicAlbum
import com.antigravity.sonytether.lumepic.LumepicUploadState
import com.antigravity.sonytether.model.CameraConnectionState
import com.antigravity.sonytether.model.TetheredPhoto
import com.antigravity.sonytether.momentia.MomentiaEvent
import com.antigravity.sonytether.momentia.MomentiaUploadState
import com.antigravity.sonytether.updater.AppUpdateState
import com.antigravity.sonytether.updater.UpdateInfo
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TetherViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = TetherEngine.getInstance(application.applicationContext)

    val connectionState: StateFlow<CameraConnectionState> = engine.connectionState
    val photos: StateFlow<List<TetheredPhoto>> = engine.photos
    val latestPhoto: StateFlow<TetheredPhoto?> = engine.latestPhoto
    val latestOriginalPhoto: StateFlow<TetheredPhoto?> = engine.latestOriginalPhoto
    val presetConfig: StateFlow<PresetConfig> = engine.presetConfig
    val logs: StateFlow<List<String>> = engine.logs
    val syncStatusMessage: StateFlow<String?> = engine.syncStatusMessage
    val activeEventName: StateFlow<String> = engine.activeEventName
    val recentEvents: StateFlow<List<String>> = engine.recentEvents
    val isUsbCameraConnected: StateFlow<Boolean> = engine.usbMtpHelper.isUsbCameraConnected
    val usbModeDescription: StateFlow<String> = engine.usbMtpHelper.usbModeDescription
    val momentiaState: StateFlow<MomentiaUploadState> = engine.momentiaManager.state
    val lumepicState: StateFlow<LumepicUploadState> = engine.lumepicManager.state
    val heroPreviewBitmap: StateFlow<android.graphics.Bitmap?> = engine.heroPreviewBitmap
    val pendingCountdownSeconds: StateFlow<Int?> = engine.pendingCountdownSeconds
    val garminHeartbeatMinutes: StateFlow<Int> = engine.garminHeartbeatMinutes
    val isComparingOriginal: StateFlow<Boolean> = engine.isComparingOriginal
    val isBwCopyEnabled: StateFlow<Boolean> = engine.isBwCopyEnabled
    val isWifiOnlyUpload: StateFlow<Boolean> = engine.isWifiOnlyUpload
    val updateState: StateFlow<AppUpdateState> = engine.updateManager.state
    val currentVersionName: String get() = engine.updateManager.currentVersionName

    fun setBwCopyEnabled(enabled: Boolean) {
        engine.setBwCopyEnabled(enabled)
    }

    fun toggleBwCopy() {
        engine.toggleBwCopy()
    }

    fun setWifiOnlyUpload(enabled: Boolean) {
        engine.setWifiOnlyUpload(enabled)
    }

    fun setComparingOriginal(comparing: Boolean) {
        engine.setComparingOriginal(comparing)
    }

    fun preparePreviewForEditing() {
        val photo = _selectedPhoto.value ?: latestPhoto.value
        if (photo != null) {
            engine.setActivePreviewPhoto(photo)
        }
    }

    fun setGarminHeartbeatMinutes(minutes: Int) {
        engine.setGarminHeartbeatMinutes(minutes)
    }

    fun finalizePendingPhotoNow() {
        engine.finalizePendingPhotoNow()
    }

    private val _selectedPhoto = MutableStateFlow<TetheredPhoto?>(null)
    val selectedPhoto: StateFlow<TetheredPhoto?> = _selectedPhoto.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showPresetEditor = MutableStateFlow(false)
    val showPresetEditor: StateFlow<Boolean> = _showPresetEditor.asStateFlow()

    fun updatePreset(config: PresetConfig) {
        engine.updatePreset(config)
    }

    fun togglePresetEditor(show: Boolean) {
        _showPresetEditor.value = show
    }

    private val viewModelScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    fun deleteEventPhotos(eventName: String) {
        viewModelScope.launch {
            engine.deleteEventPhotos(eventName)
        }
    }

    fun clearDownloadedHistory() {
        engine.clearDownloadedHistory()
    }

    fun setActiveEventName(eventName: String) {
        engine.setActiveEventName(eventName)
    }

    fun selectPhoto(photo: TetheredPhoto?) {
        _selectedPhoto.value = photo
        engine.setActivePreviewPhoto(photo ?: latestPhoto.value)
    }

    fun toggleSettings(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun loginMomentia(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        engine.momentiaManager.login(email, pass, onResult)
    }

    fun logoutMomentia() {
        engine.momentiaManager.logout()
    }

    fun selectMomentiaEvent(event: MomentiaEvent) {
        engine.momentiaManager.selectEvent(event)
    }

    fun setMomentiaAutoUpload(enabled: Boolean) {
        engine.momentiaManager.setAutoUploadEnabled(enabled)
    }

    fun refreshMomentiaEvents() {
        engine.momentiaManager.refreshEvents()
    }

    fun retryMomentiaFailedUploads() {
        engine.momentiaManager.retryFailedUploads()
    }

    // Métodos de Lumepic
    fun setLumepicCookies(cookies: String, userName: String? = null) {
        engine.lumepicManager.setCookies(cookies, userName)
    }

    fun selectLumepicAlbum(album: LumepicAlbum) {
        engine.lumepicManager.selectAlbum(album)
    }

    fun setLumepicAutoUpload(enabled: Boolean) {
        engine.lumepicManager.setAutoUploadEnabled(enabled)
    }

    fun refreshLumepicAlbums() {
        engine.lumepicManager.refreshAlbums()
    }

    fun retryLumepicFailedUploads() {
        engine.lumepicManager.retryFailedUploads()
    }

    fun logoutLumepic() {
        engine.lumepicManager.logout()
    }

    // Modo de cierre total de la app y servicio
    fun shutdownAndExit(context: android.content.Context) {
        engine.shutdownAndExit(context)
    }

    // Actualizaciones automáticas desde GitHub Releases
    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            engine.updateManager.checkForUpdates(silent)
        }
    }

    fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        viewModelScope.launch {
            engine.updateManager.downloadAndInstall(updateInfo)
        }
    }

    fun dismissUpdate() {
        engine.updateManager.dismissUpdate()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
    }
}
