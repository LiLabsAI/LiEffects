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

import java.util.function.BooleanSupplier

/**
 * Optional NDK acceleration for the shared box-blur kernels.
 *
 * The Kotlin implementations remain the reference (and the only path in JVM tests); the native
 * library is used on-device when it loads. Float prefix-sum order, integer channel division and
 * clamp-to-edge behavior match the reference. Checked native allocations return false for the
 * Kotlin fallback; cancellation is polled between small batches of rows/columns.
 */
internal object NativeBlur {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("photoeffects") }.isSuccess
    }

    fun blurFloatFast(
        src: FloatArray,
        dst: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): Boolean {
        RenderCancellation.check()
        return blurFloatFastNative(src, dst, width, height, radius, cancellation())
    }

    fun blurArgb(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ): Boolean {
        RenderCancellation.check()
        return blurArgbNative(src, dst, width, height, radius, cancellation())
    }

    private fun cancellation(): BooleanSupplier? = RenderCancellation.current()?.let { predicate ->
        BooleanSupplier { predicate() }
    }

    @JvmStatic
    private external fun blurFloatFastNative(
        src: FloatArray, dst: FloatArray, width: Int, height: Int, radius: Int,
        cancel: BooleanSupplier?,
    ): Boolean

    @JvmStatic
    private external fun blurArgbNative(
        src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int,
        cancel: BooleanSupplier?,
    ): Boolean
}
