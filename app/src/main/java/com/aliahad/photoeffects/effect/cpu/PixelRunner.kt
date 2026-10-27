package com.aliahad.photoeffects.effect.cpu

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Row-parallel executor for the deterministic CPU renderer.
 *
 * Every row is an independent pure computation, so scheduling cannot change the output:
 * results are bit-identical to a sequential run (unit-tested via repeated renders).
 */
internal object PixelRunner {

    private val threadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)

    private val pool: ExecutorService by lazy {
        Executors.newFixedThreadPool(threadCount) { r ->
            Thread(r, "dusty-cpu").apply { isDaemon = true }
        }
    }

    fun forEachRow(height: Int, block: (Int) -> Unit) {
        if (threadCount == 1 || height < 96) {
            for (y in 0 until height) block(y)
            return
        }
        val chunk = (height + threadCount - 1) / threadCount
        val tasks = ArrayList<Callable<Unit>>(threadCount)
        for (t in 0 until threadCount) {
            val start = t * chunk
            if (start >= height) break
            val end = (start + chunk).coerceAtMost(height)
            tasks.add(Callable {
                for (y in start until end) block(y)
                Unit
            })
        }
        pool.invokeAll(tasks).forEach { it.get() }
    }
}
