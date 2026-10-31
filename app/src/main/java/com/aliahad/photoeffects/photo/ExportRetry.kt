package com.aliahad.photoeffects.photo

import kotlin.math.min

/** Retries only allocation failures, always including a final attempt at the retry floor. */
internal object ExportRetry {
    private const val MIN_RETRY_PIXELS = 500_000L

    suspend fun <T> render(width: Int, height: Int, attempt: suspend (Int, Int) -> T): T {
        require(width > 0 && height > 0)
        var pixels = width.toLong() * height
        val floor = min(pixels, MIN_RETRY_PIXELS)
        while (true) {
            val (targetWidth, targetHeight) = ExportBudget.targetSize(
                width, height, pixels.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            )
            try {
                return attempt(targetWidth, targetHeight)
            } catch (oom: OutOfMemoryError) {
                if (pixels <= floor) throw oom
                pixels = (pixels / 4).coerceAtLeast(floor)
            }
        }
    }
}
