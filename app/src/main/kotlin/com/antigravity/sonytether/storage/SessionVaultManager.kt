package com.antigravity.sonytether.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File

private const val TAG = "SessionVaultManager"
private const val VAULT_FOLDER = "SonyAlpha_Tether"
private const val VAULT_FILE_NAME = ".session_vault.json"

data class SessionVaultData(
    val momentiaEmail: String? = null,
    val momentiaPassword: String? = null,
    val momentiaToken: String? = null,
    val momentiaEventId: String? = null,
    val momentiaEventName: String? = null,
    val lumepicCookies: String? = null,
    val lumepicAlbumId: String? = null,
    val lumepicAlbumName: String? = null,
    val activeEventName: String? = null,
    val selectedCameraId: String? = null
)

object SessionVaultManager {

    private fun getVaultFiles(context: Context): List<File> {
        val list = mutableListOf<File>()
        try {
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val tetherFolder = File(publicDir, VAULT_FOLDER)
            if (!tetherFolder.exists()) {
                tetherFolder.mkdirs()
            }
            list.add(File(tetherFolder, VAULT_FILE_NAME))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo acceder a Pictures para el vault: ${e.message}")
        }
        try {
            list.add(File(context.filesDir, VAULT_FILE_NAME))
        } catch (_: Exception) {}
        return list
    }

    fun saveVault(context: Context, data: SessionVaultData) {
        try {
            val json = JSONObject().apply {
                put("momentiaEmail", data.momentiaEmail)
                put("momentiaPassword", data.momentiaPassword)
                put("momentiaToken", data.momentiaToken)
                put("momentiaEventId", data.momentiaEventId)
                put("momentiaEventName", data.momentiaEventName)
                put("lumepicCookies", data.lumepicCookies)
                put("lumepicAlbumId", data.lumepicAlbumId)
                put("lumepicAlbumName", data.lumepicAlbumName)
                put("activeEventName", data.activeEventName)
                put("selectedCameraId", data.selectedCameraId)
                put("updatedAt", System.currentTimeMillis())
            }.toString(2)

            for (file in getVaultFiles(context)) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(json, Charsets.UTF_8)
                    Log.d(TAG, "Bóveda de sesión guardada en: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.w(TAG, "Error escribiendo vault en ${file.absolutePath}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serializando bóveda de sesión: ${e.message}", e)
        }
    }

    fun loadVault(context: Context): SessionVaultData? {
        for (file in getVaultFiles(context)) {
            if (file.exists() && file.length() > 0) {
                try {
                    val raw = file.readText(Charsets.UTF_8)
                    val json = JSONObject(raw)
                    return SessionVaultData(
                        momentiaEmail = json.optString("momentiaEmail").takeIf { it.isNotBlank() },
                        momentiaPassword = json.optString("momentiaPassword").takeIf { it.isNotBlank() },
                        momentiaToken = json.optString("momentiaToken").takeIf { it.isNotBlank() },
                        momentiaEventId = json.optString("momentiaEventId").takeIf { it.isNotBlank() },
                        momentiaEventName = json.optString("momentiaEventName").takeIf { it.isNotBlank() },
                        lumepicCookies = json.optString("lumepicCookies").takeIf { it.isNotBlank() },
                        lumepicAlbumId = json.optString("lumepicAlbumId").takeIf { it.isNotBlank() },
                        lumepicAlbumName = json.optString("lumepicAlbumName").takeIf { it.isNotBlank() },
                        activeEventName = json.optString("activeEventName").takeIf { it.isNotBlank() },
                        selectedCameraId = json.optString("selectedCameraId").takeIf { it.isNotBlank() }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error leyendo vault desde ${file.absolutePath}: ${e.message}")
                }
            }
        }
        return null
    }
}
