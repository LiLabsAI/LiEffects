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
 * Dark-blue cinematic grade. Unlike the pointwise filter recipes, the look is built from a
 * filmic tone curve, gamut-safe split toning and hue-selective saturation, so the numbers here
 * are curve/tint strengths rather than a flat white balance.
 */
data class MarqueeParams(
    val id: String,
    val displayName: String,
    val description: String,
    val summary: String,
    /** true = broad upper-left key (Premiere); false = narrow noir band (Midnight). */
    val premiere: Boolean,
    val seed: Int,
    val exposure: Float,
    /** Ambient white balance pushed cool; faded out on warm hues so faces stay natural. */
    val coolR: Float,
    val coolG: Float,
    val coolB: Float,
    /** Blend of the smoothstep S-curve into the linear signal: midtone contrast, never clips. */
    val curve: Float,
    /** Highlight rolloff above the knee; larger values keep whites further from clipping. */
    val shoulder: Float,
    val blackR: Float,
    val blackG: Float,
    val blackB: Float,
    val white: Float,
    val shadowR: Float,
    val shadowG: Float,
    val shadowB: Float,
    val highlightR: Float,
    val highlightG: Float,
    val highlightB: Float,
    val saturation: Float,
    /** Saturation kept for warm skin hues, so faces survive the cool mute. */
    val skinSaturation: Float,
    /** How strongly warm hues are exempted from both the cool gain and the global mute. */
    val skinProtect: Float,
    val falloff: Float,
    val grain: Float,
    val defaultIntensity: Int,
) {
    fun toUniforms(): Map<String, Float> = mapOf(
        "uStyle" to if (premiere) 0f else 1f,
        "uSeed" to seed.toFloat(),
        "uExposure" to exposure,
        "uCoolR" to coolR,
        "uCoolG" to coolG,
        "uCoolB" to coolB,
        "uCurve" to curve,
        "uShoulder" to shoulder,
        "uBlackR" to blackR,
        "uBlackG" to blackG,
        "uBlackB" to blackB,
        "uWhite" to white,
        "uShadowR" to shadowR,
        "uShadowG" to shadowG,
        "uShadowB" to shadowB,
        "uHighlightR" to highlightR,
        "uHighlightG" to highlightG,
        "uHighlightB" to highlightB,
        "uSaturation" to saturation,
        "uSkinSaturation" to skinSaturation,
        "uSkinProtect" to skinProtect,
        "uFalloff" to falloff,
        "uGrain" to grain,
    )
}