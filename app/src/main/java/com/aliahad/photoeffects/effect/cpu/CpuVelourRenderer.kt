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
import com.aliahad.photoeffects.effect.VelourParams
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic Velour renderer. Processing order is global soft focus, masked
 * variable blur, channel curves, saturation and gain, monochrome grain and intensity
 * blending. Blur radii and the radial mask are shared with the GPU implementation.
 */
class CpuVelourRenderer(private val params: VelourParams) : CpuEffect {

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

        val n = input.size
        val seedI = EffectSeed.of(seed, params.seed)
        val minDim = min(width, height)
        val sc = minDim / 1080f

        // ---------------------------------------------------------------- constants
        val sigma = params.sigma1080 * sc
        val blurRadius = params.blurRadius1080 * sc
        val grainCell = max(params.grainCell1080 * sc, 1f)
        val grainAmp = params.grainAmp / 0.2887f
        val mottleAmp = params.mottleAmp
        val mottleFreq = params.mottleFreq
        val mottleAmp2 = params.mottleAmp2
        val mottleFreq2 = params.mottleFreq2
        val toneR = params.toneR
        val toneG = params.toneG
        val toneB = params.toneB
        val satMix = params.satMix.coerceIn(0f, 1f)
        val gainR = params.gainR
        val gainG = params.gainG
        val gainB = params.gainB

        // ---------------------------------------------------------------- planes
        val baseR = FloatArray(n) { PixelMath.visibleRed(input[it]) }
        val baseG = FloatArray(n) { PixelMath.visibleGreen(input[it]) }
        val baseB = FloatArray(n) { PixelMath.visibleBlue(input[it]) }

        // 1. small global soft-focus gaussian
        var (r1, g1, b1) = FastGaussian.blurPlanes(baseR, baseG, baseB, width, height, sigma)

        // 2. masked variable blur: B = B1*(1-mask) + B2*mask
        if (blurRadius > 0.05f) {
            val (r2, g2, b2) = FastGaussian.blurPlanes(r1, g1, b1, width, height, blurRadius)
            val mask = BlurMaskStore.requireMask()
            for (y in 0 until height) {
                val v = (y + 0.5f) / height
                for (x in 0 until width) {
                    val i = y * width + x
                    val m = mask.sample((x + 0.5f) / width, v)
                    r1[i] = PixelMath.mix(r1[i], r2[i], m)
                    g1[i] = PixelMath.mix(g1[i], g2[i], m)
                    b1[i] = PixelMath.mix(b1[i], b2[i], m)
                }
            }
        }

        val out = IntArray(n)
        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.visibleRed(srcPx)
                val sG = PixelMath.visibleGreen(srcPx)
                val sB = PixelMath.visibleBlue(srcPx)

                // 3. per-channel cubic tone curves
                var cR = toneR.eval(r1[i])
                var cG = toneG.eval(g1[i])
                var cB = toneB.eval(b1[i])

                // 4. saturation mix + per-channel gain/offset
                val gray = PixelMath.luma(cR, cG, cB)
                val keep = 1f - satMix
                cR = gainR.eval(PixelMath.mix(gray, cR, keep))
                cG = gainG.eval(PixelMath.mix(gray, cG, keep))
                cB = gainB.eval(PixelMath.mix(gray, cB, keep))

                // 5a. Texture mottling, two luma-neutral octaves (fine film texture)
                val mottle1 = (PixelMath.fbm2(
                    (x + 0.5f) / minDim * mottleFreq,
                    fy / minDim * mottleFreq,
                    seedI + 53,
                ) - 0.5f) * 2f * mottleAmp
                val mottle2 = (PixelMath.fbm2(
                    (x + 0.5f) / minDim * mottleFreq2,
                    fy / minDim * mottleFreq2,
                    seedI + 71,
                ) - 0.5f) * 2f * mottleAmp2
                val textureK = (1f + mottle1 + mottle2).coerceIn(0.8f, 1.2f)
                cR *= textureK
                cG *= textureK
                cB *= textureK

                // 5. monochrome film grain
                val gix = floor((x + 0.5f) / grainCell).toInt()
                val giy = floor(fy / grainCell).toInt()
                val gv = PixelMath.hashF(gix, giy, seedI + 37)
                val grain = (gv - 0.5f) * grainAmp
                cR = (cR + grain).coerceIn(0f, 1f)
                cG = (cG + grain).coerceIn(0f, 1f)
                cB = (cB + grain).coerceIn(0f, 1f)

                // 6. intensity blend
                val rr = PixelMath.mix(sR, cR, t)
                val gg = PixelMath.mix(sG, cG, t)
                val bb = PixelMath.mix(sB, cB, t)
                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}
