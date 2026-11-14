/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
