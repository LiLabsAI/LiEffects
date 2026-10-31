package com.aliahad.photoeffects.ui.editor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reclaims a completed render if cancellation wins the handoff back to the UI dispatcher. */
internal suspend fun <T : Any> renderOnBackground(dispose: (T) -> Unit, render: () -> T): T {
    var completed: T? = null
    return try {
        withContext(Dispatchers.Default) { render().also { completed = it } }
    } catch (failure: Throwable) {
        completed?.let(dispose)
        throw failure
    }
}
