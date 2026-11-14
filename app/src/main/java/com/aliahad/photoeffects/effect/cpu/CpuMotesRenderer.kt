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
import com.aliahad.photoeffects.effect.MotesParams
import com.aliahad.photoeffects.effect.EffectSeed
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Deterministic row-parallel Motes renderer. Uses the shared grading, bloom,
 * chromatic fringing, grain and dust parameters, then blends at the selected intensity.
 */
class CpuMotesRenderer(private val params: MotesParams) : CpuEffect {

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
        val aspect = width.toFloat() / height.toFloat()

        // ---------------------------------------------------------------- 2b. soft focus
        val blurred: IntArray? = if (params.blur > 0.001f) {
            val radius = max(1, (params.blur * 6f * sc + 0.5f).toInt())
            BoxBlur.blurArgb(input, width, height, radius)
        } else {
            null
        }
        val blurW = params.blur.coerceIn(0f, 1f)

        // ---------------------------------------------------------------- 3. halation mask
        val lumenMask: FloatArray? = if (params.halation > 0.001f) {
            val hi = FloatArray(n) { i ->
                val l = PixelMath.luma(
                    PixelMath.visibleRed(input[i]),
                    PixelMath.visibleGreen(input[i]),
                    PixelMath.visibleBlue(input[i]),
                )
                (l - 0.65f).coerceAtLeast(0f)
            }
            val radius = max(1, ((2.5f + params.halation * 4f) * sc + 0.5f).toInt())
            BoxBlur.blurFloat(hi, width, height, radius)
        } else {
            null
        }

        // ---------------------------------------------------------------- constants
        val grade = if (params.grade.size == 12) params.grade else MotesParams.IDENTITY_GRADE
        val aberration = params.aberration.coerceIn(0f, 1f)
        val grain = params.grain.coerceIn(0f, 1f)
        val dust = params.dust.coerceIn(0f, 1f)
        val dustCell = max(2.0f * params.dustSize.coerceIn(0.25f, 4f) * sc, 2.0f)
        val dustThreshold = 0.95f - 0.20f * dust
        val dustAlphaScale = params.dustOpacity.coerceIn(0f, 1f)
        val dustFleckChance = dust * 0.002f
        val dustColorR = if (params.dustWhite) 1f else 0f
        val dustColorG = if (params.dustWhite) 0.985f else 0f
        val dustColorB = if (params.dustWhite) 0.955f else 0f
        val scratches = params.scratches.coerceIn(0f, 1f)
        val scratchMix = scratches * 0.35f
        val texture = params.texture.coerceIn(0f, 1f)
        val vignette = params.vignette.coerceIn(0f, 1f)
        val halation = params.halation.coerceIn(0f, 1f)
        val aberrScale = aberration * 0.012f
        val grainCell = max(1.2f * sc, 1.0f)

        val out = IntArray(n)

