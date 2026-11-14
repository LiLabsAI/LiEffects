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
 * Fast approximate gaussian blur for the CPU film pipelines.
 *
 * Implemented as three successive box-blur passes (`BoxBlur.blurFloatFast`, O(n) each), which
 * converge to a gaussian; sigma (in pixels) maps to a box radius of roughly sigma - 0.5.
 * Used by the DAYMARK pre-blur and the VELOUR soft-focus + masked variable blur.
 *
 * Pure Kotlin, deterministic, no Android dependencies.
 */
internal object FastGaussian {

    /** Blur one float plane with an approximate gaussian of [sigma] pixels. */
    fun blurChannel(src: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        RenderCancellation.check()
        if (sigma <= 0.05f || width <= 0 || height <= 0) return src.copyOf()
        val radius = kotlin.math.max(1, (sigma - 0.5f + 0.5f).toInt())
        // The first pass reads the caller's plane; blurFloatFast never mutates its input.
        var acc = src
        repeat(3) {
            acc = BoxBlur.blurFloatFast(acc, width, height, radius)
        }
        return acc
    }

    /** Blur packed RGB planes (each `width * height`) with the same gaussian. */
    fun blurPlanes(
        r: FloatArray,
        g: FloatArray,
        b: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
    ): Triple<FloatArray, FloatArray, FloatArray> = Triple(
        blurChannel(r, width, height, sigma),
        blurChannel(g, width, height, sigma),
        blurChannel(b, width, height, sigma),
    )
}
