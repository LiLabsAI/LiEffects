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

package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.EffectSeed
import com.aliahad.photoeffects.effect.MarqueeParams
import com.aliahad.photoeffects.filter.FilterGrain
import kotlin.math.abs

/** Deterministic mirror of [com.aliahad.photoeffects.effect.MARQUEE_AGSL], stage for stage. */
internal class CpuMarqueeRenderer(private val p: MarqueeParams) : CpuEffect {

    override fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray {
        require(input.size == width * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t == 0f) return input.copyOf()
        val output = IntArray(input.size)
        val noiseSeed = EffectSeed.of(seed, p.seed)
        PixelRunner.forEachRow(height) { y ->
            val uy = (y + 0.5f) / height - 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val pixel = input[i]
                val alpha = pixel ushr 24
                if (alpha == 0) {
                    output[i] = pixel
                    continue
                }
                val sr = PixelMath.red(pixel)
                val sg = PixelMath.green(pixel)
                val sb = PixelMath.blue(pixel)

                val warm = ((sr - sb) * 3f).coerceIn(0f, 1f) * ((sr - sg) * 6f).coerceIn(0f, 1f)
                val protect = warm * p.skinProtect
                val coolAmount = 1f - protect
                var r = (sr * p.exposure * PixelMath.mix(1f, p.coolR, coolAmount)).coerceIn(0f, 1f)
                var g = (sg * p.exposure * PixelMath.mix(1f, p.coolG, coolAmount)).coerceIn(0f, 1f)
                var b = (sb * p.exposure * PixelMath.mix(1f, p.coolB, coolAmount)).coerceIn(0f, 1f)
                val lum = PixelMath.luma(r, g, b)

                val saturation = PixelMath.mix(p.saturation, p.skinSaturation, protect)
                r = PixelMath.mix(lum, r, saturation)
                g = PixelMath.mix(lum, g, saturation)
                b = PixelMath.mix(lum, b, saturation)

                r = PixelMath.mix(r, r * r * (3f - 2f * r), p.curve)
                g = PixelMath.mix(g, g * g * (3f - 2f * g), p.curve)
                b = PixelMath.mix(b, b * b * (3f - 2f * b), p.curve)

                r = shoulder(r)
                g = shoulder(g)
                b = shoulder(b)

                r = p.blackR + r * (p.white - p.blackR)
                g = p.blackG + g * (p.white - p.blackG)
                b = p.blackB + b * (p.white - p.blackB)

                val shadowMask = 1f - PixelMath.smoothstep(0.05f, 0.72f, lum)
                val highlightMask = PixelMath.smoothstep(0.55f, 0.95f, lum)
                r = tint(r, p.shadowR, shadowMask)
                g = tint(g, p.shadowG, shadowMask)
                b = tint(b, p.shadowB, shadowMask)
                r = tint(r, p.highlightR, highlightMask)
                g = tint(g, p.highlightG, highlightMask)
                b = tint(b, p.highlightB, highlightMask)

                val ux = (x + 0.5f) / width - 0.5f
                val shade = shade(ux, uy)
                r = (r * shade).coerceIn(0f, 1f)
                g = (g * shade).coerceIn(0f, 1f)
                b = (b * shade).coerceIn(0f, 1f)

                val grain = FilterGrain.noise(x, y, width, height, noiseSeed, 1.2f) *
                    p.grain * PixelMath.smoothstep(0f, 0.10f, lum) *
                    (1f - PixelMath.smoothstep(0.65f, 1f, lum))
                output[i] = PixelMath.pack(
                    PixelMath.mix(sr, (r + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sg, (g + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sb, (b + grain).coerceIn(0f, 1f), t),
                    alpha,
                )
            }
        }
        return output
    }

    private fun shoulder(v: Float): Float {
        val over = (v - KNEE).coerceAtLeast(0f)
        return v - over + over / (1f + p.shoulder * over / (1f - KNEE))
    }

    private fun tint(v: Float, amount: Float, mask: Float): Float =
        v + amount * (if (amount >= 0f) 1f - v else v) * mask

    private fun shade(ux: Float, uy: Float): Float {
        val edge = PixelMath.smoothstep(0.10f, 0.50f, ux * ux + uy * uy)
        return if (p.premiere) {
            val key = PixelMath.smoothstep(-0.35f, 0.45f, ux * 0.80f + uy * 0.60f)
            1f - p.falloff * (edge * 0.65f + key * 0.35f)
        } else {
            val band = PixelMath.smoothstep(0.02f, 0.42f, abs(ux + 0.12f))
            val cinema = PixelMath.smoothstep(0.18f, 0.50f, abs(uy))
            1f - p.falloff * (edge * 0.40f + band * 0.35f + cinema * 0.25f)
        }
    }

    private companion object {
        const val KNEE = 0.72f
    }
}