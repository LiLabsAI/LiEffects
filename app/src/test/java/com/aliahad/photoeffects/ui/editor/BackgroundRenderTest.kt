package com.aliahad.photoeffects.ui.editor

import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRenderTest {
    @Test
    fun successfulRenderTransfersOwnershipToTheCaller() = runBlocking {
        val disposed = mutableListOf<Any>()
        val rendered = Any()
        val received = renderOnBackground(disposed::add) { rendered }
        assertSame(rendered, received)
        assertTrue(disposed.isEmpty())
    }

    @Test(timeout = 10_000)
    fun cancellationDisposesCompletedRenderDiscardedAtDispatcherHandoff() = runBlocking {
        val disposed = mutableListOf<Any>()
        val started = CompletableDeferred<Unit>()
        val finishRender = CountDownLatch(1)
        val rendered = Any()
        val job = async(start = CoroutineStart.UNDISPATCHED) {
            renderOnBackground(disposed::add) {
                started.complete(Unit)
                finishRender.await()
                rendered
            }
        }
        try {
            started.await()
            // Cancellation cannot interrupt the synchronous renderer. Its returned bitmap is
            // otherwise lost when withContext rejects the result on the UI-side handoff.
            job.cancel()
        } finally {
            finishRender.countDown()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, disposed.size)
        assertSame(rendered, disposed.single())
    }
}
