/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliahad.photoeffects.effect

/**
 * Shared Daymark parameters. Processing order is pre-blur, cubic channel curves,
 * saturation and gain, vignette, grain and mottling, a separately drawn date stamp,
 * and the final intensity blend. Retune the single row in DaymarkFamily.
 */
data class DaymarkParams(
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
    /** Uniform payload pushed into the DAYMARK AGSL program (the stamp is an overlay). */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(DaymarkUniforms.SEED, seed.toFloat())
        put(DaymarkUniforms.BLUR_SIGMA, blurSigma1080)
        put(DaymarkUniforms.TONE_R3, toneR.a3)
        put(DaymarkUniforms.TONE_R2, toneR.a2)
        put(DaymarkUniforms.TONE_R1, toneR.a1)
        put(DaymarkUniforms.TONE_R0, toneR.a0)
        put(DaymarkUniforms.TONE_G3, toneG.a3)
        put(DaymarkUniforms.TONE_G2, toneG.a2)
        put(DaymarkUniforms.TONE_G1, toneG.a1)
        put(DaymarkUniforms.TONE_G0, toneG.a0)
        put(DaymarkUniforms.TONE_B3, toneB.a3)
        put(DaymarkUniforms.TONE_B2, toneB.a2)
        put(DaymarkUniforms.TONE_B1, toneB.a1)
        put(DaymarkUniforms.TONE_B0, toneB.a0)
        put(DaymarkUniforms.SAT_MIX, satMix)
        put(DaymarkUniforms.GAIN_R, gainR.gain)
        put(DaymarkUniforms.OFF_R, gainR.offset)
        put(DaymarkUniforms.GAIN_G, gainG.gain)
        put(DaymarkUniforms.OFF_G, gainG.offset)
        put(DaymarkUniforms.GAIN_B, gainB.gain)
        put(DaymarkUniforms.OFF_B, gainB.offset)
        put(DaymarkUniforms.VIG_C0, vignetteC0)
        put(DaymarkUniforms.VIG_C1, vignetteC1)
        put(DaymarkUniforms.VIG_C2, vignetteC2)
        put(DaymarkUniforms.GRAIN_AMP, grainAmp)
        put(DaymarkUniforms.GRAIN_CELL, grainCell1080)
        put(DaymarkUniforms.MOTTLE_AMP, mottleAmp)
        put(DaymarkUniforms.MOTTLE_FREQ, mottleFreq)
    }

    /** Stamp metadata for the GPU preview overlay ([PhotoEffect.stamp]). */
    fun stampSpec(dateProvider: () -> DotMatrixDate.DateParts = DotMatrixDate.systemDate) =
        DotMatrixDate.StampSpec(colorRgb = DotMatrixDate.STAMP_COLOR_RGB, dateProvider = dateProvider)

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = "warm faded film · grain · vignette · date stamp"
}

/** Uniform names shared by the DAYMARK AGSL shader and the CPU mirror. */
object DaymarkUniforms {
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
