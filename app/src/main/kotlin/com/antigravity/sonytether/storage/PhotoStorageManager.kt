package com.antigravity.sonytether.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.antigravity.sonytether.editor.ImagePresetProcessor
import com.antigravity.sonytether.editor.PresetConfig
import com.antigravity.sonytether.model.DownloadStatus
import com.antigravity.sonytether.model.TetheredPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

private const val TAG = "PhotoStorageManager"

data class SavedOriginalResult(
    val photo: TetheredPhoto,
    val originalBytes: ByteArray,
    val rotationDegrees: Int,
    val fileName: String
)

data class SavedBwResult(
    val photo: TetheredPhoto,
    val bwBytes: ByteArray,
    val fileName: String
)

class PhotoStorageManager(private val context: Context) {

    companion object {
        const val BASE_FOLDER = "SonyAlpha_Tether"

        fun sanitizeFolderName(name: String): String {
            return name.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        }
    }

    /**
     * Guarda la foto ORIGINAL recibida por USB en Pictures/SonyA6400_Tether/<Evento>/Originales/
     * y mantiene únicamente las últimas 5 originales para ahorrar espacio en el celular.
     */
    suspend fun saveOriginalFromUsb(
        mtpDevice: UsbDevice,
        objectHandle: Int,
        fileName: String,
        expectedSize: Long,
        eventName: String,
        recentOriginalUris: MutableList<Uri>,
        readObjectCallback: (java.io.OutputStream) -> Unit
    ): Result<SavedOriginalResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val sanitizedEvent = sanitizeFolderName(eventName)
            val originalRelativeFolder = "${Environment.DIRECTORY_PICTURES}/$BASE_FOLDER/$sanitizedEvent"
            val resolver = context.contentResolver

