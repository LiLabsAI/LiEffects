package com.aliahad.photoeffects.photo

import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExportRetryTest {
    @Test fun allocationFailureShrinksAndIncludesTheFinalFloor() = runBlocking {
        val sizes = mutableListOf<Pair<Int, Int>>()
        val result = ExportRetry.render(2000, 1500) { width, height ->
            sizes += width to height
            if (sizes.size < 3) throw OutOfMemoryError("render allocation")
            width to height
        }
        assertEquals(3, sizes.size)
        assertEquals(2000 to 1500, sizes.first())
        assertTrue(sizes[1].first.toLong() * sizes[1].second <= 750_000)
        assertTrue(result.first.toLong() * result.second <= 500_000)
    }

    @Test fun missingOriginalIsNeverRetriedAsASmallerPreview() = runBlocking {
        val failure = IOException("Original photo is unavailable")
        var attempts = 0
        try {
            ExportRetry.render(4000, 3000) { _, _ -> attempts++; throw failure }
            fail("Missing source must fail the export")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
        assertEquals(1, attempts)
    }

    @Test fun cancellationIsPropagatedWithoutRetry() = runBlocking {
        val failure = CancellationException("editor closed")
        var attempts = 0
        try {
            ExportRetry.render(4000, 3000) { _, _ -> attempts++; throw failure }
            fail("Cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(failure, actual)
        }
        assertEquals(1, attempts)
    }

    @Test fun smallPhotosAreNotUpscaledAndTerminalOomEscapes() = runBlocking {
        val failure = OutOfMemoryError("still unavailable")
        var attempts = 0
        try {
            ExportRetry.render(320, 240) { width, height ->
                assertEquals(320 to 240, width to height)
                attempts++
                throw failure
            }
            fail("Final allocation failure must escape")
        } catch (actual: OutOfMemoryError) {
            assertSame(failure, actual)
        }
        assertEquals(1, attempts)
    }
}
