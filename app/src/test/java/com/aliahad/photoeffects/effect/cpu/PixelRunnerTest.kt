package com.aliahad.photoeffects.effect.cpu

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class PixelRunnerTest {
    @Test fun workerFailuresReachTheCallerWithoutWrapping() {
        for (height in listOf(1, 100)) {
            for (failure in listOf(OutOfMemoryError("render allocation"), IllegalStateException("render failure"))) {
                try {
                    PixelRunner.forEachRow(height) { throw failure }
                    fail("expected worker failure")
                } catch (actual: Throwable) {
                    assertSame(failure, actual)
                }
            }
        }
    }

    @Test fun cancellationStopsBeforeAnyRowsAreRendered() {
        var rows = 0
        val previous = RenderCancellation.current()
        try {
            RenderCancellation.set { true }
            try {
                PixelRunner.forEachRow(100) { rows++ }
                fail("expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(0, rows)
            }
        } finally {
            RenderCancellation.set(previous)
        }
    }

    @Test fun blurPrepassesCooperateWithCancellation() {
        val previous = RenderCancellation.current()
        try {
            RenderCancellation.set { true }
            val operations = listOf<() -> Unit>(
                { BoxBlur.blurArgbReference(IntArray(64), 8, 8, 2) },
                { BoxBlur.blurFloat(FloatArray(64), 8, 8, 2) },
                { BoxBlur.blurFloatFastReference(FloatArray(64), 8, 8, 2) },
            )
            for (operation in operations) {
                try {
                    operation()
                    fail("expected blur cancellation")
                } catch (_: CancellationException) { }
            }
        } finally {
            RenderCancellation.set(previous)
        }
    }
}
