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

package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.PhotoEffect
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Picks the largest safe export resolution for the deterministic CPU renderers.
 *
 * Each effect reports its peak render working set per pixel ([PhotoEffect.exportBytesPerPixel]),
 * including Java pixel arrays and native/JNI buffers;
 * the CPU mirror of LUMEN/VELOUR needs several full-frame float planes, so rendering a
 * 12 MP photo directly would exhaust the heap. The budget keeps the render within
 * [HEAP_FRACTION] of the process heap and lets [PhotoSaver] fall back further on OOM.
 */
object ExportBudget {

    /** Share of the process heap the render may use; the rest is the app's working set. */
    private const val HEAP_FRACTION = 0.40

    /** Never export below the previous 1440 px long edge (≈1.55 MP on 4:3). */
    const val MIN_PIXELS = 1_500_000

    /** Upper bound for very large sources (48 MP+ camera modes). */
    const val MAX_PIXELS = 24_000_000

    /** Decode + JPEG encode only (no effect selected): two ARGB bitmaps, no pixel arrays. */
    private const val NO_EFFECT_BYTES_PER_PIXEL = 8

    fun bytesPerPixel(effect: PhotoEffect?): Int =
        effect?.exportBytesPerPixel ?: NO_EFFECT_BYTES_PER_PIXEL

    fun maxPixels(
        effect: PhotoEffect?,
        maxMemoryBytes: Long = Runtime.getRuntime().maxMemory(),
        additionalBytesPerPixel: Int = 0,
    ): Int {
        require(additionalBytesPerPixel >= 0)
        val budgetBytes = (maxMemoryBytes * HEAP_FRACTION).toLong()
        val pixels = (budgetBytes / (bytesPerPixel(effect).toLong() + additionalBytesPerPixel))
            .coerceAtMost(Int.MAX_VALUE.toLong())
        return pixels.toInt().coerceIn(MIN_PIXELS, MAX_PIXELS)
    }

    /** Fit [width] x [height] into [maxPixels], preserving aspect ratio. */
    fun targetSize(width: Int, height: Int, maxPixels: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        require(maxPixels > 0)
        val pixels = width.toLong() * height
        if (pixels <= maxPixels) return width to height
        val scale = sqrt(maxPixels.toDouble() / pixels)
        var w = max(1, (width * scale).toInt())
        var h = max(1, (height * scale).toInt())
        // The one-pixel minimum on an extreme panorama can otherwise put it over budget.
        if (w.toLong() * h > maxPixels) {
            if (w >= h) w = max(1, maxPixels / h)
            else h = max(1, maxPixels / w)
        }
        return w to h
    }

    /** Longest-edge equivalent of [maxPixels] for a [width] x [height] source. */
    fun maxDimension(width: Int, height: Int, maxPixels: Int): Int {
        val (w, h) = targetSize(width, height, maxPixels)
        return max(w, h)
    }
}
