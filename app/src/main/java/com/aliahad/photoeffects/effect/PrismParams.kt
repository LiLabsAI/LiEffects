package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.PrismLutData

/**
 * Shared Prism parameters. Each variant samples a 33³ colour LUT packed in
 * assets/prism_luts.png. Variant one first applies an ellipse-masked blur;
 * all variants finish with intensity blending. The atlas and parameters are shared
 * by the CPU and AGSL paths.
 */
data class PrismParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Index of this variant's 198x198 block in the six-block LUT atlas (0..5). */
    val lutIndex: Int,
    /** Variant #1: apply portrait-ellipse soft focus. */
    val ellipseBlur: Boolean = false,
    // ---- calibrated tool values; the look is driven by the LUT ----
    /** "Filter" tool value (0..100). */
    val filter: Int = 100,
    /** "Blur" tool value (0..100). */
    val blur: Int = 100,
    val defaultIntensity: Int = 100,
) {
    /** Uniform payload pushed into the PRISM AGSL program. */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(
            PrismUniforms.LUT_ORIGIN_X,
            ((lutIndex % PrismLutData.ATLAS_COLUMNS) * PrismLutData.BLOCK).toFloat(),
        )
        put(
            PrismUniforms.LUT_ORIGIN_Y,
            ((lutIndex / PrismLutData.ATLAS_COLUMNS) * PrismLutData.BLOCK).toFloat(),
        )
        put(PrismUniforms.ELLIPSE_ON, if (ellipseBlur) 1f else 0f)
        put(PrismUniforms.ELLIPSE_CX, ELLIPSE_CX)
        put(PrismUniforms.ELLIPSE_CY, ELLIPSE_CY)
        put(PrismUniforms.ELLIPSE_RX, ELLIPSE_RX)
        put(PrismUniforms.ELLIPSE_RY, ELLIPSE_RY)
        put(PrismUniforms.ELLIPSE_SOFT, ELLIPSE_SOFT)
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = if (ellipseBlur) "colour LUT · soft focus" else "colour LUT"

    companion object {
        // Recovered #1 portrait ellipse (normalized image coordinates, origin top-left).
        const val ELLIPSE_CX = 0.5f
        const val ELLIPSE_CY = 0.4788f
        const val ELLIPSE_RX = 0.3588f
        const val ELLIPSE_RY = 0.2812f
        const val ELLIPSE_SOFT = 0.436f
    }
}

/** Uniform names shared by the PRISM AGSL shader and the CPU mirror. */
object PrismUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    /** LUT atlas bitmap input (asset-backed, sampled in atlas pixel space). */
    const val LUT_SHADER = "uLut"
    /** Pixel origin of this variant's 198x198 block inside the atlas. */
    const val LUT_ORIGIN_X = "uLutOriginX"
    const val LUT_ORIGIN_Y = "uLutOriginY"
    const val ELLIPSE_ON = "uEllipseOn"
    const val ELLIPSE_CX = "uEllipseCx"
    const val ELLIPSE_CY = "uEllipseCy"
    const val ELLIPSE_RX = "uEllipseRx"
    const val ELLIPSE_RY = "uEllipseRy"
    const val ELLIPSE_SOFT = "uEllipseSoft"
}
