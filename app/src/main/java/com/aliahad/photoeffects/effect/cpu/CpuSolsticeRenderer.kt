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
import com.aliahad.photoeffects.effect.SolsticeParams
import com.aliahad.photoeffects.filter.FilterGrain

/** Row-parallel mirror of SOLSTICE_AGSL; no intermediate images or per-pixel trigonometry. */
internal class CpuSolsticeRenderer(private val p: SolsticeParams) : CpuEffect {
    override fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray {
        require(width > 0 && height > 0 && input.size.toLong() == width.toLong() * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t == 0f) return input.copyOf()
        val output = IntArray(input.size)
        val noiseSeed = EffectSeed.of(seed, p.seed)
        PixelRunner.forEachRow(height) { y ->
            val dy = ((y + 0.5f) / height - p.centerY) / p.radiusY
            for (x in 0 until width) {
                val i = y * width + x
                val pixel = input[i]
                val alpha = pixel ushr 24
                if (alpha == 0) { output[i] = pixel; continue }
                val sr = PixelMath.red(pixel)
                val sg = PixelMath.green(pixel)
                val sb = PixelMath.blue(pixel)
                val dx = ((x + 0.5f) / width - p.centerX) / p.radiusX
                val distance = dx * dx + dy * dy
                val field = 1f - PixelMath.smoothstep(0.02f, 1f, distance)
                val core = (1f - PixelMath.smoothstep(0f, 0.18f, distance)) * 0.12f
                val amount = p.light * field
                val grain = if (p.grain > 0f) FilterGrain.noise(x, y, width, height, noiseSeed, 1.4f) *
                    p.grain * (0.35f + 0.65f * field) else 0f
                val r = sr + (1f - sr) * (p.red * amount + core)
                val g = sg + (1f - sg) * (p.green * amount + core)
                val b = sb + (1f - sb) * (p.blue * amount + core)
                output[i] = PixelMath.pack(
                    PixelMath.mix(sr, (r + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sg, (g + grain).coerceIn(0f, 1f), t),
                    PixelMath.mix(sb, (b + grain).coerceIn(0f, 1f), t), alpha,
                )
            }
        }
        return output
    }
}
