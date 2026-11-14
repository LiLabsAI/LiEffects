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
import com.aliahad.photoeffects.effect.ScreenprintParams
import com.aliahad.photoeffects.filter.FilterGrain
import kotlin.math.max

/** Direct per-pixel halftone mirror; the same two screens and edge widths are used in AGSL. */
internal class CpuScreenprintRenderer(private val p: ScreenprintParams) : CpuEffect {
    override fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray {
        require(width > 0 && height > 0 && input.size.toLong() == width.toLong() * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t == 0f) return input.copyOf()
        val output = IntArray(input.size)
        val noiseSeed = EffectSeed.of(seed, p.seed)
        val cell = max(2f, p.pitch * max(width, height) / 1600f)
        val edge = max(0.02f, 0.75f / cell)
        PixelRunner.forEachRow(height) { y ->
            val py = y + 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val pixel = input[i]
                val alpha = pixel ushr 24
                if (alpha == 0) { output[i] = pixel; continue }
                val sr = PixelMath.red(pixel)
                val sg = PixelMath.green(pixel)
                val sb = PixelMath.blue(pixel)
                val shade = 1f - PixelMath.luma(sr, sg, sb)
                val px = x + 0.5f
                val gx = (px * p.cosine + py * p.sine) / cell
                val gy = (-px * p.sine + py * p.cosine) / cell
                val amount = dot(gx, gy, shade, edge)
                var r = p.paperR * PixelMath.mix(1f, p.inkR, amount)
                var g = p.paperG * PixelMath.mix(1f, p.inkG, amount)
                var b = p.paperB * PixelMath.mix(1f, p.inkB, amount)
                if (p.dual) {
                    // Crossed screen with a fraction-cell offset: no second source sample needed.
                    val secondShade = (shade * 0.72f + (sb - sr) * 0.22f).coerceIn(0f, 1f)
                    val second = dot(-gy + 0.22f, gx + 0.12f, secondShade, edge)
                    r *= PixelMath.mix(1f, p.secondR, second)
                    g *= PixelMath.mix(1f, p.secondG, second)
                    b *= PixelMath.mix(1f, p.secondB, second)
                }
                val grain = if (p.grain > 0f) FilterGrain.noise(x, y, width, height, noiseSeed, 1.8f) * p.grain else 0f
                output[i] = PixelMath.pack(
                    PixelMath.mix(sr, (r + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sg, (g + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sb, (b + grain).coerceIn(0f, 1f), t), alpha,
                )
            }
        }
        return output
    }

    private fun dot(x: Float, y: Float, shade: Float, edge: Float): Float {
        val dx = PixelMath.fract(x) - 0.5f
        val dy = PixelMath.fract(y) - 0.5f
        val radius = 0.5f * shade
        val mask = 1f - PixelMath.smoothstep(radius - edge, radius + edge, dx * dx + dy * dy)
        val fading = mask * PixelMath.smoothstep(0f, 0.10f, shade)
        return PixelMath.mix(fading, 1f, PixelMath.smoothstep(0.88f, 1f, shade))
    }
}