        PixelRunner.forEachRow(height) { y ->
            val fy = y + 0.5f
            val uy = fy / height
            val py = fy / 1080f

            var scratchCenters: FloatArray? = null
            var scratchGates: BooleanArray? = null
            if (scratches > 0.001f) {
                val centers = FloatArray(8)
                val gates = BooleanArray(8)
                for (k in 0 until 8) {
                    val fk = k.toFloat()
                    val xi = PixelMath.hashF(k, 3, seedI + 7) * 0.98f + 0.01f
                    val wobble = 0.0015f * sin(py * 6f + fk * 2.1f + seedI)
                    centers[k] = xi + wobble
                    gates[k] = PixelMath.hashF(floor(py * 0.5f).toInt(), k * 13, seedI + 13) > 0.4f
                }
                scratchCenters = centers
                scratchGates = gates
            }

            for (x in 0 until width) {
                val i = y * width + x
                val srcPx = input[i]
                val alpha = (srcPx ushr 24) and 0xFF
                val sR = PixelMath.visibleRed(srcPx)
                val sG = PixelMath.visibleGreen(srcPx)
                val sB = PixelMath.visibleBlue(srcPx)

                // ---------------------------------------------------- 2a. aberration
                var aberR = sR
                var aberG = sG
                var aberB = sB
                if (aberration > 0.001f) {
                    val ux = (x + 0.5f) / width
                    val dx = (ux - 0.5f) * aberrScale * width
                    val dy = (uy - 0.5f) * aberrScale * height
                    val rp = PixelMath.sampleArgb(input, width, height, x + 0.5f + dx, fy + dy)
                    val bp = PixelMath.sampleArgb(input, width, height, x + 0.5f - dx, fy - dy)
                    // The sampler already removed invisible source RGB. Read its straight
                    // channels even when very small interpolated coverage rounds to alpha 0.
                    aberR = PixelMath.red(rp)
                    aberB = PixelMath.blue(bp)
                }

                // ---------------------------------------------------- 2b. soft focus
                var bR = aberR
                var bG = aberG
                var bB = aberB
                if (blurred != null) {
                    bR = PixelMath.mix(aberR, PixelMath.visibleRed(blurred[i]), blurW)
                    bG = PixelMath.mix(aberG, PixelMath.visibleGreen(blurred[i]), blurW)
                    bB = PixelMath.mix(aberB, PixelMath.visibleBlue(blurred[i]), blurW)
                }

                // ---------------------------------------------------- 1. color grade
                var cR = bR * grade[0] + bG * grade[1] + bB * grade[2] + grade[3]
                var cG = bR * grade[4] + bG * grade[5] + bB * grade[6] + grade[7]
                var cB = bR * grade[8] + bG * grade[9] + bB * grade[10] + grade[11]
                cR = cR.coerceIn(0f, 1f)
                cG = cG.coerceIn(0f, 1f)
                cB = cB.coerceIn(0f, 1f)

                // ---------------------------------------------------- 3. halation
                if (lumenMask != null) {
                    val hi = lumenMask[i] * halation * 2.2f
                    val hr = hi.coerceIn(0f, 1f)
                    val hg = (hi * 0.6f).coerceIn(0f, 1f)
                    val hb = (hi * 0.35f).coerceIn(0f, 1f)
                    cR = 1f - (1f - cR) * (1f - hr)
                    cG = 1f - (1f - cG) * (1f - hg)
                    cB = 1f - (1f - cB) * (1f - hb)
                    cR = cR.coerceIn(0f, 1f)
                    cG = cG.coerceIn(0f, 1f)
                    cB = cB.coerceIn(0f, 1f)
                }

                // ---------------------------------------------------- 4. film grain
                // Integer-lattice white noise band-limited to ~1.2px cells (matches AGSL).
                if (grain > 0.001f) {
                    val gix = floor((x + 0.5f) / grainCell).toInt()
                    val giy = floor(fy / grainCell).toInt()
                    val gv = PixelMath.hashF(gix, giy, seedI + 11)
                    val gb = 0.5f + (gv - 0.5f) * (grain * 0.18f)
                    cR = PixelMath.softLightFilm(cR, gb).coerceIn(0f, 1f)
                    cG = PixelMath.softLightFilm(cG, gb).coerceIn(0f, 1f)
                    cB = PixelMath.softLightFilm(cB, gb).coerceIn(0f, 1f)
                    cR = PixelMath.softLightFilm(cR, gb).coerceIn(0f, 1f)
                    cG = PixelMath.softLightFilm(cG, gb).coerceIn(0f, 1f)
                    cB = PixelMath.softLightFilm(cB, gb).coerceIn(0f, 1f)
                }

                // ---------------------------------------------------- 5. dust specks
                // Scalar mask only (never the noise alpha). The disc-blur on the noise is gated
                // on multiplier (=minDim/1080) >= 2.0; preview/thumbnails
                // are below that, so flecks stay sharp 1-3 px specks.
                if (dust > 0.001f) {
                    val qx = (x + 0.5f) / dustCell
                    val qy = fy / dustCell
                    var f = PixelMath.fbm2(qx, qy, seedI + 3)
                    if (minDim >= 2160) {
                        val o = sc / dustCell
                        f = (f * 2f +
                            PixelMath.fbm2(qx + o, qy, seedI + 3) +
                            PixelMath.fbm2(qx - o, qy, seedI + 3) +
                            PixelMath.fbm2(qx, qy + o, seedI + 3) +
                            PixelMath.fbm2(qx, qy - o, seedI + 3)) / 6f
                    }
                    val m = PixelMath.smoothstep(dustThreshold, dustThreshold + 0.08f, f)
                    var a = (m * dustAlphaScale).coerceIn(0f, 1f)
                    val fleck = PixelMath.hashF(x, y, seedI + 5)
                    if (fleck > 1f - dustFleckChance) {
                        a = max(a, dustAlphaScale * 0.85f)
                    }
                    if (a > 0f) {
                        cR = PixelMath.mix(cR, dustColorR, a)
                        cG = PixelMath.mix(cG, dustColorG, a)
                        cB = PixelMath.mix(cB, dustColorB, a)
                    }
                }

                // ---------------------------------------------------- 6. scratches
                if (scratchCenters != null && scratchGates != null) {
                    val ux = (x + 0.5f) / width
                    var s = 0f
                    for (k in 0 until 8) {
                        if (scratchGates[k]) {
                            val dx = abs(ux - scratchCenters[k])
                            if (dx < 0.0025f) {
                                s += 1f - PixelMath.smoothstep(0f, 0.0025f, dx)
                            }
                        }
                    }
                    if (s > 0f) {
                        val a = min(s, 1f) * scratchMix
                        cR = PixelMath.mix(cR, 0.95f, a)
                        cG = PixelMath.mix(cG, 0.94f, a)
                        cB = PixelMath.mix(cB, 0.90f, a)
                    }
                }

                // ---------------------------------------------------- 7. texture
                if (texture > 0.001f) {
                    val ux = (x + 0.5f) / width
                    val tx = PixelMath.fbm2(ux * 3f, uy * 3f, seedI + 29)
                    val mul = 1f - texture * 0.12f * (1f - tx)
                    cR *= mul
                    cG *= mul
                    cB *= mul
                }

                // ---------------------------------------------------- 8. vignette
                if (vignette > 0.001f) {
                    val dx = ((x + 0.5f) / width - 0.5f) * aspect
                    val dy = uy - 0.5f
                    val d = sqrt(dx * dx + dy * dy)
                    val vig = 1f - vignette * PixelMath.smoothstep(0.35f, 0.95f, d * 0.9f)
                    cR *= vig
                    cG *= vig
                    cB *= vig
                }

                // ---------------------------------------------------- 9. intensity blend
                val rr = PixelMath.mix(sR, cR.coerceIn(0f, 1f), t)
                val gg = PixelMath.mix(sG, cG.coerceIn(0f, 1f), t)
                val bb = PixelMath.mix(sB, cB.coerceIn(0f, 1f), t)
                out[i] = PixelMath.pack(rr, gg, bb, alpha)
            }
        }
        return out
    }
}
