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
