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
 * Parameters of the Motes texture pipeline, shared by the deterministic CPU renderer
 * and live AGSL preview. Retune variants in MotesFamily rather than either renderer.
 */
data class MotesParams(
    val id: String,
    val displayName: String,
    val description: String,
    /** Per-variant fixed seed (101..105); combined with the photo seed. */
    val seed: Int,
    // ---- color grade ----
    /**
     * Row-major 3x4 affine grade calibrated per variant:
     *   r' = g0*r + g1*g + g2*b + g3
     *   g' = g4*r + g5*g + g6*b + g7
     *   b' = g8*r + g9*g + g10*b + g11
     * Least-squares on the baseline->export pairs (verification report §4); keeps each
     * variant's measured cast (teal / blue / green / warm / brown) instead of a scalar warm tint.
     */
    val grade: List<Float> = IDENTITY_GRADE,
    // ---- optics ----
    val aberration: Float = 0f,    // 0..1 radial RGB fringing
    val blur: Float = 0f,         // 0..1 soft focus amount
    val halation: Float = 0f,     // 0..1 warm highlight bloom
    // ---- grain ----
    val grain: Float = 0f,        // 0..1 film grain strength (soft-light)
    // ---- dust ----
    val dust: Float = 0f,         // 0..1 dust amount
    val dustWhite: Boolean = true, // true = white specks, false = black
    val dustSize: Float = 1f,     // 0.5..3, smaller = finer specks
    val dustOpacity: Float = 0.7f, // 0.5..0.85
    // ---- scratches / texture ----
    val scratches: Float = 0f,    // 0..1
    val texture: Float = 0f,      // 0..1 paper/lint mottling
    // ---- vignette ----
    val vignette: Float = 0f,     // 0..1
    val defaultIntensity: Int = 100,
) {
    val dustColor: DustColor
        get() = if (dustWhite) DustColor.WHITE else DustColor.BLACK

    /** Uniform payload pushed into the AGSL shader (seed is combined per-photo by the renderer). */
    fun toUniforms(): Map<String, Float> = buildMap {
        put(MotesUniforms.SEED, seed.toFloat())
        val g = if (grade.size == 12) grade else IDENTITY_GRADE
        for (i in 0 until 12) put(MotesUniforms.GRADE[i], g[i])
        put(MotesUniforms.ABERRATION, aberration.coerceIn(0f, 1f))
        put(MotesUniforms.BLUR, blur.coerceIn(0f, 1f))
        put(MotesUniforms.LUMEN, halation.coerceIn(0f, 1f))
        put(MotesUniforms.GRAIN, grain.coerceIn(0f, 1f))
        put(MotesUniforms.DUST, dust.coerceIn(0f, 1f))
        put(MotesUniforms.DUST_WHITE, if (dustWhite) 1f else 0f)
        put(MotesUniforms.DUST_SIZE, dustSize.coerceIn(0.25f, 4f))
        put(MotesUniforms.DUST_OPACITY, dustOpacity.coerceIn(0f, 1f))
        put(MotesUniforms.SCRATCHES, scratches.coerceIn(0f, 1f))
        put(MotesUniforms.TEXTURE, texture.coerceIn(0f, 1f))
        put(MotesUniforms.VIGNETTE, vignette.coerceIn(0f, 1f))
    }

    /** One-line description shown under the effect name in the panel. */
    fun summary(): String = buildString {
        if (dust > 0.001f) {
            append(if (dustWhite) "white dust" else "black dust")
        } else {
            append("film grade")
        }
        if (grain > 0.02f) append(" · grain")
        if (scratches > 0.05f) append(" · scratches")
        if (halation > 0.05f) append(" · halation")
    }

    companion object {
        /** Identity affine grade (no colour change). */
        val IDENTITY_GRADE: List<Float> = listOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
        )
    }
}

/** Positive dust = white specks, negative = black specks. */
enum class DustColor { WHITE, BLACK }

/** Uniform names shared by the AGSL shader and the CPU mirror. */
object MotesUniforms {
    const val INTENSITY = "uIntensity"
    const val SEED = "uSeed"
    const val RESOLUTION = "uResolution"
    /** 12 affine grade uniforms, row-major 3x4 (uGrade0..uGrade11). */
    val GRADE: List<String> = (0 until 12).map { "uGrade$it" }
    const val ABERRATION = "uAberration"
    const val BLUR = "uBlur"
    const val LUMEN = "uLumen"
    const val GRAIN = "uGrain"
    const val DUST = "uDust"
    const val DUST_WHITE = "uDustWhite"
    const val DUST_SIZE = "uDustSize"
    const val DUST_OPACITY = "uDustOpacity"
    const val SCRATCHES = "uScratches"
    const val TEXTURE = "uTexture"
    const val VIGNETTE = "uVignette"
}
