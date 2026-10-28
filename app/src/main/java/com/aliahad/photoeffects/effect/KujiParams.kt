package com.aliahad.photoeffects.effect

/**
 * Parameters of the KUJI pipeline (PREQUEL "kuji" pack, 5 numbered variants).
 *
 * Fitted on the SAME demo photo from PREQUEL 2.5.0's full-resolution exports
 * (`docs/prequel-research/device-kuji/exports/kuji-{1..5}-{framed,unframed}.jpg`); see
 * `docs/prequel-research/spec/kuji-rewrite-spec.md` for the measurement tables.
 *
 * PREQUEL's KUJI tool row is Filter / Grain / Frame. Filter is 100 on every variant, Grain is 100
 * on #1..#4 and 80 on #5, and Frame is a boolean toggle that is On for all five defaults. The
 * frame itself is not procedural: the exact paper/ink overlay was recovered by diffing the framed
 * and unframed exports (the photo underneath is bit-identical), and is bundled as
 * `assets/kuji_frame_{1..5}.png`.
 *
 * Pipeline order (identical in [KUJI_AGSL] and
 * [com.aliahad.photoeffects.effect.cpu.CpuKujiRenderer]):
 *   1. per-channel cubic tone curves
 *   2. saturation mix + per-channel gain/offset
 *   3. monochrome film grain
 *   4. paper frame overlay (premultiplied alpha over the graded photo)
 *   5. intensity blend
 *
 * RETUNING = EDITING THE FIVE ROWS IN [KujiFamily] ONLY.
 */
data class KujiParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Fixed per-variant seed (combined with the per-photo seed at render time). */
    val seed: Int,
    // ---- measured PREQUEL tool row (reference; the look is driven by the fitted values below) ----
    /** "Filter" tool value (0..100). */
    val filter: Int = 100,
    /** "Grain" tool value (0..100). */
    val grain: Int = 100,
    /** "Frame" toggle (PREQUEL's discrete Off/On control, On for every default). */
    val frame: Boolean = true,
    // ---- fitted transformation ----
    val toneR: CubicTone,
    val toneG: CubicTone,
    val toneB: CubicTone,
    val satMix: Float,
    val gainR: GainOffset,
    val gainG: GainOffset,
    val gainB: GainOffset,
    /** Monochrome grain amplitude (luma std) and cell size per 1080-scale min dimension. */
    val grainAmp: Float,
    val grainCell1080: Float = 1.35f,
    /** Bundled frame overlay asset recovered from PREQUEL's framed export. */
    val frameAsset: String,
    val defaultIntensity: Int = 100,
) {
    /** Uniform payload pushed into the KUJI AGSL program. */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(KujiUniforms.SEED, seed.toFloat())
        put(KujiUniforms.TONE_R3, toneR.a3)
        put(KujiUniforms.TONE_R2, toneR.a2)
        put(KujiUniforms.TONE_R1, toneR.a1)
        put(KujiUniforms.TONE_R0, toneR.a0)
        put(KujiUniforms.TONE_G3, toneG.a3)
        put(KujiUniforms.TONE_G2, toneG.a2)
        put(KujiUniforms.TONE_G1, toneG.a1)
        put(KujiUniforms.TONE_G0, toneG.a0)
        put(KujiUniforms.TONE_B3, toneB.a3)
        put(KujiUniforms.TONE_B2, toneB.a2)
        put(KujiUniforms.TONE_B1, toneB.a1)
        put(KujiUniforms.TONE_B0, toneB.a0)
        put(KujiUniforms.SAT_MIX, satMix)
        put(KujiUniforms.GAIN_R, gainR.gain)
        put(KujiUniforms.OFF_R, gainR.offset)
        put(KujiUniforms.GAIN_G, gainG.gain)
        put(KujiUniforms.OFF_G, gainG.offset)
        put(KujiUniforms.GAIN_B, gainB.gain)
        put(KujiUniforms.OFF_B, gainB.offset)
        put(KujiUniforms.GRAIN_AMP, grainAmp)
        put(KujiUniforms.GRAIN_CELL, grainCell1080)
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        append("paper frame · grain")
        if (grain >= 90) append(" · heavy grain")
    }
}

/** Uniform names shared by the KUJI AGSL shader and the CPU mirror. */
object KujiUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
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
    /** Frame overlay bitmap input (asset-backed, bound by the preview). */
    const val FRAME_SHADER = "uFrame"
}
