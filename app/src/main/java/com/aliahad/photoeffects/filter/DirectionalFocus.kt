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
import com.aliahad.photoeffects.effect.cpu.PixelRunner
import kotlin.math.abs

/**
 * Original directional focus model: a horizontal triangular smear, a narrow vertical spread,
 * and optional headroom-limited local detail. All distances arrive in image pixels.
 *
 * The CPU stores only one full-resolution premultiplied RGBA float intermediate (16 B/pixel)
 * plus its ARGB output (4 B/pixel). Including the input and the caller's bitmap/output buffers,
 * the pipeline must reserve at least 32 B/pixel. No per-pixel/tap arrays are allocated. Execute
 * this prepass outside another PixelRunner row block so its two row-parallel passes can finish.
 */
internal object DirectionalFocus {
    private const val SQRT_TWO = 1.41421356f

    fun render(
        input: IntArray,
        width: Int,
        height: Int,
        radius: Float,
        spread: Float,
        detail: Float,
        detailRadius: Float,
        lift: Float = 0f,
    ): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == input.size.toLong())
        require(input.size <= Int.MAX_VALUE / 4)
        require(radius.isFinite() && radius >= 0f && spread.isFinite() && spread >= 0f)
        require(detail.isFinite() && detail in 0f..2f && detailRadius.isFinite() && detailRadius >= 0f)
        require(lift.isFinite() && lift in 0f..0.02f)
        if (radius == 0f && spread == 0f && detail == 0f && lift == 0f) return input.copyOf()

        val horizontal = FloatArray(input.size * 4)
        val offsets = FloatArray(49) { (it - 24).toFloat() * radius / 24f }
        val weights = FloatArray(49) { (24 - abs(it - 24)).toFloat() / 576f }
        PixelRunner.forEachRow(height) { y ->
            val row = y * width
            for (x in 0 until width) {
                val index = (row + x) * 4
                if (radius == 0f) {
                    val p = input[row + x]
                    val a = (p ushr 24) / 255f
                    horizontal[index] = ((p ushr 16) and 255) / 255f * a
                    horizontal[index + 1] = ((p ushr 8) and 255) / 255f * a
                    horizontal[index + 2] = (p and 255) / 255f * a
                    horizontal[index + 3] = a
                    continue
                }
                var r = 0f; var g = 0f; var b = 0f; var a = 0f
                for (tap in 0..48) {
                    val weight = weights[tap]
                    if (weight == 0f) continue
                    val px = (x.toFloat() + offsets[tap]).coerceIn(0f, (width - 1).toFloat())
                    val x0 = px.toInt()
                    val x1 = (x0 + 1).coerceAtMost(width - 1)
                    val f = px - x0
                    val p0 = input[row + x0]
                    val p1 = input[row + x1]
                    val a0 = (p0 ushr 24) / 255f
                    val a1 = (p1 ushr 24) / 255f
                    val r0 = ((p0 ushr 16) and 255) / 255f * a0
                    val g0 = ((p0 ushr 8) and 255) / 255f * a0
                    val b0 = (p0 and 255) / 255f * a0
                    val r1 = ((p1 ushr 16) and 255) / 255f * a1
                    val g1 = ((p1 ushr 8) and 255) / 255f * a1
                    val b1 = (p1 and 255) / 255f * a1
                    r += (r0 + (r1 - r0) * f) * weight
                    g += (g0 + (g1 - g0) * f) * weight
                    b += (b0 + (b1 - b0) * f) * weight
                    a += (a0 + (a1 - a0) * f) * weight
                }
                horizontal[index] = r
                horizontal[index + 1] = g
                horizontal[index + 2] = b
                horizontal[index + 3] = a
            }
        }

        val out = IntArray(input.size)
        val verticalOffset = SQRT_TWO * spread
        val localRadius = SQRT_TWO * detailRadius
        PixelRunner.forEachRow(height) { y ->
            val above = (y.toFloat() - verticalOffset).coerceIn(0f, (height - 1).toFloat())
            val below = (y.toFloat() + verticalOffset).coerceIn(0f, (height - 1).toFloat())
            val ya = above.toInt()
            val yb = below.toInt()
            val fa = above - ya
            val fb = below - yb
            val rowA0 = ya * width
            val rowA1 = (ya + 1).coerceAtMost(height - 1) * width
            val rowB0 = yb * width
            val rowB1 = (yb + 1).coerceAtMost(height - 1) * width
            val row = y * width
            val local = if (detail > 0f) FloatArray(3) else null
            for (x in 0 until width) {
                val i = row + x
                val center = i * 4
                val a0 = (rowA0 + x) * 4
                val a1 = (rowA1 + x) * 4
                val b0 = (rowB0 + x) * 4
                val b1 = (rowB1 + x) * 4
                val alpha = vertical(horizontal, a0, a1, fa, center, b0, b1, fb, 3)
                var r = if (alpha > 0f) vertical(horizontal, a0, a1, fa, center, b0, b1, fb, 0) / alpha else 0f
                var g = if (alpha > 0f) vertical(horizontal, a0, a1, fa, center, b0, b1, fb, 1) / alpha else 0f
                var b = if (alpha > 0f) vertical(horizontal, a0, a1, fa, center, b0, b1, fb, 2) / alpha else 0f
                val motionR = r; val motionG = g; val motionB = b
                val source = input[i]
                if (local != null) {
                    FilterSoftFocus.sample(input, width, height, x, y, localRadius, local)
                    val visible = (source ushr 24) > 0
                    val dr = (if (visible) ((source ushr 16) and 255) / 255f else 0f) - local[0]
                    val dg = (if (visible) ((source ushr 8) and 255) / 255f else 0f) - local[1]
                    val db = (if (visible) (source and 255) / 255f else 0f) - local[2]
                    r += detail * dr * if (dr >= 0f) 1f - r else r
                    g += detail * dg * if (dg >= 0f) 1f - g else g
                    b += detail * db * if (db >= 0f) 1f - b else b
                }
                r += lift * (1f - motionR)
                g += lift * (1f - motionG)
                b += lift * (1f - motionB)
                out[i] = PixelMath.pack(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f), source ushr 24)
            }
        }
        return out
    }

    private fun vertical(
        data: FloatArray,
        above0: Int,
        above1: Int,
        aboveFraction: Float,
        center: Int,
        below0: Int,
        below1: Int,
        belowFraction: Float,
        channel: Int,
    ): Float {
        val a = data[above0 + channel]
        val b = data[below0 + channel]
        val above = a + (data[above1 + channel] - a) * aboveFraction
        val below = b + (data[below1 + channel] - b) * belowFraction
        return above * 0.25f + data[center + channel] * 0.5f + below * 0.25f
    }
}

