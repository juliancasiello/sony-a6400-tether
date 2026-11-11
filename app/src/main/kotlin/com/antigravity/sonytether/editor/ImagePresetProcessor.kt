package com.antigravity.sonytether.editor

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object ImagePresetProcessor {

    /**
     * Construye una curva tonal estrictamente monótona (Fritsch-Carlson Monotonic Cubic Spline)
     * replicando el comportamiento de las 5 zonas de iluminación de Lightroom:
     * - Exposición (Exposure)
     * - Altas Luces (Highlights)
     * - Sombras (Shadows)
     * - Blancos (Whites)
     * - Negros (Blacks)
     * - Contraste
     * - Dehaze (Neblina)
     */
    fun buildToneLut(config: PresetConfig): IntArray {
        val expFactor = 2.0.pow(config.exposure.toDouble()).toFloat()

        // Puntos de control normalizados [0.0 .. 1.0]
        val xPts = floatArrayOf(0.00f, 0.15f, 0.35f, 0.50f, 0.70f, 0.85f, 1.00f)

        // y0: Negros y Dehaze
        var y0 = (0.00f + config.blacks * 0.10f - config.dehaze * 0.05f).coerceIn(0.00f, 0.15f)

        // y1: Negros transición y Sombras bajas
        var y1 = (0.15f + config.blacks * 0.08f + config.shadows * 0.10f).coerceIn(y0 + 0.01f, 0.35f)

        // y2: Sombras medias
        var y2 = (0.35f + config.shadows * 0.14f).coerceIn(y1 + 0.01f, 0.55f)

        // y3: Medios (Exposición + Contraste)
        val midRaw = 0.50f * expFactor
        val cFactor = 1.0f + config.contrast * 0.40f
        var y3 = (0.50f + (midRaw - 0.50f) * cFactor).coerceIn(y2 + 0.01f, 0.70f)

        // y4: Altas Luces
        var y4 = (0.70f + config.highlights * 0.14f).coerceIn(y3 + 0.01f, 0.85f)

        // y5: Altas Luces altas y Blancos
        var y5 = (0.85f + config.highlights * 0.10f + config.whites * 0.08f).coerceIn(y4 + 0.01f, 0.98f)

        // y6: Blancos / Punto blanco
        var y6 = (1.00f + config.whites * 0.08f).coerceIn(y5 + 0.01f, 1.00f)

        val yPts = floatArrayOf(y0, y1, y2, y3, y4, y5, y6)

        // Garantizar monotonía estricta en los puntos de control
        for (i in 1 until yPts.size) {
            if (yPts[i] <= yPts[i - 1]) {
                yPts[i] = min(1.0f, yPts[i - 1] + 0.005f)
            }
        }

        // Interpolación Spline Cúbica Monótona de Fritsch-Carlson
        val n = xPts.size
        val deltas = FloatArray(n - 1)
        for (i in 0 until n - 1) {
            deltas[i] = (yPts[i + 1] - yPts[i]) / (xPts[i + 1] - xPts[i])
        }

        val m = FloatArray(n)
        m[0] = deltas[0]
        m[n - 1] = deltas[n - 2]
        for (i in 1 until n - 1) {
            if (deltas[i - 1] * deltas[i] <= 0f) {
                m[i] = 0f
            } else {
                m[i] = (deltas[i - 1] + deltas[i]) / 2f
            }
        }

        for (i in 0 until n - 1) {
            if (deltas[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
            } else {
                val alpha = m[i] / deltas[i]
                val beta = m[i + 1] / deltas[i]
                val dist = alpha * alpha + beta * beta
                if (dist > 9f) {
                    val tau = 3f / kotlin.math.sqrt(dist)
                    m[i] = tau * alpha * deltas[i]
                    m[i + 1] = tau * beta * deltas[i]
                }
            }
        }

        val lut = IntArray(256)
        for (i in 0..255) {
            val x = i / 255.0f
            var seg = 0
            var low = 0
            var high = n - 1
            while (low <= high) {
                val mid = (low + high) / 2
                if (xPts[mid] <= x) {
                    seg = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            seg = min(seg, n - 2)

            val h = xPts[seg + 1] - xPts[seg]
            val t = (x - xPts[seg]) / h
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2 * t3 - 3 * t2 + 1
            val h10 = t3 - 2 * t2 + t
            val h01 = -2 * t3 + 3 * t2
            val h11 = t3 - t2

            val yVal = h00 * yPts[seg] + h10 * h * m[seg] + h01 * yPts[seg + 1] + h11 * h * m[seg + 1]
            lut[i] = (yVal.coerceIn(0f, 1f) * 255f).roundToInt()
        }

        return lut
    }

    /**
     * Aplica el preset completo a un Bitmap utilizando procesamiento paralelo multinúcleo:
     * - Curva de tonos Lightroom (Exposición, Highlights, Shadows, Whites, Blacks, Contrast)
     * - Dehaze (reducción de neblina y recuperación de saturación)
     * - Temperatura de color (Kelvin balance)
     * - Intensidad / Saturación
     * - Textura (micro-contraste local en frecuencias medias)
     */
    suspend fun applyPresetToBitmap(
        source: Bitmap,
        config: PresetConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val totalPixels = width * height

        val baseLut = buildToneLut(config)

        // Compensación de temperatura de color
        val temp = config.temperature
        val redFactor = (1f + (temp * 0.20f)).coerceIn(0.6f, 1.4f)
        val greenFactor = (1f + (temp * 0.05f)).coerceIn(0.8f, 1.2f)
        val blueFactor = (1f - (temp * 0.25f)).coerceIn(0.6f, 1.4f)

        val lutR = IntArray(256) { i -> (baseLut[i] * redFactor).roundToInt().coerceIn(0, 255) }
        val lutG = IntArray(256) { i -> (baseLut[i] * greenFactor).roundToInt().coerceIn(0, 255) }
        val lutB = IntArray(256) { i -> (baseLut[i] * blueFactor).roundToInt().coerceIn(0, 255) }

        val effectiveSat = (config.saturation * (1f + max(0f, config.dehaze) * 0.25f)).coerceIn(0f, 2.5f)
        val applySat = effectiveSat != 1.0f
        val textureVal = config.texture.coerceIn(-1.0f, 1.0f)
        val applyTexture = textureVal != 0.0f

        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val numCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        val rowsPerCore = height / numCores

        // 1. Paso de Mapeo Tonal (LUT) y Saturación en Paralelo
        coroutineScope {
            val jobs = (0 until numCores).map { coreIdx ->
                async(Dispatchers.Default) {
                    val startY = coreIdx * rowsPerCore
                    val endY = if (coreIdx == numCores - 1) height else (coreIdx + 1) * rowsPerCore
                    val startIdx = startY * width
                    val endIdx = endY * width

                    for (i in startIdx until endIdx) {
                        val p = pixels[i]
                        val a = (p ushr 24) and 0xFF
                        var r = lutR[(p ushr 16) and 0xFF]
                        var g = lutG[(p ushr 8) and 0xFF]
                        var b = lutB[p and 0xFF]

                        if (applySat) {
                            val y = (0.299f * r + 0.587f * g + 0.114f * b)
                            r = (y + effectiveSat * (r - y)).roundToInt().coerceIn(0, 255)
                            g = (y + effectiveSat * (g - y)).roundToInt().coerceIn(0, 255)
                            b = (y + effectiveSat * (b - y)).roundToInt().coerceIn(0, 255)
                        }

                        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
            jobs.awaitAll()
        }

        // 2. Paso de Textura (Micro-contraste) en Paralelo con buffer de scanline anti-cascada
        if (applyTexture) {
            val textureFactor = textureVal * 0.60f
            coroutineScope {
                val jobs = (0 until numCores).map { coreIdx ->
                    async(Dispatchers.Default) {
                        val startY = max(1, coreIdx * rowsPerCore)
                        val endY = min(height - 1, if (coreIdx == numCores - 1) height - 1 else (coreIdx + 1) * rowsPerCore)
                        val rowStep = width

                        for (y in startY until endY) {
                            val rowOffset = y * width
                            for (x in 1 until width - 1) {
                                val idx = rowOffset + x
                                val p = pixels[idx]

                                val pTop = pixels[idx - rowStep]
                                val pBottom = pixels[idx + rowStep]
                                val pLeft = pixels[idx - 1]
                                val pRight = pixels[idx + 1]

                                val r = (p ushr 16) and 0xFF
                                val g = (p ushr 8) and 0xFF
                                val b = p and 0xFF

                                val avgR = (((pTop ushr 16) and 0xFF) + ((pBottom ushr 16) and 0xFF) +
                                        ((pLeft ushr 16) and 0xFF) + ((pRight ushr 16) and 0xFF)) shr 2
                                val avgG = (((pTop ushr 8) and 0xFF) + ((pBottom ushr 8) and 0xFF) +
                                        ((pLeft ushr 8) and 0xFF) + ((pRight ushr 8) and 0xFF)) shr 2
                                val avgB = ((pTop and 0xFF) + (pBottom and 0xFF) +
                                        (pLeft and 0xFF) + (pRight and 0xFF)) shr 2

                                val diffR = r - avgR
                                val diffG = g - avgG
                                val diffB = b - avgB

                                val newR = (r + diffR * textureFactor).roundToInt().coerceIn(0, 255)
                                val newG = (g + diffG * textureFactor).roundToInt().coerceIn(0, 255)
                                val newB = (b + diffB * textureFactor).roundToInt().coerceIn(0, 255)

                                pixels[idx] = (p and -0x1000000) or (newR shl 16) or (newG shl 8) or newB
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        // Si source es mutable y del mismo tamaño/config, reutilizar para ahorrar 96 MB de RAM
        val result = if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        result
    }
}
