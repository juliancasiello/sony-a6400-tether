package com.antigravity.sonytether.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.antigravity.sonytether.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val isNewer: Boolean
)

data class AppUpdateState(
    val isChecking: Boolean = false,
    val availableUpdate: UpdateInfo? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedApkFile: File? = null,
    val error: String? = null,
    val checkMessage: String? = null
)

class AppUpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(AppUpdateState())
    val state = _state.asStateFlow()

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    suspend fun checkForUpdates(silent: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!silent) {
            _state.value = _state.value.copy(isChecking = true, error = null, checkMessage = null)
        }
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/juliancasiello/sony-a6400-tether/releases/latest")
                .header("User-Agent", "SonyTether-AndroidApp")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                if (!silent) {
                    _state.value = _state.value.copy(
                        isChecking = false,
                        error = "Error comprobando versión (${response.code})"
                    )
                }
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "")

            // Buscar asset de APK
            var downloadUrl: String? = null
            var fileName = "SonyA6400_Tether.apk"
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        fileName = assetName
                        downloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                        break
                    }
                }
            }

            if (downloadUrl.isNullOrBlank()) {
                if (!silent) {
                    _state.value = _state.value.copy(
                        isChecking = false,
                        checkMessage = "No se encontró archivo APK en la última versión de GitHub."
                    )
                }
                return@withContext null
            }

            val isNewer = compareVersions(tagName, currentVersionName) > 0
            val info = UpdateInfo(
                tagName = tagName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                fileName = fileName,
                isNewer = isNewer
            )

            if (isNewer) {
                _state.value = _state.value.copy(
                    isChecking = false,
                    availableUpdate = info,
                    error = null
                )
            } else {
                _state.value = _state.value.copy(
                    isChecking = false,
                    availableUpdate = null,
                    checkMessage = if (!silent) "Tenés la última versión instalada ($currentVersionName)" else null
                )
            }
            return@withContext if (isNewer) info else null
        } catch (e: Exception) {
            if (!silent) {
                _state.value = _state.value.copy(
                    isChecking = false,
                    error = "No se pudo conectar a GitHub: ${e.localizedMessage}"
                )
            }
            return@withContext null
        }
    }

    suspend fun downloadAndInstall(updateInfo: UpdateInfo) = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(isDownloading = true, downloadProgress = 0f, error = null)
        try {
            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "SonyTether-AndroidApp")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _state.value = _state.value.copy(
                    isDownloading = false,
                    error = "Error al descargar actualización (${response.code})"
                )
                return@withContext
            }

            val responseBody = response.body ?: throw IllegalStateException("Cuerpo de respuesta vacío")
            val totalBytes = responseBody.contentLength()

            val cacheDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(cacheDir, updateInfo.fileName)
            if (apkFile.exists()) apkFile.delete()

            responseBody.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesCopied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val progress = bytesCopied.toFloat() / totalBytes.toFloat()
                            _state.value = _state.value.copy(downloadProgress = progress)
                        }
                    }
                }
            }

            _state.value = _state.value.copy(
                isDownloading = false,
                downloadProgress = 1f,
                downloadedApkFile = apkFile
            )

            // Iniciar instalación en el hilo principal
            withContext(Dispatchers.Main) {
                triggerApkInstall(context, apkFile)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isDownloading = false,
                error = "Fallo al descargar APK: ${e.localizedMessage}"
            )
        }
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(availableUpdate = null, checkMessage = null, error = null)
    }

    companion object {
        fun triggerApkInstall(context: Context, apkFile: File) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun compareVersions(remoteVersion: String, localVersion: String): Int {
            val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
            val cleanLocal = localVersion.trim().removePrefix("v").removePrefix("V")

            val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
            val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) {
                    return r.compareTo(l)
                }
            }
            return 0
        }
    }
}