/** Direct GPU evaluation of the same separable field, without an intermediate raster. */
internal const val DIRECTIONAL_FOCUS_AGSL = """
float4 directionalFocusSample(float2 p) {
    float2 hi = max(uResolution - float2(0.5), float2(0.5));
    return float4(content.eval(clamp(p, float2(0.5), hi)));
}

float3 directionalFocusStraight(float4 p) {
    return p.a > 0.0 ? p.rgb / p.a : float3(0.0);
}

float3 directionalFocus(float2 coord, float radius, float spread, float detail, float detailRadius, float lift) {
    float4 source = directionalFocusSample(coord);
    if (radius <= 0.0 && spread <= 0.0 && detail <= 0.0 && lift <= 0.0) return directionalFocusStraight(source);
    float4 accumulated = float4(0.0);
    for (int y = -1; y <= 1; y++) {
        float verticalWeight = y == 0 ? 0.5 : 0.25;
        float dy = float(y) * 1.41421356 * spread;
        for (int i = -24; i <= 24; i++) {
            float weight = (24.0 - abs(float(i))) / 576.0;
            if (weight > 0.0) {
                float dx = float(i) * radius / 24.0;
                accumulated += directionalFocusSample(coord + float2(dx, dy)) * weight * verticalWeight;
            }
        }
    }
    float3 blurred = directionalFocusStraight(accumulated);
    float3 motion = blurred;
    if (detail > 0.0) {
        float3 local = float3(0.0);
        float localRadius = 1.41421356 * detailRadius;
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                float weight = (x == 0 ? 2.0 : 1.0) * (y == 0 ? 2.0 : 1.0) / 16.0;
                local += directionalFocusStraight(directionalFocusSample(
                    coord + float2(float(x), float(y)) * localRadius)) * weight;
            }
        }
        float3 d = directionalFocusStraight(source) - local;
        float3 room = float3(d.r >= 0.0 ? 1.0 - blurred.r : blurred.r,
            d.g >= 0.0 ? 1.0 - blurred.g : blurred.g,
            d.b >= 0.0 ? 1.0 - blurred.b : blurred.b);
        blurred += detail * d * room;
    }
    blurred += lift * (1.0 - motion);
    return clamp(blurred, 0.0, 1.0);
}
"""
