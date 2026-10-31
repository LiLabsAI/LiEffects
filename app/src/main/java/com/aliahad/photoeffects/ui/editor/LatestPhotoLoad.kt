package com.aliahad.photoeffects.ui.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Owns each decode until it is either accepted by the latest request or discarded. Callers run
 * on the editor's main-thread scope; decoding itself may switch dispatchers or finish late.
 */
internal class LatestPhotoLoad<T : Any>(private val discard: (T) -> Unit) {
    private var generation = 0L

    suspend fun run(
        decode: suspend () -> T,
        onLoaded: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
        onLoadingChanged: (Boolean) -> Unit,
    ) {
        val request = ++generation
        onLoadingChanged(true)
        var owned: T? = null
        try {
            val decoded = decode()
            owned = decoded
            currentCoroutineContext().ensureActive()
            if (request != generation) return
            owned = null // The callback takes ownership, including if its own work throws.
            onLoaded(decoded)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: OutOfMemoryError) {
            if (request == generation) onFailure(failure)
        } catch (failure: Exception) {
            if (request == generation) onFailure(failure)
        } finally {
            owned?.let(discard)
            if (request == generation) onLoadingChanged(false)
        }
    }
}
