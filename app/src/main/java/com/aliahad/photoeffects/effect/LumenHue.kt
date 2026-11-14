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

package com.aliahad.photoeffects.effect

/**
 * Piecewise halo hue mapping shared by the CPU and GPU paths. Calibrated anchor
 * colours cover red-orange, cyan and blue-violet fringes.
 */
object LumenHue {

    val redOrange = floatArrayOf(1.00f, 0.20f, 0.01f)
    val cyan = floatArrayOf(0.01f, 0.79f, 1.00f)
    val blueViolet = floatArrayOf(0.37f, 0.27f, 1.00f)
    val violet = floatArrayOf(0.60f, 0.25f, 1.00f)
    val warmWhite = floatArrayOf(1.00f, 0.96f, 0.86f)
    val cyanWhite = floatArrayOf(0.86f, 0.95f, 1.00f)
    val blueWhite = floatArrayOf(0.90f, 0.94f, 1.00f)
    val violetWhite = floatArrayOf(0.94f, 0.90f, 1.00f)

    /** Outer halo tint: hue 0 = red/orange, 0.5 = cool cyan, 0.7 = blue/violet, 1 = violet. */
    fun outerColor(hue: Float): FloatArray {
        val h = hue.coerceIn(0f, 1f)
        return when {
            h <= 0.5f -> mix(redOrange, cyan, 2f * h)
            h <= 0.7f -> mix(cyan, blueViolet, (h - 0.5f) / 0.2f)
            else -> mix(blueViolet, violet, (h - 0.7f) / 0.3f)
        }
    }

    /** Inner halo tint: warm white -> cyan white -> blue white -> violet white. */
    fun innerColor(hue: Float): FloatArray {
        val h = hue.coerceIn(0f, 1f)
        return when {
            h <= 0.5f -> mix(warmWhite, cyanWhite, 2f * h)
            h <= 0.7f -> mix(cyanWhite, blueWhite, (h - 0.5f) / 0.2f)
            else -> mix(blueWhite, violetWhite, (h - 0.7f) / 0.3f)
        }
    }

    private fun mix(a: FloatArray, b: FloatArray, t: Float): FloatArray = floatArrayOf(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
    )
}
