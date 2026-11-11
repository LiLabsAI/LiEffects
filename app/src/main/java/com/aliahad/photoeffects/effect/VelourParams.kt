package com.aliahad.photoeffects.effect

/**
 * Shared Velour parameters. Processing order is global soft focus, variable masked
 * blur, cubic channel curves, saturation and gain, grain, then intensity blending.
 * The bundled blurmask.jpg keeps the centre sharp and softens the edges.
 */
data class VelourParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Fixed per-variant seed (combined with the per-photo seed at render time). */
    val seed: Int,
    // ---- calibrated tool values; the look is driven by the values below ----
    /** "Filter" tool value (0..100). */
    val filter: Int = 100,
    /** "Blur" tool value (0..100); drives the masked blur radius. */
    val blur: Int = 20,
    /** "Texture" tool value (0..100). */
    val texture: Int = 100,
    /** "Monochrome" tool value (0..100). */
    val monochrome: Int = 0,
    /** "Scale" tool value (0..100, -1 = not exposed). */
    val scale: Int = -1,
    /** "Random" tool value (0..100, -1 = not exposed). */
    val random: Int = -1,
    // ---- fitted transformation ----
    /** Global soft-focus gaussian sigma per 1080-scale minimum dimension (§1.1). */
    val sigma1080: Float = 2.16f,
    val toneR: CubicTone = CubicTone(-1.72827f, 2.35061f, 0.25792f, 0.02981f),
    val toneG: CubicTone = CubicTone(-1.61948f, 2.68282f, -0.13652f, 0.05348f),
    val toneB: CubicTone = CubicTone(0.01295f, 0.52265f, 0.54160f, 0.00525f),
    val satMix: Float = 0.450f,
    val gainR: GainOffset = GainOffset(1.06336f, -0.02092f),
    val gainG: GainOffset = GainOffset(1.00328f, -0.00219f),
    val gainB: GainOffset = GainOffset(1.01971f, -0.00845f),
    /** Monochrome grain amplitude (luma std) and cell size per 1080-scale min dimension (§1.4). */
    val grainAmp: Float = 0.0113f,
    val grainCell1080: Float = 1.35f,
    /** Texture-tool mottling: two luma-neutral octaves (freq per 1080-scale min dimension). */
    val mottleAmp: Float = 0.018f,
    val mottleFreq: Float = 2.0f,
    val mottleAmp2: Float = 0.008f,
    val mottleFreq2: Float = 4.0f,
    val defaultIntensity: Int = 100,
) {
    /** Masked variable blur radius in px per 1080-scale min dimension (§1.2). */
    val blurRadius1080: Float get() = 30f * (blur / 100f) * 1.25f

    /** Uniform payload pushed into the VELOUR AGSL program. */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(VelourUniforms.SEED, seed.toFloat())
        put(VelourUniforms.SIGMA, sigma1080)
        put(VelourUniforms.BLUR_RADIUS, blurRadius1080)
        put(VelourUniforms.TONE_R3, toneR.a3)
        put(VelourUniforms.TONE_R2, toneR.a2)
        put(VelourUniforms.TONE_R1, toneR.a1)
        put(VelourUniforms.TONE_R0, toneR.a0)
        put(VelourUniforms.TONE_G3, toneG.a3)
        put(VelourUniforms.TONE_G2, toneG.a2)
        put(VelourUniforms.TONE_G1, toneG.a1)
        put(VelourUniforms.TONE_G0, toneG.a0)
        put(VelourUniforms.TONE_B3, toneB.a3)
        put(VelourUniforms.TONE_B2, toneB.a2)
        put(VelourUniforms.TONE_B1, toneB.a1)
        put(VelourUniforms.TONE_B0, toneB.a0)
        put(VelourUniforms.SAT_MIX, satMix)
        put(VelourUniforms.GAIN_R, gainR.gain)
        put(VelourUniforms.OFF_R, gainR.offset)
        put(VelourUniforms.GAIN_G, gainG.gain)
        put(VelourUniforms.OFF_G, gainG.offset)
        put(VelourUniforms.GAIN_B, gainB.gain)
        put(VelourUniforms.OFF_B, gainB.offset)
        put(VelourUniforms.GRAIN_AMP, grainAmp)
        put(VelourUniforms.GRAIN_CELL, grainCell1080)
        put(VelourUniforms.MOTTLE_AMP, mottleAmp)
        put(VelourUniforms.MOTTLE_FREQ, mottleFreq)
        put(VelourUniforms.MOTTLE_AMP2, mottleAmp2)
        put(VelourUniforms.MOTTLE_FREQ2, mottleFreq2)
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        append("soft-focus glow · grain")
        if (blur >= 45) append(" · heavy blur") else if (blur >= 25) append(" · blur")
        if (scale >= 0) append(" · scale")
        if (random >= 0) append(" · random")
        if (monochrome > 0) append(" · mono")
    }
}

/** Uniform names shared by the VELOUR AGSL shader and the CPU mirror. */
object VelourUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    const val SIGMA = "uSigma"
    const val BLUR_RADIUS = "uBlurRadius"
    const val TONE_R3 = "uToneR3"
    const val TONE_R2 = "uToneR2"
    const val TONE_R1 = "uToneR1"
    const val TONE_R0 = "uToneR0"
    const val TONE_G3 = "uToneG3"
    const val TONE_G2 = "uToneG2"
    const val TONE_G1 = "uToneG1"
    const val TONE_G0 = "uToneG0"
    const val TONE_B3 = "uToneB3"
    const val TONE_B2 = "uToneB2"
    const val TONE_B1 = "uToneB1"
    const val TONE_B0 = "uToneB0"
    const val SAT_MIX = "uSatMix"
    const val GAIN_R = "uGainR"
    const val OFF_R = "uOffR"
    const val GAIN_G = "uGainG"
    const val OFF_G = "uOffG"
    const val GAIN_B = "uGainB"
    const val OFF_B = "uOffB"
    const val GRAIN_AMP = "uGrainAmp"
    const val GRAIN_CELL = "uGrainCell"
    const val MOTTLE_AMP = "uMottleAmp"
    const val MOTTLE_FREQ = "uMottleFreq"
    const val MOTTLE_AMP2 = "uMottleAmp2"
    const val MOTTLE_FREQ2 = "uMottleFreq2"
}
