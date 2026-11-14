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

/**
 * Local detail and channel-separation sampling: a [1, 2, 1] kernel plus bilinear RGB/coverage.
 * Coordinates address pixel centres as integers, matching a LINEAR BitmapShader evaluated at
 * (x + 0.5, y + 0.5). The caller supplies radius in image pixels and owns the reusable RGB
 * output buffer. Bilinear taps interpolate premultiplied RGBA, then return straight RGB,
 * matching the GPU's filtered content shader. Clamp-to-edge applies; output alpha remains
 * the caller's job.
 */
internal object FilterSoftFocus {
    /** Straight RGB plus returned coverage; caller owns the reusable RGB buffer. */
    fun bilinear(input: IntArray, width: Int, height: Int, x: Float, y: Float, out: FloatArray): Float {
        out[0] = 0f; out[1] = 0f; out[2] = 0f
        return accumulate(input, width, height, x, y, 1f, out)
    }

    fun sample(
        input: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Float,
        out: FloatArray,
    ) {
        require(width > 0 && height > 0 && input.size == width * height)
        require(out.size >= 3)
        if (radius <= 0f) {
            val pixel = input[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]
            out[0] = ((pixel ushr 16) and 255) / 255f
            out[1] = ((pixel ushr 8) and 255) / 255f
            out[2] = (pixel and 255) / 255f
            return
        }
        out[0] = 0f
        out[1] = 0f
        out[2] = 0f
        for (dy in -1..1) for (dx in -1..1) {
            val weight = (if (dx == 0) 2f else 1f) * (if (dy == 0) 2f else 1f) / 16f
            accumulate(input, width, height, x.toFloat() + dx * radius, y.toFloat() + dy * radius, weight, out)
        }
    }

    /** Bilinear interpolation with no transient arrays or pixel buffers. */
    private fun accumulate(
        input: IntArray,
        width: Int,
        height: Int,
        px: Float,
        py: Float,
        weight: Float,
        out: FloatArray,
    ): Float {
        val cx = px.coerceIn(0f, (width - 1).toFloat())
        val cy = py.coerceIn(0f, (height - 1).toFloat())
        val x0 = cx.toInt()
        val y0 = cy.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val p00 = input[y0 * width + x0]
        val p10 = input[y0 * width + x1]
        val p01 = input[y1 * width + x0]
        val p11 = input[y1 * width + x1]
        // Photos are usually opaque: retain the original inexpensive RGB-only path.
        if ((p00 ushr 24) == 255 && (p10 ushr 24) == 255 &&
            (p01 ushr 24) == 255 && (p11 ushr 24) == 255) {
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val a = ((p00 ushr shift) and 255) / 255f
                val b = ((p10 ushr shift) and 255) / 255f
                val c = ((p01 ushr shift) and 255) / 255f
                val d = ((p11 ushr shift) and 255) / 255f
                val top = a + (b - a) * fx
                val bottom = c + (d - c) * fx
                out[channel] += (top + (bottom - top) * fy) * weight
            }
            return 1f
        }
        val alpha00 = (p00 ushr 24) / 255f
        val alpha10 = (p10 ushr 24) / 255f
        val alpha01 = (p01 ushr 24) / 255f
        val alpha11 = (p11 ushr 24) / 255f
        val topAlpha = alpha00 + (alpha10 - alpha00) * fx
        val bottomAlpha = alpha01 + (alpha11 - alpha01) * fx
        val sampleAlpha = topAlpha + (bottomAlpha - topAlpha) * fy
        if (sampleAlpha <= 0f) return 0f
        for (channel in 0..2) {
            val shift = 16 - channel * 8
            val a = ((p00 ushr shift) and 255) / 255f * alpha00
            val b = ((p10 ushr shift) and 255) / 255f * alpha10
            val c = ((p01 ushr shift) and 255) / 255f * alpha01
            val d = ((p11 ushr shift) and 255) / 255f * alpha11
            val top = a + (b - a) * fx
            val bottom = c + (d - c) * fx
            out[channel] += ((top + (bottom - top) * fy) / sampleAlpha) * weight
        }
        return sampleAlpha
    }
}
