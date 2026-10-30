package com.aliahad.photoeffects.effect.cpu

import java.util.concurrent.CancellationException

/**
 * Cooperative cancellation for blocking CPU renders.
 *
 * [PixelRunner] checks this token once per row, so a cancelled thumbnail pass stops within a
 * tile instead of running to completion and competing with the UI. The token is thread-local
 * on the caller; worker tasks capture the same predicate at fan-out time.
 */
internal object RenderCancellation {
    private val current = ThreadLocal<(() -> Boolean)?>()

    fun current(): (() -> Boolean)? = current.get()

    fun set(predicate: (() -> Boolean)?) {
        if (predicate == null) current.remove() else current.set(predicate)
    }

    fun check() {
        if (current.get()?.invoke() == true) throw CancellationException("CPU render cancelled")
    }
}
