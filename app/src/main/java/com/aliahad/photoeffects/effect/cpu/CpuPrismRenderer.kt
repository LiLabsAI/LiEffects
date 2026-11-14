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
import com.aliahad.photoeffects.effect.PrismParams
import kotlin.math.sqrt

/**
 * Deterministic CPU mirror of the PRISM AGSL shader.
 *
 * Pipeline (identical to [com.aliahad.photoeffects.effect.PRISM_AGSL]):
 * optional ellipse-masked 3x3 [1,2,1] blur (variant #1) -> trilinear 33^3 LUT sample ->
 * intensity blend.
 *
 * The LUT comes from [PrismLutStore] (populated from the bundled atlas at app start); when it
 * is unavailable (plain JVM tests that did not inject the atlas) an identity LUT is used so the
 * rest of the pipeline stays testable in isolation.
 */
class CpuPrismRenderer(private val params: PrismParams) : CpuEffect {

    override fun apply(
        input: IntArray,
        width: Int,
        height: Int,
        intensity: Float,
        seed: Long,
    ): IntArray {
        require(input.size == width * height) { "pixel buffer does not match $width x $height" }
        val t = intensity.coerceIn(0f, 1f)
        if (t <= 0f || width <= 0 || height <= 0) return input.copyOf()

        val lut = PrismLutStore.get(params.id) ?: PrismLutData.identity()
        val ellipse = params.ellipseBlur
        val out = IntArray(input.size)
        PixelRunner.forEachRow(height) { y ->
            val v = (y + 0.5f) / height
            val graded = FloatArray(3)
            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.visibleRed(srcPx)
                val sG = PixelMath.visibleGreen(srcPx)
                val sB = PixelMath.visibleBlue(srcPx)

                var r = sR
                var g = sG
                var b = sB
                if (ellipse) {
                    val u = (x + 0.5f) / width
                    val dx = (u - PrismParams.ELLIPSE_CX) / PrismParams.ELLIPSE_RX
                    val dy = (v - PrismParams.ELLIPSE_CY) / PrismParams.ELLIPSE_RY
                    val m = PixelMath.smoothstep(
                        1f,
                        1f + PrismParams.ELLIPSE_SOFT,
                        sqrt(dx * dx + dy * dy),
                    )
                    if (m > 0f) {
                        val x0 = (x - 1).coerceAtLeast(0)
                        val x2 = (x + 1).coerceAtMost(width - 1)
                        val y0 = (y - 1).coerceAtLeast(0)
                        val y2 = (y + 1).coerceAtMost(height - 1)
                        val p00 = input[y0 * width + x0]
                        val p10 = input[y0 * width + x]
                        val p20 = input[y0 * width + x2]
                        val p01 = input[y * width + x0]
                        val p21 = input[y * width + x2]
                        val p02 = input[y2 * width + x0]
                        val p12 = input[y2 * width + x]
                        val p22 = input[y2 * width + x2]
                        val bR = (PixelMath.visibleRed(p00) + 2f * PixelMath.visibleRed(p10) +
                            PixelMath.visibleRed(p20) + 2f * PixelMath.visibleRed(p01) + 4f * sR +
                            2f * PixelMath.visibleRed(p21) + PixelMath.visibleRed(p02) +
                            2f * PixelMath.visibleRed(p12) + PixelMath.visibleRed(p22)) * 0.0625f
                        val bG = (PixelMath.visibleGreen(p00) + 2f * PixelMath.visibleGreen(p10) +
                            PixelMath.visibleGreen(p20) + 2f * PixelMath.visibleGreen(p01) + 4f * sG +
                            2f * PixelMath.visibleGreen(p21) + PixelMath.visibleGreen(p02) +
                            2f * PixelMath.visibleGreen(p12) + PixelMath.visibleGreen(p22)) * 0.0625f
                        val bB = (PixelMath.visibleBlue(p00) + 2f * PixelMath.visibleBlue(p10) +
                            PixelMath.visibleBlue(p20) + 2f * PixelMath.visibleBlue(p01) + 4f * sB +
                            2f * PixelMath.visibleBlue(p21) + PixelMath.visibleBlue(p02) +
                            2f * PixelMath.visibleBlue(p12) + PixelMath.visibleBlue(p22)) * 0.0625f
                        r = PixelMath.mix(sR, bR, m)
                        g = PixelMath.mix(sG, bG, m)
                        b = PixelMath.mix(sB, bB, m)
                    }
                }

                lut.sample(r, g, b, graded)
                out[i] = PixelMath.pack(
                    PixelMath.mix(sR, graded[0], t),
                    PixelMath.mix(sG, graded[1], t),
                    PixelMath.mix(sB, graded[2], t),
                    alpha,
                )
            }
        }
        return out
    }
}