            val originalValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, originalRelativeFolder)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val originalUri: Uri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                originalValues
            ) ?: return@withContext Result.failure(Exception("No se pudo crear entrada original en MediaStore"))

            val baos = ByteArrayOutputStream((expectedSize.coerceAtLeast(1024L).coerceAtMost(25 * 1024 * 1024L)).toInt())
            resolver.openOutputStream(originalUri)?.use { originalOut ->
                readObjectCallback(object : java.io.OutputStream() {
                    override fun write(b: Int) {
                        originalOut.write(b)
                        baos.write(b)
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        originalOut.write(b, off, len)
                        baos.write(b, off, len)
                    }
                    override fun flush() {
                        originalOut.flush()
                        baos.flush()
                    }
                })
                originalOut.flush()
            } ?: return@withContext Result.failure(Exception("No se pudo abrir stream para foto original"))

            val originalBytes = baos.toByteArray()
            val finalOriginalSize = if (originalBytes.isNotEmpty()) originalBytes.size.toLong() else expectedSize

            // Leer orientación EXIF original
            val exif = if (originalBytes.isNotEmpty()) {
                try {
                    android.media.ExifInterface(java.io.ByteArrayInputStream(originalBytes))
                } catch (e: Throwable) {
                    Log.w(TAG, "No se pudo leer EXIF original: ${e.message}")
                    null
                }
            } else null

            val exifOrientation = exif?.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            ) ?: android.media.ExifInterface.ORIENTATION_NORMAL

            val rotationDegrees = when (exifOrientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            Log.i(TAG, "Orientación EXIF para $fileName: rotación ${rotationDegrees}°")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                originalValues.clear()
                originalValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                originalValues.put(MediaStore.Images.Media.ORIENTATION, rotationDegrees)
                resolver.update(originalUri, originalValues, null, null)
            }

            val durationMs = System.currentTimeMillis() - startTime

            val originalPhoto = TetheredPhoto(
                id = UUID.randomUUID().toString(),
                fileName = fileName,
                remoteUrl = "usb://${mtpDevice.deviceName}/$objectHandle",
                localUri = originalUri,
                fileSizeBytes = finalOriginalSize,
                timestamp = System.currentTimeMillis(),
                status = DownloadStatus.COMPLETED,
                downloadDurationMs = durationMs
            )

            Result.success(SavedOriginalResult(originalPhoto, originalBytes, rotationDegrees, fileName))
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando foto original", e)
            Result.failure(e)
        }
    }

    /**
     * Procesa la foto original aplicando la orientación física correcta y el preset
     * guardándola en Pictures/SonyA6400_Tether/<Evento>/Editadas/ y notificando para subida a Momentia.
     */
    suspend fun saveEditedPhoto(
        originalBytes: ByteArray,
        baseFileName: String,
        rotationDegrees: Int,
        eventName: String,
        presetConfig: PresetConfig,
        onEditedPhotoReady: ((fileName: String, bytes: ByteArray) -> Unit)? = null
    ): Result<TetheredPhoto> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (originalBytes.isEmpty()) {
                return@withContext Result.failure(Exception("Bytes originales vacíos"))
            }

            val sanitizedEvent = sanitizeFolderName(eventName)
            val editedRelativeFolder = "${Environment.DIRECTORY_PICTURES}/$BASE_FOLDER/$sanitizedEvent/Editadas"
            val resolver = context.contentResolver

            val editedFileName = if (baseFileName.contains('.')) {
                baseFileName.substringBeforeLast('.') + "_editada.jpg"
            } else {
                "${baseFileName}_editada.jpg"
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inMutable = true
            }
            val rawBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
                ?: return@withContext Result.failure(Exception("No se pudo decodificar bitmap original"))

            var uprightBitmap: Bitmap? = null
            var editedBitmap: Bitmap? = null

            try {
                // Rotar físicamente los píxeles para que fotos verticales queden siempre verticales
                uprightBitmap = if (rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
                    val rotated = Bitmap.createBitmap(
                        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                    )
                    if (rotated != rawBitmap) {
                        rawBitmap.recycle()
                    }
                    rotated
                } else {
                    rawBitmap
                }

                editedBitmap = ImagePresetProcessor.applyPresetToBitmap(uprightBitmap, presetConfig)

                val editedValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, editedFileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.ORIENTATION, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, editedRelativeFolder)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val editedUri = resolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    editedValues
                ) ?: return@withContext Result.failure(Exception("No se pudo insertar foto editada en MediaStore"))

                val editedBaos = ByteArrayOutputStream()
                editedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, editedBaos)
                val editedBytes = editedBaos.toByteArray()

                resolver.openOutputStream(editedUri)?.use { editedOut ->
                    editedOut.write(editedBytes)
                    editedOut.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    editedValues.clear()
                    editedValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    editedValues.put(MediaStore.Images.Media.ORIENTATION, 0)
                    resolver.update(editedUri, editedValues, null, null)
                }

                val editedPhoto = TetheredPhoto(
                    id = UUID.randomUUID().toString(),
                    fileName = editedFileName,
                    remoteUrl = "preset://$editedFileName",
                    localUri = editedUri,
                    fileSizeBytes = editedBytes.size.toLong(),
                    timestamp = System.currentTimeMillis(),
                    status = DownloadStatus.COMPLETED,
                    downloadDurationMs = System.currentTimeMillis() - startTime
                )

                if (editedBytes.isNotEmpty()) {
                    try {
                        onEditedPhotoReady?.invoke(editedFileName, editedBytes)
                    } catch (ue: Throwable) {
                        Log.w(TAG, "Aviso en callback de foto editada: ${ue.message}")
                    }
                }

                Log.i(TAG, "Foto editada guardada exitosamente en $editedRelativeFolder/$editedFileName")
                Result.success(editedPhoto)
            } finally {
                if (uprightBitmap != null && !uprightBitmap.isRecycled && uprightBitmap != rawBitmap) {
                    uprightBitmap.recycle()
                }
                if (!rawBitmap.isRecycled) {
                    rawBitmap.recycle()
                }
                if (editedBitmap != null && !editedBitmap.isRecycled && editedBitmap != uprightBitmap) {
                    editedBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando y guardando foto editada: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Genera y guarda una copia fiel en blanco y negro (grayscale) en Pictures/SonyA6400_Tether/<Evento>/
     * aplicando la rotación física para que quede siempre vertical u horizontal correcta.
     */
    suspend fun saveBwCopy(
        originalBytes: ByteArray,
        baseFileName: String,
        rotationDegrees: Int,
        eventName: String
    ): Result<SavedBwResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            if (originalBytes.isEmpty()) {
                return@withContext Result.failure(Exception("Bytes originales vacíos para copia B&N"))
            }

            val sanitizedEvent = sanitizeFolderName(eventName)
            val relativeFolder = "${Environment.DIRECTORY_PICTURES}/$BASE_FOLDER/$sanitizedEvent"
            val resolver = context.contentResolver

            val bwFileName = if (baseFileName.contains('.')) {
                baseFileName.substringBeforeLast('.') + "_BN.jpg"
            } else {
                "${baseFileName}_BN.jpg"
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inMutable = true
            }
            val rawBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
                ?: return@withContext Result.failure(Exception("No se pudo decodificar bitmap para copia B&N"))

            val targetWidth = if (rotationDegrees == 90 || rotationDegrees == 270) rawBitmap.height else rawBitmap.width
            val targetHeight = if (rotationDegrees == 90 || rotationDegrees == 270) rawBitmap.width else rawBitmap.height

            val bwBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bwBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

            val rotMatrix = Matrix()
            if (rotationDegrees != 0) {
                rotMatrix.postRotate(rotationDegrees.toFloat())
                when (rotationDegrees) {
                    90 -> rotMatrix.postTranslate(targetWidth.toFloat(), 0f)
                    180 -> rotMatrix.postTranslate(targetWidth.toFloat(), targetHeight.toFloat())
                    270 -> rotMatrix.postTranslate(0f, targetHeight.toFloat())
                }
            }
            canvas.drawBitmap(rawBitmap, rotMatrix, paint)
            rawBitmap.recycle()

            val bwValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, bwFileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.ORIENTATION, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativeFolder)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val bwUri = resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                bwValues
            ) ?: run {
                bwBitmap.recycle()
                return@withContext Result.failure(Exception("No se pudo insertar copia B&N en MediaStore"))
            }

            val bwBaos = ByteArrayOutputStream()
            bwBitmap.compress(Bitmap.CompressFormat.JPEG, 92, bwBaos)
            bwBitmap.recycle()
            val bwBytes = bwBaos.toByteArray()

            resolver.openOutputStream(bwUri)?.use { out ->
                out.write(bwBytes)
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bwValues.clear()
                bwValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                bwValues.put(MediaStore.Images.Media.ORIENTATION, 0)
                resolver.update(bwUri, bwValues, null, null)
            }

            val bwPhoto = TetheredPhoto(
                id = UUID.randomUUID().toString(),
                fileName = bwFileName,
                remoteUrl = "bw://$bwFileName",
                localUri = bwUri,
                fileSizeBytes = bwBytes.size.toLong(),
                timestamp = System.currentTimeMillis(),
                status = DownloadStatus.COMPLETED,
                downloadDurationMs = System.currentTimeMillis() - startTime
            )

            Log.i(TAG, "Copia B&N guardada exitosamente: $relativeFolder/$bwFileName (${bwBytes.size / 1024} KB)")
            Result.success(SavedBwResult(bwPhoto, bwBytes, bwFileName))
        } catch (e: Exception) {
            Log.e(TAG, "Error generando copia B&N de $baseFileName: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Guarda ambas versiones en una sola llamada (original + editada inmediata).
     */
    suspend fun savePhotoFromUsbMtp(
        mtpDevice: UsbDevice,
        objectHandle: Int,
        fileName: String,
        expectedSize: Long,
        eventName: String,
        presetConfig: PresetConfig,
        recentOriginalUris: MutableList<Uri>,
        readObjectCallback: (java.io.OutputStream) -> Unit,
        onEditedPhotoReady: ((fileName: String, bytes: ByteArray) -> Unit)? = null
    ): Result<Pair<TetheredPhoto, TetheredPhoto?>> = withContext(Dispatchers.IO) {
        val origRes = saveOriginalFromUsb(
            mtpDevice = mtpDevice,
            objectHandle = objectHandle,
            fileName = fileName,
            expectedSize = expectedSize,
            eventName = eventName,
            recentOriginalUris = recentOriginalUris,
            readObjectCallback = readObjectCallback
        )

        if (origRes.isFailure) {
            return@withContext Result.failure(origRes.exceptionOrNull() ?: Exception("Fallo al guardar original"))
        }

        val origData = origRes.getOrThrow()
        val editRes = saveEditedPhoto(
            originalBytes = origData.originalBytes,
            baseFileName = origData.fileName,
            rotationDegrees = origData.rotationDegrees,
            eventName = eventName,
            presetConfig = presetConfig,
            onEditedPhotoReady = onEditedPhotoReady
        )

        Result.success(Pair(origData.photo, editRes.getOrNull()))
    }

    /**
     * Elimina todas las fotos de un evento completo (Originales y Editadas) para liberar espacio.
     */
    suspend fun deleteEventPhotos(eventName: String): Int = withContext(Dispatchers.IO) {
        val sanitizedEvent = sanitizeFolderName(eventName)
        if (sanitizedEvent.isBlank()) return@withContext 0

        val resolver = context.contentResolver
        var deletedCount = 0

        try {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%$BASE_FOLDER/$sanitizedEvent%", "%SonyA6400_Tether/$sanitizedEvent%")

            val cursor = resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    try {
                        deletedCount += resolver.delete(uri, null, null)
                    } catch (de: Throwable) {
                        Log.w(TAG, "No se pudo eliminar foto $uri: ${de.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando fotos del evento $eventName", e)
        }

        deletedCount
    }
}
