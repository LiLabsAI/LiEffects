package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.FocalLutData

/**
 * Parameters of the FOCAL pipeline (PREQUEL "Focal" pack, 6 numbered variants, found on the
 * FOR YOU shelf).
 *
 * Recovered from PREQUEL 2.5.0's full-resolution exports of the SAME demo photo
 * (`docs/prequel-research/device-focal/exports/focal-{1..6}.jpg`) against the shared baseline:
 * every variant is a hue-dependent 3D colour LUT, fitted at 33^3 nodes (`docs/prequel-research/
 * scripts/fit_focal_lut.py`, median fit error 1.3-1.6 / 255) and packed into
 * `assets/focal_luts.png` (six 198x198 blocks, see [FocalLutData]).
 *
 * Variant #1 additionally carries PREQUEL's portrait-ellipse soft focus: the photo is sharp
 * inside the ellipse and blurred outside it (3x3 [1,2,1] kernel fitted at sigma 0.7 px on the
 * 1600-px reference export). The measured FOCAL tool row is Blur 100 / Filter 100 on every
 * variant, but only #1's look includes the blur.
 *
 * Pipeline ([FOCAL_AGSL] and [com.aliahad.photoeffects.effect.cpu.CpuFocalRenderer]):
 *   1. optional ellipse-masked blur of the source (variant #1 only)
 *   2. trilinear sample of this variant's 33^3 colour LUT
 *   3. intensity blend
 *
 * RETUNING = EDITING THE SIX ROWS IN [FocalFamily]; the LUT atlas is regenerated from the
 * research exports by `fit_focal_lut.py` + `pack_focal_lut_atlas.py`.
 */
data class FocalParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Index of this variant's 198x198 block in the six-block LUT atlas (0..5). */
    val lutIndex: Int,
    /** Variant #1: apply PREQUEL's portrait-ellipse soft focus. */
    val ellipseBlur: Boolean = false,
    // ---- measured PREQUEL tool row (reference; the look is the fitted LUT) ----
    /** "Filter" tool value (0..100). */
    val filter: Int = 100,
    /** "Blur" tool value (0..100). */
    val blur: Int = 100,
    val defaultIntensity: Int = 100,
) {
    /** Uniform payload pushed into the FOCAL AGSL program. */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(
            FocalUniforms.LUT_ORIGIN_X,
            ((lutIndex % FocalLutData.ATLAS_COLUMNS) * FocalLutData.BLOCK).toFloat(),
        )
        put(
            FocalUniforms.LUT_ORIGIN_Y,
            ((lutIndex / FocalLutData.ATLAS_COLUMNS) * FocalLutData.BLOCK).toFloat(),
        )
        put(FocalUniforms.ELLIPSE_ON, if (ellipseBlur) 1f else 0f)
        put(FocalUniforms.ELLIPSE_CX, ELLIPSE_CX)
        put(FocalUniforms.ELLIPSE_CY, ELLIPSE_CY)
        put(FocalUniforms.ELLIPSE_RX, ELLIPSE_RX)
        put(FocalUniforms.ELLIPSE_RY, ELLIPSE_RY)
        put(FocalUniforms.ELLIPSE_SOFT, ELLIPSE_SOFT)
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

/** Uniform names shared by the FOCAL AGSL shader and the CPU mirror. */
object FocalUniforms {
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
