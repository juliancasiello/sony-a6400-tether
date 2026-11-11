package com.antigravity.sonytether.lumepic

import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "LumepicApiClient"
private const val BASE_URL = "https://photographer.lumepic.com"
private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

class LumepicApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val jpegMediaType = "image/jpeg".toMediaType()
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun Request.Builder.withLumepicHeaders(cookies: String?): Request.Builder {
        header("User-Agent", BROWSER_USER_AGENT)
        header("Accept", "*/*")
        header("Origin", BASE_URL)
        header("Referer", "$BASE_URL/es/albums")
        header("Sec-Ch-Ua", "\"Chromium\";v=\"128\", \"Not-A.Brand\";v=\"99\"")
        header("Sec-Ch-Ua-Mobile", "?1")
        header("Sec-Ch-Ua-Platform", "\"Android\"")
        header("Sec-Fetch-Dest", "empty")
        header("Sec-Fetch-Mode", "cors")
        header("Sec-Fetch-Site", "same-origin")
        header("x-session-id", UUID.randomUUID().toString())
        header("x-correlation-id", UUID.randomUUID().toString())
        header("x-request-id", UUID.randomUUID().toString())
        if (!cookies.isNullOrBlank()) {
            header("Cookie", cookies)
        }
        return this
    }

    /**
     * Consulta los álbumes del fotógrafo en Lumepic.
     */
    suspend fun getAlbums(cookies: String): Result<List<LumepicAlbum>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/api/photographer/albums?pagination%5Blimit%5D=50&pagination%5Bskip%5D=0&order%5B0%5D%5Bfield%5D=CREATED_AT&order%5B0%5D%5Bsort%5D=DESC"
            val req = Request.Builder()
                .url(url)
                .get()
                .withLumepicHeaders(cookies)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val err = "Error HTTP ${response.code} obteniendo álbumes de Lumepic"
                Log.w(TAG, "$err: $respStr")
                return@withContext Result.failure(Exception(err))
            }

            val json = JSONObject(respStr)
            val items = json.optJSONArray("items") ?: JSONArray()
            val result = mutableListOf<LumepicAlbum>()

            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val desc = item.optString("description", "Sin título")
                val takenDate = item.optString("takenDate", null)
                val status = item.optString("status", "PUBLISHED")
                val cover = item.optString("coverPhotographSrc", null)
                val count = item.optInt("photographCount", 0)
                val loc = item.optJSONObject("location")
                val spotName = loc?.optString("spotName", null)

                if (id.isNotBlank()) {
                    result.add(
                        LumepicAlbum(
                            id = id,
                            description = desc,
                            takenDate = takenDate,
                            status = status,
                            coverPhotographSrc = cover,
                            photographCount = count,
                            spotName = spotName
                        )
                    )
                }
            }

            Log.i(TAG, "Se obtuvieron ${result.size} álbumes de Lumepic exitosamente")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando álbumes de Lumepic: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra la foto en Lumepic mediante batch-create.
     */
    suspend fun batchCreatePhotograph(
        cookies: String,
        albumId: String,
        fileName: String,
        imageBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val exifInfo = extractExifMetadata(imageBytes)
            val width = exifInfo.width
            val height = exifInfo.height
            val takenDateIso = exifInfo.takenDateIso ?: isoDateFormat.format(Date())

            val extraInfoArray = JSONArray().apply {
                for (extra in exifInfo.extraList) {
                    put(JSONObject().apply {
                        put("key", extra.key)
                        put("value", extra.value)
                    })
                }
            }

            val photoObj = JSONObject().apply {
                put("albumId", albumId)
                put("type", "image/jpeg")
                put("size", imageBytes.size)
                put("originalFileName", fileName)
                put("clientRef", UUID.randomUUID().toString())
                put("takenDate", takenDateIso)
                put("width", width)
                put("height", height)
                put("extraInfo", extraInfoArray)
            }

            val bodyJson = JSONObject().apply {
                put("albumId", albumId)
                put("uploadAttemptId", UUID.randomUUID().toString())
                put("photographs", JSONArray().apply { put(photoObj) })
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/api/photographs/batch-create")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .withLumepicHeaders(cookies)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP ${response.code} en batch-create de Lumepic: $respStr"))
            }

            val respArray = JSONArray(respStr)
            if (respArray.length() == 0) {
                return@withContext Result.failure(Exception("Respuesta vacía en batch-create de Lumepic"))
            }

            val first = respArray.getJSONObject(0)
            val photographId = first.optString("photographId")
            if (photographId.isBlank()) {
                return@withContext Result.failure(Exception("No se recibió photographId en Lumepic"))
            }

            Log.i(TAG, "Foto $fileName registrada en Lumepic con ID: $photographId")
            Result.success(photographId)
        } catch (e: Exception) {
            Log.e(TAG, "Error en batch-create de Lumepic: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Solicita la URL prefirmada de Amazon S3 en Lumepic mediante batch-presign.
     */
    suspend fun batchPresignPhotograph(
        cookies: String,
        albumId: String,
        photographId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("albumId", albumId)
                put("photographIds", JSONArray().apply { put(photographId) })
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/api/photographs/batch-presign")
                .post(bodyJson.toRequestBody(jsonMediaType))
                .withLumepicHeaders(cookies)
                .build()

            val response = client.newCall(req).execute()
            val respStr = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP ${response.code} en batch-presign de Lumepic: $respStr"))
            }

            val respArray = JSONArray(respStr)
            if (respArray.length() == 0) {
                return@withContext Result.failure(Exception("Respuesta vacía en batch-presign de Lumepic"))
            }

            val first = respArray.getJSONObject(0)
            val uploadUrl = first.optString("uploadUrl")
            if (uploadUrl.isBlank()) {
                return@withContext Result.failure(Exception("URL de subida vacía en Lumepic"))
            }

            Log.i(TAG, "URL prefirmada de S3 obtenida para photographId $photographId")
            Result.success(uploadUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error en batch-presign de Lumepic: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sube binariamente la foto directa a AWS S3.
     */
    suspend fun uploadToS3(
        uploadUrl: String,
        bytes: ByteArray,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val progressBody = object : RequestBody() {
                override fun contentType() = jpegMediaType
                override fun contentLength() = bytes.size.toLong()

                override fun writeTo(sink: BufferedSink) {
                    val total = bytes.size.toFloat()
                    val chunkSize = 64 * 1024
                    var offset = 0

                    while (offset < bytes.size) {
                        val len = minOf(chunkSize, bytes.size - offset)
                        sink.write(bytes, offset, len)
                        offset += len
                        onProgress(offset / total)
                    }
                }
            }

            val req = Request.Builder()
                .url(uploadUrl)
                .put(progressBody)
                .header("Content-Type", "image/jpeg")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP ${response.code} al subir a S3 de Lumepic"))
            }

            Log.i(TAG, "Subida binaria a S3 de Lumepic exitosa (HTTP ${response.code})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error en subida binaria a S3 de Lumepic: ${e.message}", e)
            Result.failure(e)
        }
    }

    private data class ExtractedExif(
        val width: Int,
        val height: Int,
        val takenDateIso: String?,
        val extraList: List<LumepicExtraInfo>
    )

    private fun extractExifMetadata(bytes: ByteArray): ExtractedExif {
        val extra = mutableListOf<LumepicExtraInfo>()
        var width = 6000
        var height = 4000
        var takenDateIso: String? = null

        try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))

            val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (w > 0) width = w
            if (h > 0) height = h

            val dt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (!dt.isNullOrBlank()) {
                try {
                    val exifSdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val parsed = exifSdf.parse(dt)
                    if (parsed != null) {
                        takenDateIso = isoDateFormat.format(parsed)
                    }
                } catch (_: Exception) {}
            }

            val tagKeys = listOf(
                ExifInterface.TAG_MAKE to "Make",
                ExifInterface.TAG_MODEL to "Model",
                ExifInterface.TAG_ORIENTATION to "Orientation",
                ExifInterface.TAG_EXPOSURE_TIME to "ExposureTime",
                ExifInterface.TAG_F_NUMBER to "FNumber",
                ExifInterface.TAG_ISO_SPEED_RATINGS to "ISO",
                ExifInterface.TAG_FOCAL_LENGTH to "FocalLength",
                ExifInterface.TAG_WHITE_BALANCE to "WhiteBalance",
                ExifInterface.TAG_SOFTWARE to "Software",
                ExifInterface.TAG_ARTIST to "Artist"
            )

            for ((tag, key) in tagKeys) {
                exif.getAttribute(tag)?.let { value ->
                    extra.add(LumepicExtraInfo(key, value))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo extraer metadata EXIF completa: ${e.message}")
        }

        if (extra.none { it.key == "Make" }) extra.add(LumepicExtraInfo("Make", "SONY"))
        if (extra.none { it.key == "Model" }) extra.add(LumepicExtraInfo("Model", "ILCE-6400"))

        return ExtractedExif(width, height, takenDateIso, extra)
    }
}
