package com.aliahad.photoeffects.effect

/**
 * Parameters of the YESTERDAY pipeline (PREQUEL "yesterday" pack — a SINGLE preset).
 *
 * The pack ships one look (HUD `Yesterday`, no variant strip); the tool panel is
 * Filter 70 / Noise 100 / Vignette 35 / Mist 30 / Gaussian blur 2 / Date. This row is the
 * faithful rewrite fitted by the Lead on the SAME demo photo from PREQUEL's own full-res
 * export (`docs/prequel-research/device-packs/exports/yesterday_fullhd.jpg`); see
 * `docs/prequel-research/spec/yesterday-rewrite-spec.md` §1 for the exact constants.
 *
 * Pipeline order (implemented identically by [YESTERDAY_AGSL] and
 * [com.aliahad.photoeffects.effect.cpu.CpuYesterdayRenderer]):
 *   1. pre-blur            gaussian sigma = 0.67 * min(W,H)/1080   ("Gaussian blur 2")
 *   2. per-channel tone    cubic curves (§1.2), lifts blacks + warm R>G>B bias
 *   3. saturation mix      mix(gray, c, 1-0.175) + per-channel gain/offset (§1.3)
 *   4. vignette            g(r) = 1.0985 - 0.0337 r - 0.1047 r^2 (§1.4)
 *   5. grain + mottle      monochrome value-noise, amp 0.029, cell 2 * min/1080; low-freq fbm
 *   6. date stamp          dot-matrix `DD MM 'YY`, drawn separately over everything (§1.6)
 *   7. intensity blend
 *
 * RETUNING = EDITING THIS ONE ROW ONLY.
 */
data class YesterdayParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Fixed preset seed (combined with the per-photo seed at render time). */
    val seed: Int = 301,
    /** Pre-blur sigma per 1080-scale minimum dimension ("Gaussian blur 2"). */
    val blurSigma1080: Float = 0.67f,
    // ---- per-channel cubic tone curves, coefficients highest degree first (§1.2) ----
    val toneR: CubicTone = CubicTone(a3 = -0.4152f, a2 = 0.5073f, a1 = 0.7445f, a0 = 0.0843f),
    val toneG: CubicTone = CubicTone(a3 = -0.2005f, a2 = 0.5166f, a1 = 0.5905f, a0 = 0.0956f),
    val toneB: CubicTone = CubicTone(a3 = -0.1093f, a2 = 0.4140f, a1 = 0.5182f, a0 = 0.1028f),
    /** Saturation mix amount: t = mix(gray, c, 1 - satMix) (§1.3). */
    val satMix: Float = 0.115f,
    val gainR: GainOffset = GainOffset(1.01665f, -0.00788f),
    val gainG: GainOffset = GainOffset(1.00173f, -0.00034f),
    val gainB: GainOffset = GainOffset(1.00545f, -0.00381f),
    // ---- vignette polynomial g(r) = c0 + c1*r + c2*r^2 (§1.4) ----
    val vignetteC0: Float = 1.098529f,
    val vignetteC1: Float = -0.033667f,
    val vignetteC2: Float = -0.104743f,
    /** Monochrome grain amplitude (luma std) and cell size per 1080-scale min dimension (§1.5). */
    val grainAmp: Float = 0.019f,
    val grainCell1080: Float = 2.0f,
    /** Low-frequency mottling amplitude and frequency (per min dimension). */
    val mottleAmp: Float = 0.0108f,
    val mottleFreq: Float = 1.5f,
    val defaultIntensity: Int = 100,
) {
    /** Uniform payload pushed into the YESTERDAY AGSL program (the stamp is an overlay). */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(YesterdayUniforms.SEED, seed.toFloat())
        put(YesterdayUniforms.BLUR_SIGMA, blurSigma1080)
        put(YesterdayUniforms.TONE_R3, toneR.a3)
        put(YesterdayUniforms.TONE_R2, toneR.a2)
        put(YesterdayUniforms.TONE_R1, toneR.a1)
        put(YesterdayUniforms.TONE_R0, toneR.a0)
        put(YesterdayUniforms.TONE_G3, toneG.a3)
        put(YesterdayUniforms.TONE_G2, toneG.a2)
        put(YesterdayUniforms.TONE_G1, toneG.a1)
        put(YesterdayUniforms.TONE_G0, toneG.a0)
        put(YesterdayUniforms.TONE_B3, toneB.a3)
        put(YesterdayUniforms.TONE_B2, toneB.a2)
        put(YesterdayUniforms.TONE_B1, toneB.a1)
        put(YesterdayUniforms.TONE_B0, toneB.a0)
        put(YesterdayUniforms.SAT_MIX, satMix)
        put(YesterdayUniforms.GAIN_R, gainR.gain)
        put(YesterdayUniforms.OFF_R, gainR.offset)
        put(YesterdayUniforms.GAIN_G, gainG.gain)
        put(YesterdayUniforms.OFF_G, gainG.offset)
        put(YesterdayUniforms.GAIN_B, gainB.gain)
        put(YesterdayUniforms.OFF_B, gainB.offset)
        put(YesterdayUniforms.VIG_C0, vignetteC0)
        put(YesterdayUniforms.VIG_C1, vignetteC1)
        put(YesterdayUniforms.VIG_C2, vignetteC2)
        put(YesterdayUniforms.GRAIN_AMP, grainAmp)
        put(YesterdayUniforms.GRAIN_CELL, grainCell1080)
        put(YesterdayUniforms.MOTTLE_AMP, mottleAmp)
        put(YesterdayUniforms.MOTTLE_FREQ, mottleFreq)
    }

    /** Stamp metadata for the GPU preview overlay ([PhotoEffect.stamp]). */
    fun stampSpec(dateProvider: () -> DotMatrixDate.DateParts = DotMatrixDate.systemDate) =
        DotMatrixDate.StampSpec(colorRgb = DotMatrixDate.STAMP_COLOR_RGB, dateProvider = dateProvider)

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = "warm faded film · grain · vignette · date stamp"
}

/** Uniform names shared by the YESTERDAY AGSL shader and the CPU mirror. */
object YesterdayUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    const val BLUR_SIGMA = "uBlurSigma"
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
    const val VIG_C0 = "uVigC0"
    const val VIG_C1 = "uVigC1"
    const val VIG_C2 = "uVigC2"
    const val GRAIN_AMP = "uGrainAmp"
    const val GRAIN_CELL = "uGrainCell"
    const val MOTTLE_AMP = "uMottleAmp"
    const val MOTTLE_FREQ = "uMottleFreq"
}
