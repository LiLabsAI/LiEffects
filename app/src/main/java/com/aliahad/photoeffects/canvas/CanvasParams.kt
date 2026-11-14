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

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

enum class CanvasControl { Ratio, Rotate }

/** Width:height formats. Original follows the source orientation after quarter turns. */
enum class CanvasRatio(val label: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0),
    SQUARE("Square", 1, 1),
    STORY("Story", 9, 16),
    FRAME("Frame", 16, 9),
    POST_4_5("Post", 4, 5),
    POST_5_4("Post", 5, 4),
    CLASSIC_3_4("Classic", 3, 4),
    CLASSIC_4_3("Classic", 4, 3),
    PORTRAIT("Portrait", 2, 3),
    LANDSCAPE("Landscape", 3, 2);

    val ratioLabel: String get() = if (this == ORIGINAL) "Original" else "$width:$height"
    val displayName: String get() = if (this == ORIGINAL) label else "$label ($ratioLabel)"
    fun rotated(): CanvasRatio = entries.firstOrNull {
        it.width == height && it.height == width
    } ?: ORIGINAL
}

/** Small, nondestructive geometry; centers use oriented source coordinates, never preview pixels. */
data class CanvasParams(
    val ratio: CanvasRatio = CanvasRatio.ORIGINAL,
    val centerX: Double = 0.5,
    val centerY: Double = 0.5,
    val zoom: Double = 1.0,
    val quarterTurns: Int = 0,
    val straighten: Double = 0.0,
    val mirrored: Boolean = false,
) {
    val isIdentity: Boolean get() = this == CanvasParams()

    fun sanitized(): CanvasParams = copy(
        centerX = centerX.finiteOr(0.5).coerceIn(0.0, 1.0),
        centerY = centerY.finiteOr(0.5).coerceIn(0.0, 1.0),
        zoom = zoom.finiteOr(1.0).coerceIn(1.0, 8.0),
        quarterTurns = ((quarterTurns % 4) + 4) % 4,
        straighten = straighten.finiteOr(0.0).coerceIn(-45.0, 45.0),
    )

    fun constrained(width: Int, height: Int): CanvasParams {
        val safe = sanitized()
        val geometry = safe.geometry(width, height)
        return safe.copy(centerX = geometry.centerX / width, centerY = geometry.centerY / height)
    }

    fun geometry(width: Int, height: Int): CanvasGeometry {
        require(width > 0 && height > 0)
        val p = sanitized()
        val aspect = if (ratio == CanvasRatio.ORIGINAL) {
            if (p.quarterTurns % 2 == 0) width.toDouble() / height else height.toDouble() / width
        } else ratio.width.toDouble() / ratio.height
        val radians = Math.toRadians(p.quarterTurns * 90.0 + p.straighten)
        // Exact quarter-turn coefficients avoid losing a pixel to floating point roundoff.
        val c = cos(radians).let { if (abs(it) < 1e-12) 0.0 else it }
        val s = sin(radians).let { if (abs(it) < 1e-12) 0.0 else it }
        val cropHeight = min(
            width / (abs(c) * aspect + abs(s)),
            height / (abs(s) * aspect + abs(c)),
        ) / p.zoom
        val cropWidth = cropHeight * aspect
        val halfX = (abs(c) * cropWidth + abs(s) * cropHeight) / 2
        val halfY = (abs(s) * cropWidth + abs(c) * cropHeight) / 2
        return CanvasGeometry(
            width, height, cropWidth, cropHeight,
            (p.centerX * width).coerceIn(halfX.coerceAtMost(width / 2.0), (width - halfX).coerceAtLeast(width / 2.0)),
            (p.centerY * height).coerceIn(halfY.coerceAtMost(height / 2.0), (height - halfY).coerceAtLeast(height / 2.0)),
            c, s, if (p.mirrored) -1.0 else 1.0,
            p.quarterTurns * 90.0 + p.straighten,
        )
    }

    /** Pan/zoom about the gesture centroid, with no blank corners or accumulated edge overshoot. */
    fun transformed(
        width: Int, height: Int, zoomChange: Double,
        panX: Double, panY: Double, anchorX: Double = 0.5, anchorY: Double = 0.5,
    ): CanvasParams {
        val before = constrained(width, height)
        val old = before.geometry(width, height)
        val next = before.copy(zoom = (before.zoom * zoomChange.finiteOr(1.0)).coerceIn(1.0, 8.0))
        val new = next.geometry(width, height)
        val oldPoint = old.sourcePoint(anchorX.finiteOr(0.5), anchorY.finiteOr(0.5))
        val newPoint = new.sourcePoint(anchorX.finiteOr(0.5) + panX.finiteOr(0.0), anchorY.finiteOr(0.5) + panY.finiteOr(0.0))
        return next.copy(
            centerX = (new.centerX + oldPoint.first - newPoint.first) / width,
            centerY = (new.centerY + oldPoint.second - newPoint.second) / height,
        ).constrained(width, height)
    }
}

