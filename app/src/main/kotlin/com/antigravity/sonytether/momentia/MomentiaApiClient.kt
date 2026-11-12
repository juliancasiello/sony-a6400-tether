package com.antigravity.sonytether.momentia

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "MomentiaApiClient"
private const val BASE_URL = "https://moment-ia.shop/api"
private const val ORIGIN_URL = "https://moment-ia.shop"
private const val REFERER_URL = "https://moment-ia.shop/photographer/dashboard?section=upload"
private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

class MomentiaApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jpegMediaType = "image/jpeg".toMediaType()

    private fun Request.Builder.withStandardBrowserHeaders(token: String? = null): Request.Builder {
        header("User-Agent", BROWSER_USER_AGENT)
        header("Accept", "application/json, text/plain, */*")
        header("Origin", ORIGIN_URL)
        header("Referer", REFERER_URL)
        header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
        header("Sec-Ch-Ua-Mobile", "?0")
        header("Sec-Ch-Ua-Platform", "\"Windows\"")
        header("Sec-Fetch-Dest", "empty")
        header("Sec-Fetch-Mode", "cors")
        header("Sec-Fetch-Site", "same-origin")
        if (!token.isNullOrBlank()) {
            header("Authorization", "Bearer $token")
        }
        return this
    }

    suspend fun login(email: String, pass: String): Result<Pair<String, MomentiaUser>> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("email", email.trim())
                put("password", pass)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/auth/login")
                .post(jsonBody.toRequestBody(jsonMediaType))
                .withStandardBrowserHeaders()
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errDetail = try {
                    JSONObject(respStr).optString("detail", "Error HTTP ${response.code}")
                } catch (_: Throwable) {
                    "Error HTTP ${response.code}"
                }
                return@withContext Result.failure(Exception(errDetail))
            }

            val respJson = JSONObject(respStr)
            val token = respJson.optString("token")
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("No se recibió token de autenticación"))
            }

            val userObj = respJson.optJSONObject("user")
            val user = MomentiaUser(
                id = userObj?.optString("id", "") ?: "",
                email = userObj?.optString("email", email) ?: email,
                name = userObj?.optString("name", "Fotógrafo") ?: "Fotógrafo",
                role = userObj?.optString("role", "photographer") ?: "photographer"
            )

            Log.i(TAG, "Login exitoso en Momentia para ${user.email} (${user.role})")
            Result.success(Pair(token, user))
        } catch (e: Exception) {
            Log.e(TAG, "Error en login de Momentia: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUploadEvents(token: String): Result<List<MomentiaEvent>> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE_URL/photographer/upload-events")
                .get()
                .withStandardBrowserHeaders(token)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error al cargar álbumes: HTTP ${response.code}"))
            }

            val json = JSONObject(respStr)
            val events = mutableListOf<MomentiaEvent>()

            fun parseArray(arr: JSONArray?) {
                if (arr == null) return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val name = item.optString("name", item.optString("title", "Álbum sin nombre"))
                    val date = item.optString("event_date", item.optString("created_at", ""))
                    if (id.isNotBlank() && events.none { it.id == id }) {
                        events.add(MomentiaEvent(id = id, name = name, date = date))
                    }
                }
            }

            parseArray(json.optJSONArray("my_albums"))
            parseArray(json.optJSONArray("assigned_official"))
            parseArray(json.optJSONArray("open_official"))

            Log.i(TAG, "Se encontraron ${events.size} álbumes disponibles en Momentia")
            Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener eventos de Momentia: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun requestUploadUrl(
        token: String,
        eventId: String,
        filename: String,
        fileSizeBytes: Long,
        timestampMs: Long = System.currentTimeMillis()
    ): Result<MomentiaPresignedUrl> = withContext(Dispatchers.IO) {
        try {
            val cleanName = filename.replace(Regex("[^a-zA-Z0-9_.-]"), "")
            val clientHash = "$cleanName-$fileSizeBytes-$timestampMs"

            val filesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("filename", filename)
                    put("content_type", "image/jpeg")
                    put("client_hash", clientHash)
                })
            }

            val bodyJson = JSONObject().apply {
                put("event_id", eventId)
                put("files", filesArray)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/media/request-upload-urls")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .withStandardBrowserHeaders(token)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error al solicitar URL de subida: HTTP ${response.code}"))
            }

            val json = JSONObject(respStr)
            val urls = json.optJSONArray("urls")
            if (urls == null || urls.length() == 0) {
                return@withContext Result.failure(Exception("Respuesta sin URLs de subida"))
            }

            val first = urls.getJSONObject(0)
            val presigned = MomentiaPresignedUrl(
                fileId = first.optString("file_id", ""),
                filename = first.optString("filename", filename),
                uploadUrl = if (first.isNull("upload_url")) null else first.optString("upload_url"),
                s3Key = if (first.isNull("s3_key")) null else first.optString("s3_key"),
                alreadyUploaded = first.optBoolean("already_uploaded", false),
                clientHash = first.optString("client_hash", clientHash)
            )

            Result.success(presigned)
        } catch (e: Exception) {
            Log.e(TAG, "Error solicitando presigned URL para $filename: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadToS3(
        uploadUrl: String,
        imageBytes: ByteArray,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val reqBody = imageBytes.toRequestBody(jpegMediaType)

            // Direct binary PUT to AWS S3 signed URL: ONLY Content-Type header
            val req = Request.Builder()
                .url(uploadUrl)
                .put(reqBody)
                .header("Content-Type", "image/jpeg")
                .build()

            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                onProgress(1.0f)
                Result.success(Unit)
            } else {
                val errBody = response.body?.string().orEmpty().take(200)
                Result.failure(Exception("Fallo en S3 PUT: HTTP ${response.code} - $errBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error subiendo archivo a S3: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun confirmUpload(
        token: String,
        eventId: String,
        fileId: String,
        s3Key: String,
        filename: String,
        clientHash: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("file_id", fileId)
                put("s3_key", s3Key)
                put("event_id", eventId)
                put("filename", filename)
                put("tags", JSONArray())
                put("client_hash", clientHash)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/media/confirm-upload")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .withStandardBrowserHeaders(token)
                .build()

            val response = client.newCall(req).execute()
            if (response.isSuccessful) {
                Log.i(TAG, "Confirmación de subida exitosa en Momentia para $filename")
                Result.success(Unit)
            } else {
                val err = response.body?.string().orEmpty().take(200)
                Result.failure(Exception("Error confirmando subida en Momentia: HTTP ${response.code} - $err"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error confirmando subida en Momentia: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Consulta las ventas de fotos en Momentia (opcionalmente filtradas por evento).
     */
    suspend fun getSales(
        token: String,
        eventId: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<MomentiaSalesSummary> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL/photographer/sales?page=$page&limit=$limit")
            if (!eventId.isNullOrBlank() && eventId != "all") {
                urlBuilder.append("&event_id=$eventId")
            }

            val req = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .withStandardBrowserHeaders(token)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP ${response.code} consultando ventas"))
            }

            val json = JSONObject(respStr)
            val total = json.optInt("total", 0)
            val totalPhotos = json.optInt("total_photos", 0)
            val totalEarnings = json.optDouble("total_earnings", 0.0)
            val currency = json.optString("display_currency", "ARS")

            val ordersArray = json.optJSONArray("orders") ?: JSONArray()
            val orders = mutableListOf<MomentiaSaleOrder>()
            for (i in 0 until ordersArray.length()) {
                val o = ordersArray.optJSONObject(i) ?: continue
                val orderId = o.optString("order_id", o.optString("id", ""))
                if (orderId.isNotBlank()) {
                    orders.add(
                        MomentiaSaleOrder(
                            orderId = orderId,
                            eventId = o.optString("event_id", null),
                            eventName = o.optString("event_name", null),
                            createdAt = o.optString("created_at", null),
                            totalAmount = o.optDouble("total_amount", 0.0),
                            photographerEarnings = o.optDouble("photographer_amount", o.optDouble("photographer_earnings", 0.0)),
                            photosCount = o.optInt("photos_count", o.optInt("photo_count", 1)),
                            thumbnailUrl = o.optString("thumbnail_url", null),
                            currency = o.optString("currency", currency),
                            status = o.optString("status", "PAID")
                        )
                    )
                }
            }

            Result.success(
                MomentiaSalesSummary(
                    totalOrders = total,
                    totalPhotos = totalPhotos,
                    totalEarnings = totalEarnings,
                    currency = currency,
                    recentOrders = orders,
                    lastCheckedTimestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando ventas de Momentia: ${e.message}", e)
            Result.failure(e)
        }
    }
}
