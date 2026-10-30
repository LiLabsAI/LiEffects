package com.aliahad.photoeffects.effect.cpu

/**
 * Cheap separable box blur used by the CPU mirror for the soft-focus and halation stages.
 *
 * The GPU path uses a 9-tap gaussian approximation; a box blur of radius r has a similar
 * low-pass effect and keeps the CPU renderer allocation-light and fast for thumbnails,
 * exports and unit tests.
 */
internal object BoxBlur {

    fun blurArgb(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius < 1) return src
        if (NativeBlur.available) {
            val native = IntArray(src.size)
            if (NativeBlur.blurArgb(src, native, width, height, radius)) return native
        }
        return blurArgbReference(src, width, height, radius)
    }

    internal fun blurArgbReference(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius < 1) return src
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        val window = 2 * radius + 1
        // horizontal
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                for (k in -radius..radius) {
                    val sx = (x + k).coerceIn(0, width - 1)
                    val p = src[row + sx]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    a += (p ushr 24) and 0xFF
                }
                tmp[row + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            }
        }
        // vertical
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0
                var g = 0
                var b = 0
                var a = 0
                for (k in -radius..radius) {
                    val sy = (y + k).coerceIn(0, height - 1)
                    val p = tmp[sy * width + x]
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                    a += (p ushr 24) and 0xFF
                }
                out[y * width + x] = ((a / window) shl 24) or
                    ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            }
        }
        return out
    }

    fun blurFloat(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius < 1) return src
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val window = 2 * radius + 1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0f
                for (k in -radius..radius) {
                    sum += src[row + (x + k).coerceIn(0, width - 1)]
                }
                tmp[row + x] = sum / window
            }
        }
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0f
                for (k in -radius..radius) {
                    sum += tmp[(y + k).coerceIn(0, height - 1) * width + x]
                }
                out[y * width + x] = sum / window
            }
        }
        return out
    }

    /**
     * O(n) box blur for large radii (used by the halation bloom where radii can exceed 100 px).
     *
     * [blurFloat] costs O(n * radius); this variant uses per-row/per-column prefix sums with
     * clamped edge replication, so cost is O(n) independent of radius.
     */
    fun blurFloatFast(src: FloatArray, width: Int, height: Int, radius: Int): FloatArray {
        if (radius < 1 || width <= 0 || height <= 0) return src.copyOf()
        if (NativeBlur.available) {
            val native = FloatArray(src.size)
            if (NativeBlur.blurFloatFast(src, native, width, height, radius)) return native
        }
        return blurFloatFastReference(src, width, height, radius)
    }

    internal fun blurFloatFastReference(
        src: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
    ): FloatArray {
        if (radius < 1 || width <= 0 || height <= 0) return src.copyOf()
        val window = 2 * radius + 1
        val inv = 1f / window
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)

        // Horizontal pass: prefix sums over a clamped-extended row.
        val extW = width + 2 * radius
        val prefix = FloatArray(extW + 1)
        for (y in 0 until height) {
            val row = y * width
            prefix[0] = 0f
            for (x in 0 until extW) {
                prefix[x + 1] = prefix[x] + src[row + (x - radius).coerceIn(0, width - 1)]
            }
            for (x in 0 until width) {
                tmp[row + x] = (prefix[x + window] - prefix[x]) * inv
            }
        }

        // Vertical pass: prefix sums over a clamped-extended column.
        val extH = height + 2 * radius
        val prefixC = FloatArray(extH + 1)
        for (x in 0 until width) {
            prefixC[0] = 0f
            for (y in 0 until extH) {
                prefixC[y + 1] = prefixC[y] + tmp[(y - radius).coerceIn(0, height - 1) * width + x]
            }
            for (y in 0 until height) {
                out[y * width + x] = (prefixC[y + window] - prefixC[y]) * inv
            }
        }
        return out
    }
}
