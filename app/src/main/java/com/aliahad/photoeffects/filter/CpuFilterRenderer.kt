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

import com.aliahad.photoeffects.effect.CpuEffect
import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.effect.cpu.PixelRunner
import com.aliahad.photoeffects.effect.EffectSeed
import kotlin.math.sqrt

/** Pure Kotlin mirror: spatial processing, colour blend, signed tone controls, then grain. */
class CpuFilterRenderer(private val params: FilterParams) : CpuEffect {
    override fun apply(input: IntArray, width: Int, height: Int, intensity: Float, seed: Long): IntArray {
        require(input.size == width * height)
        val t = intensity.coerceIn(0f, 1f)
        if (t <= 0f || width <= 0 || height <= 0) return input.copyOf()
        val p = params
        val level = p.intensity.coerceIn(0, 100) / 100f
        val scale = width / 1600f
        val focused = if (p.softFocus > 0f && level > 0f) DirectionalFocus.render(
            input, width, height, p.softFocus * level * (1f + p.focusRadiusCurve * (1f - level)) * scale,
            (p.focusSpread + p.focusSpreadGrowth * level) * scale, p.focusDetail,
            p.focusDetailRadius * scale, p.focusLift,
        ) else input
        val sharpen = p.sharpen.coerceIn(0, 100) / 100f
        val grain = p.grain.coerceIn(0, 100) / 100f
        val color = FilterColor(p)
        val aberration = p.aberration.coerceIn(0, 100) / 100f * p.aberrationRadius * 2f / 1600f
        val grainSeed = EffectSeed.of(seed, p.grainSeed)

        // Aberration samples the graded image at channel offsets. Grade once so the two displaced
        // samples do not re-run sharpen/grade (the earlier implementation graded three times).
        val graded = if (aberration > 0f) IntArray(input.size) else null
        if (graded != null) {
            PixelRunner.forEachRow(height) { y ->
                val out = FloatArray(3)
                for (x in 0 until width) {
                    val i = y * width + x
                    var r = PixelMath.red(focused[i])
                    var g = PixelMath.green(focused[i])
                    var b = PixelMath.blue(focused[i])
                    if (sharpen > 0f) {
                        var br = 0f; var bg = 0f; var bb = 0f
                        for (dy in -1..1) for (dx in -1..1) {
                            val q = input[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                            val w = (if (dx == 0) 2f else 1f) * (if (dy == 0) 2f else 1f) / 16f
                            br += PixelMath.red(q) * w
                            bg += PixelMath.green(q) * w
                            bb += PixelMath.blue(q) * w
                        }
                        r = (r + (r - br) * sharpen).coerceIn(0f, 1f)
                        g = (g + (g - bg) * sharpen).coerceIn(0f, 1f)
                        b = (b + (b - bb) * sharpen).coerceIn(0f, 1f)
                    }
                    color.apply(r, g, b, out)
                    graded[i] = PixelMath.pack(out[0], out[1], out[2], input[i] ushr 24)
                }
            }
        }

        val out = IntArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val gradeOut = if (graded == null) FloatArray(3) else null
            val sample = if (graded != null) FloatArray(3) else null
            for (x in 0 until width) {
                val i = y * width + x
                val src = input[i]
                val sr = PixelMath.red(src)
                val sg = PixelMath.green(src)
                val sb = PixelMath.blue(src)
                var r: Float
                var g: Float
                var b: Float
                if (graded != null) {
                    val gp = graded[i]
                    r = PixelMath.red(gp)
                    g = PixelMath.green(gp)
                    b = PixelMath.blue(gp)
                    // Opposing radial channel magnifications, applied after the grade so
                    // even a monochrome recipe can produce coloured edge fringes.
                    val dx = (x + 0.5f - width * 0.5f) * aberration
                    val dy = (y + 0.5f - height * 0.5f) * aberration
                    val sampleBuf = requireNotNull(sample)
                    val redCoverage = FilterSoftFocus.bilinear(
                        graded, width, height, x + dx, y + dy, sampleBuf,
                    )
                    r = PixelMath.mix(r, sampleBuf[0], redCoverage)
                    val blueCoverage = FilterSoftFocus.bilinear(
                        graded, width, height, x - dx, y - dy, sampleBuf,
                    )
                    b = PixelMath.mix(b, sampleBuf[2], blueCoverage)
                } else {
                    r = PixelMath.red(focused[i])
                    g = PixelMath.green(focused[i])
                    b = PixelMath.blue(focused[i])
                    if (sharpen > 0f) {
                        var br = 0f; var bg = 0f; var bb = 0f
                        for (dy in -1..1) for (dx in -1..1) {
                            val q = input[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                            val w = (if (dx == 0) 2f else 1f) * (if (dy == 0) 2f else 1f) / 16f
                            br += PixelMath.red(q) * w
                            bg += PixelMath.green(q) * w
                            bb += PixelMath.blue(q) * w
                        }
                        r = (r + (r - br) * sharpen).coerceIn(0f, 1f)
                        g = (g + (g - bg) * sharpen).coerceIn(0f, 1f)
                        b = (b + (b - bb) * sharpen).coerceIn(0f, 1f)
                    }
                    val gradeBuf = requireNotNull(gradeOut)
                    color.apply(r, g, b, gradeBuf)
                    r = gradeBuf[0]; g = gradeBuf[1]; b = gradeBuf[2]
                }
                if (grain > 0f) {
                    val luma = PixelMath.luma(r, g, b).coerceIn(0f, 1f)
                    val response = 0.35f + 0.65f * sqrt(4f * luma * (1f - luma))
                    val noise = FilterGrain.noise(x, y, width, height, grainSeed, p.grainSize) * grain * 0.18f * response
                    r = (r + noise).coerceIn(0f, 1f)
                    g = (g + noise).coerceIn(0f, 1f)
                    b = (b + noise).coerceIn(0f, 1f)
                }
                out[i] = PixelMath.pack(PixelMath.mix(sr, r, t), PixelMath.mix(sg, g, t), PixelMath.mix(sb, b, t), src ushr 24)
            }
        }
        return out
    }
}
