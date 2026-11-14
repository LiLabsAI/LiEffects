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

package com.aliahad.photoeffects.canvas

import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.effect.cpu.PixelRunner
import kotlinx.coroutines.CancellationException

/** Pure, row-parallel affine sampler. Pixel centers and premultiplied interpolation match GPU. */
internal object CanvasPixels {
    fun render(
        source: IntArray, width: Int, height: Int, geometry: CanvasGeometry,
        cancel: () -> Boolean = { false },
    ): IntArray {
        require(width > 0 && height > 0 && source.size.toLong() == width.toLong() * height)
        val (outputWidth, outputHeight) = geometry.outputSize(width, height)
        val output = IntArray(outputWidth * outputHeight)
        val m = geometry.inverseMatrixValues(width, height, outputWidth, outputHeight)
        PixelRunner.forEachRow(outputHeight) { y ->
            if (cancel()) throw CancellationException("Canvas export cancelled")
            val py = y + 0.5f
            val startX = m[1] * py + m[2]
            val startY = m[4] * py + m[5]
            for (x in 0 until outputWidth) {
                val px = x + 0.5f
                output[y * outputWidth + x] = PixelMath.sampleArgb(
                    source, width, height, m[0] * px + startX, m[3] * px + startY,
                )
            }
        }
        if (cancel()) throw CancellationException("Canvas export cancelled")
        return output
    }
}
