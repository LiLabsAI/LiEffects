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

/**
 * Decoded radial blur mask with bilinear scalar sampling. The mask is approximately
 * 0.05 at the centre and 0.82 in the corners, keeping the centre sharper than the
 * edges. The bitmap is loaded once and shared by the portrait-softening pipeline.
 */
class BlurMaskData(val values: FloatArray, val width: Int, val height: Int) {

    init {
        require(values.size == width * height) { "mask buffer does not match $width x $height" }
        require(width > 0 && height > 0) { "mask dimensions must be positive" }
    }

    /** Bilinear sample of the mask stretched over the whole image (keepAspect = 0). */
    fun sample(u: Float, v: Float): Float {
        val x = (u.coerceIn(0f, 1f) * (width - 1)).coerceIn(0f, (width - 1).toFloat())
        val y = (v.coerceIn(0f, 1f) * (height - 1)).coerceIn(0f, (height - 1).toFloat())
        val x0 = x.toInt()
        val y0 = y.toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1)
        val y1 = (y0 + 1).coerceAtMost(height - 1)
        val fx = x - x0
        val fy = y - y0
        val a = values[y0 * width + x0]
        val b = values[y0 * width + x1]
        val c = values[y1 * width + x0]
        val d = values[y1 * width + x1]
        return (a * (1f - fx) + b * fx) * (1f - fy) + (c * (1f - fx) + d * fx) * fy
    }

    companion object {
        /**
         * Analytic fallback matching the measured radial profile (used only when the bundled
         * asset has not been loaded, e.g. JVM-only tests).
         */
        fun analytic(mask: Int = 1): BlurMaskData {
            val w = 64
            val h = 64
            val values = FloatArray(w * h)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val u = (x + 0.5f) / w
                    val v = (y + 0.5f) / h
                    val dx = (u - 0.5f) * 2f
                    val dy = (v - 0.5f) * 2f
                    val r = kotlin.math.sqrt(dx * dx + dy * dy)
                    values[y * w + x] = (0.46f * r * r - 0.10f).coerceIn(0f, 1f)
                }
            }
            return BlurMaskData(values, w, h)
        }
    }
}

/** Process-wide store for the bundled blur mask (populated by `MainActivity`). */
object BlurMaskStore {
    @Volatile
    var current: BlurMaskData? = null

    /** Mask to use for rendering; falls back to the analytic radial approximation. */
    fun requireMask(): BlurMaskData = current ?: BlurMaskData.analytic()
}
