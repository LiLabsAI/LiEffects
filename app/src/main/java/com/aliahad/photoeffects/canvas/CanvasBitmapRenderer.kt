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

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException

/** Final geometry pass after Filter → Adjust → Effect, including effect frames/date overlays. */
object CanvasBitmapRenderer {
    fun render(
        source: Bitmap,
        geometry: CanvasGeometry,
        cancel: () -> Boolean = { false },
    ): Bitmap {
        if (cancel()) throw CancellationException("Canvas export cancelled")
        val (width, height) = geometry.outputSize(source.width, source.height)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val output = CanvasPixels.render(pixels, source.width, source.height, geometry, cancel)
        if (cancel()) throw CancellationException("Canvas export cancelled")
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(source.hasAlpha())
        }
    }
}
