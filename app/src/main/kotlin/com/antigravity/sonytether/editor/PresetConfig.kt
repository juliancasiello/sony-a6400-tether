package com.antigravity.sonytether.editor

data class PresetConfig(
    // Iluminación estilo Lightroom
    val exposure: Float = 0.0f,       // -2.0f .. +2.0f EV
    val highlights: Float = 0.0f,     // -1.0f .. +1.0f (-100% .. +100%)
    val shadows: Float = 0.0f,        // -1.0f .. +1.0f (-100% .. +100%)
    val whites: Float = 0.0f,         // -1.0f .. +1.0f (-100% .. +100%)
    val blacks: Float = 0.0f,         // -1.0f .. +1.0f (-100% .. +100%)

    // Presencia y Enfoque
    val contrast: Float = 0.0f,       // -1.0f .. +1.0f (-100% .. +100%)
    val texture: Float = 0.0f,        // -1.0f .. +1.0f (Realce de micro-contraste)
    val dehaze: Float = 0.0f,         // -1.0f .. +1.0f (Corrección de velo y neblina)

    // Color
    val temperature: Float = 0.0f,    // -1.0f .. +1.0f (Frío .. Cálido)
    val saturation: Float = 1.0f      // 0.0f .. 2.0f (0% B&N .. 200% Vívido)
) {
    companion object {
        val EVENTO = PresetConfig(
            exposure = 0.15f,
            highlights = -0.25f,
            shadows = 0.25f,
            whites = 0.10f,
            blacks = -0.10f,
            contrast = 0.10f,
            texture = 0.20f,
            dehaze = 0.10f,
            temperature = 0.08f,
            saturation = 1.15f
        )

        val CALIDO = PresetConfig(
            exposure = 0.10f,
            highlights = -0.15f,
            shadows = 0.20f,
            whites = 0.05f,
            blacks = -0.05f,
            contrast = 0.08f,
            texture = 0.10f,
            dehaze = 0.05f,
            temperature = 0.25f,
            saturation = 1.10f
        )

        val VIBRANTE = PresetConfig(
            exposure = 0.12f,
            highlights = -0.30f,
            shadows = 0.35f,
            whites = 0.15f,
            blacks = -0.15f,
            contrast = 0.15f,
            texture = 0.25f,
            dehaze = 0.15f,
            temperature = 0.05f,
            saturation = 1.30f
        )

        val NEUTRO = PresetConfig(
            exposure = 0.0f,
            highlights = 0.0f,
            shadows = 0.0f,
            whites = 0.0f,
            blacks = 0.0f,
            contrast = 0.0f,
            texture = 0.0f,
            dehaze = 0.0f,
            temperature = 0.0f,
            saturation = 1.0f
        )
    }
}
