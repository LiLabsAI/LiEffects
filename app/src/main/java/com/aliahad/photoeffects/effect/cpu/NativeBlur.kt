package com.aliahad.photoeffects.effect.cpu

/**
 * Optional NDK acceleration for the shared box-blur kernels.
 *
 * The Kotlin implementations remain the reference (and the only path in JVM tests); the native
 * library is used on-device when it loads. Both implementations use the same prefix-sum order,
 * integer division and clamp-to-edge behavior.
 */
internal object NativeBlur {
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("photoeffects") }.isSuccess
    }

    @JvmStatic
    external fun blurFloatFast(
        src: FloatArray,
        dst: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): Boolean

    @JvmStatic
    external fun blurArgb(
        src: IntArray,
        dst: IntArray,
        width: Int,
        height: Int,
        radius: Int,
    ): Boolean
}
