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
 * Shared Paperlight parameters. Processing order is cubic channel curves,
 * saturation and gain, monochrome grain, a premultiplied paper overlay, then
 * intensity blending. All five variants default to enabling their bundled frame.
 */
data class PaperlightParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Fixed per-variant seed (combined with the per-photo seed at render time). */
    val seed: Int,
    // ---- calibrated tool values; the look is driven by the values below ----
    /** "Filter" tool value (0..100). */
    val filter: Int = 100,
    /** "Grain" tool value (0..100). */
    val grain: Int = 100,
    /** "Frame" toggle (Off/On, enabled for every default). */
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
    /** Bundled reference-derived paper-frame asset. */
    val frameAsset: String,
    val defaultIntensity: Int = 100,
) {
    /** Uniform payload pushed into the PAPERLIGHT AGSL program. */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(PaperlightUniforms.SEED, seed.toFloat())
        put(PaperlightUniforms.TONE_R3, toneR.a3)
        put(PaperlightUniforms.TONE_R2, toneR.a2)
        put(PaperlightUniforms.TONE_R1, toneR.a1)
        put(PaperlightUniforms.TONE_R0, toneR.a0)
        put(PaperlightUniforms.TONE_G3, toneG.a3)
        put(PaperlightUniforms.TONE_G2, toneG.a2)
        put(PaperlightUniforms.TONE_G1, toneG.a1)
        put(PaperlightUniforms.TONE_G0, toneG.a0)
        put(PaperlightUniforms.TONE_B3, toneB.a3)
        put(PaperlightUniforms.TONE_B2, toneB.a2)
        put(PaperlightUniforms.TONE_B1, toneB.a1)
        put(PaperlightUniforms.TONE_B0, toneB.a0)
        put(PaperlightUniforms.SAT_MIX, satMix)
        put(PaperlightUniforms.GAIN_R, gainR.gain)
        put(PaperlightUniforms.OFF_R, gainR.offset)
        put(PaperlightUniforms.GAIN_G, gainG.gain)
        put(PaperlightUniforms.OFF_G, gainG.offset)
        put(PaperlightUniforms.GAIN_B, gainB.gain)
        put(PaperlightUniforms.OFF_B, gainB.offset)
        put(PaperlightUniforms.GRAIN_AMP, grainAmp)
        put(PaperlightUniforms.GRAIN_CELL, grainCell1080)
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        append("paper frame · grain")
        if (grain >= 90) append(" · heavy grain")
    }
}

/** Uniform names shared by the PAPERLIGHT AGSL shader and the CPU mirror. */
object PaperlightUniforms {
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
