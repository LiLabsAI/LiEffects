package com.aliahad.photoeffects.ui.editor

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestPhotoLoadTest {
    @Test
    fun reverseCompletionAcceptsOnlyNewestPhotoAndDisposesOlderDecode() = runBlocking {
        val discarded = mutableListOf<String>()
        val accepted = mutableListOf<String>()
        val loading = mutableListOf<Boolean>()
        val failures = mutableListOf<Throwable>()
        val loader = LatestPhotoLoad<String>(discarded::add)
        val olderResult = CompletableDeferred<String>()
        val newerResult = CompletableDeferred<String>()
        val older = launch(start = CoroutineStart.UNDISPATCHED) {
            loader.run(olderResult::await, accepted::add, failures::add, loading::add)
        }
        val newer = launch(start = CoroutineStart.UNDISPATCHED) {
            loader.run(newerResult::await, accepted::add, failures::add, loading::add)
        }
        newerResult.complete("new photo")
        newer.join()
        olderResult.complete("old photo")
        older.join()

        assertEquals(listOf("new photo"), accepted)
        assertEquals(listOf("old photo"), discarded)
        assertEquals(listOf(true, true, false), loading)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun latestFailurePreservesExistingPhotoAndPreventsOlderRequestReplacingIt() = runBlocking {
        val discarded = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val loader = LatestPhotoLoad<String>(discarded::add)
        var currentPhoto = "current photo with edits"
        var loading = false
        val olderResult = CompletableDeferred<String>()
        val older = launch(start = CoroutineStart.UNDISPATCHED) {
            loader.run(olderResult::await, { currentPhoto = it }, failures::add, { loading = it })
        }
        val failure = IOException("source unavailable")
        loader.run({ throw failure }, { currentPhoto = it }, failures::add, { loading = it })
        olderResult.complete("stale photo")
        older.join()

        assertEquals("current photo with edits", currentPhoto)
        assertEquals(listOf(failure), failures)
        assertEquals(listOf("stale photo"), discarded)
        assertFalse(loading)
    }

    @Test
    fun decoderOutOfMemoryIsRecoverableAndSubsequentLoadStillSucceeds() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val loader = LatestPhotoLoad<String> { error("No completed decode to discard") }
        var currentPhoto = "existing photo"
        var loading = false
        val failure = OutOfMemoryError("decode allocation")
        loader.run({ throw failure }, { currentPhoto = it }, failures::add, { loading = it })
        assertEquals("existing photo", currentPhoto)
        assertEquals(listOf(failure), failures)
        assertFalse(loading)

        loader.run({ "replacement" }, { currentPhoto = it }, failures::add, { loading = it })
        assertEquals("replacement", currentPhoto)
        assertFalse(loading)
    }

    @Test
    fun cancellationRemainsCancellationAndDoesNotReportAnErrorOrPublishAPhoto() = runBlocking {
        val accepted = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()
        val loader = LatestPhotoLoad<String> { error("No completed decode to discard") }
        var loading = false
        val pending = CompletableDeferred<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            loader.run(pending::await, accepted::add, failures::add, { loading = it })
        }
        assertTrue(loading)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(accepted.isEmpty())
        assertTrue(failures.isEmpty())
        assertFalse(loading)
    }
}