/** Shared affine geometry for the GPU-backed view and software export Canvas. */
data class CanvasGeometry(
    val sourceWidth: Int, val sourceHeight: Int,
    val cropWidth: Double, val cropHeight: Double,
    val centerX: Double, val centerY: Double,
    val cosine: Double, val sine: Double, val mirror: Double, val rotation: Double,
) {
    val aspect: Float get() = (cropWidth / cropHeight).toFloat()

    /** Sampling never exceeds the fresh decoded raster's resolution, including zoomed crops. */
    fun outputSize(renderWidth: Int = sourceWidth, renderHeight: Int = sourceHeight): Pair<Int, Int> {
        val scale = min(renderWidth.toDouble() / sourceWidth, renderHeight.toDouble() / sourceHeight).coerceAtMost(1.0)
        return floor(cropWidth * scale + 1e-7).toInt().coerceAtLeast(1) to
            floor(cropHeight * scale + 1e-7).toInt().coerceAtLeast(1)
    }

    fun sourcePoint(x: Double, y: Double): Pair<Double, Double> {
        val dx = (x - 0.5) * cropWidth
        val dy = (y - 0.5) * cropHeight
        return centerX + mirror * (cosine * dx + sine * dy) to centerY - sine * dx + cosine * dy
    }

    /** Android Matrix row order, input raster → output raster. No density-dependent overloads. */
    fun matrixValues(inputWidth: Int, inputHeight: Int, outputWidth: Double, outputHeight: Double): FloatArray {
        val sx = outputWidth / cropWidth
        val sy = outputHeight / cropHeight
        return floatArrayOf(
            (sx * cosine * mirror * sourceWidth / inputWidth).toFloat(),
            (-sx * sine * sourceHeight / inputHeight).toFloat(),
            (outputWidth / 2 - sx * (cosine * mirror * centerX - sine * centerY)).toFloat(),
            (sy * sine * mirror * sourceWidth / inputWidth).toFloat(),
            (sy * cosine * sourceHeight / inputHeight).toFloat(),
            (outputHeight / 2 - sy * (sine * mirror * centerX + cosine * centerY)).toFloat(),
            0f, 0f, 1f,
        )
    }

    internal fun inverseMatrixValues(inputWidth: Int, inputHeight: Int, outputWidth: Int, outputHeight: Int): FloatArray {
        val m = matrixValues(inputWidth, inputHeight, outputWidth.toDouble(), outputHeight.toDouble())
        val a = m[0].toDouble(); val b = m[1].toDouble(); val c = m[2].toDouble()
        val d = m[3].toDouble(); val e = m[4].toDouble(); val f = m[5].toDouble()
        val determinant = a * e - b * d
        check(determinant.isFinite() && determinant != 0.0) { "Invalid canvas transform" }
        return floatArrayOf(
            (e / determinant).toFloat(), (-b / determinant).toFloat(), ((b * f - e * c) / determinant).toFloat(),
            (-d / determinant).toFloat(), (a / determinant).toFloat(), ((d * c - a * f) / determinant).toFloat(),
        )
    }
}

private fun Double.finiteOr(default: Double): Double = if (isFinite()) this else default
