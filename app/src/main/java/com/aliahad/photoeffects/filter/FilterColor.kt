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

package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.cpu.PixelMath

/** Shared pointwise grade, evaluated before channel separation and procedural grain. */
internal class FilterColor(private val p: FilterParams) {
    private val highlights = p.highlights.coerceIn(-100, 100) / 100f
    private val shadows = p.shadows.coerceIn(-100, 100) / 100f

    fun apply(inputR: Float, inputG: Float, inputB: Float, out: FloatArray) {
        var r = inputR; var g = inputG; var b = inputB
        val baseR = r; val baseG = g; val baseB = b
        r *= p.exposure; g *= p.exposure; b *= p.exposure
        val gray = PixelMath.luma(r, g, b)
        val shadow = 1f - PixelMath.smoothstep(0.10f, 0.65f, gray)
        val highlight = PixelMath.smoothstep(0.45f, 1f, gray)
        r = ((PixelMath.mix(gray, r, p.saturation) - 0.5f) * p.contrast + 0.5f) * p.gainR
        g = ((PixelMath.mix(gray, g, p.saturation) - 0.5f) * p.contrast + 0.5f) * p.gainG
        b = ((PixelMath.mix(gray, b, p.saturation) - 0.5f) * p.contrast + 0.5f) * p.gainB
        r += p.shadowR * shadow + p.highlightR * highlight
        g += p.shadowG * shadow + p.highlightG * highlight
        b += p.shadowB * shadow + p.highlightB * highlight
        r = (p.black + r * (p.white - p.black)).coerceIn(0f, 1f)
        g = (p.black + g * (p.white - p.black)).coerceIn(0f, 1f)
        b = (p.black + b * (p.white - p.black)).coerceIn(0f, 1f)
        val strength = p.strength.coerceIn(0f, 1f)
        r = PixelMath.mix(baseR, r, strength)
        g = PixelMath.mix(baseG, g, strength)
        b = PixelMath.mix(baseB, b, strength)
        // Original luminance-selective curves. Each stage is monotone on neutral
        // ramps and blends using available RGB headroom, without spatial halos.
        if (highlights != 0f) {
            val luma = PixelMath.luma(r, g, b).coerceIn(0f, 1f)
            val mask = PixelMath.smoothstep(0.10f, 0.90f, luma)
            if (highlights > 0f) {
                val lift = 0.5f * highlights * mask
                r += lift * (1f - r); g += lift * (1f - g); b += lift * (1f - b)
            } else {
                val gain = 1f + 0.2f * highlights * mask
                r *= gain; g *= gain; b *= gain
            }
        }
        if (shadows != 0f) {
            val luma = PixelMath.luma(r, g, b).coerceIn(0f, 1f)
            if (shadows > 0f) {
                val lift = 0.16f * shadows * (1f - PixelMath.smoothstep(0f, 0.8f, luma))
                r += lift * (1f - r); g += lift * (1f - g); b += lift * (1f - b)
            } else {
                val gain = 1f + shadows * (1f - luma) * (1f - luma)
                r *= gain; g *= gain; b *= gain
            }
        }
        out[0] = r; out[1] = g; out[2] = b
    }
}
