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

package com.aliahad.photoeffects.effect.cpu

import android.os.Process
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Row-parallel executor for the deterministic CPU renderer.
 *
 * Every row is an independent pure computation, so scheduling cannot change the output:
 * results are bit-identical to a sequential run (unit-tested via repeated renders).
 */
internal object PixelRunner {

    // Leave the primary core for the UI; the renderer is background work.
    private val threadCount: Int = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 6)

    private val pool: ExecutorService by lazy {
        Executors.newFixedThreadPool(threadCount) { r ->
            Thread({
                // Android-only; JVM tests run against SDK stubs that throw for this call.
                runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
                r.run()
            }, "photo-cpu").apply { isDaemon = true }
        }
    }

    fun forEachRow(height: Int, block: (Int) -> Unit) {
        val cancel = RenderCancellation.current()
        if (threadCount == 1 || height < 96) {
            for (y in 0 until height) {
                if (cancel?.invoke() == true) throw CancellationException("CPU render cancelled")
                block(y)
            }
            return
        }
        val chunk = (height + threadCount - 1) / threadCount
        val tasks = ArrayList<Callable<Unit>>(threadCount)
        for (t in 0 until threadCount) {
            val start = t * chunk
            if (start >= height) break
            val end = (start + chunk).coerceAtMost(height)
            tasks.add(Callable {
                for (y in start until end) {
                    if (cancel?.invoke() == true) throw CancellationException("CPU render cancelled")
                    block(y)
                }
                Unit
            })
        }
        try {
            pool.invokeAll(tasks).forEach { it.get() }
        } catch (e: ExecutionException) {
            // In particular, preserve OutOfMemoryError so export can reduce its size and retry.
            throw e.cause ?: e
        }
    }
}
