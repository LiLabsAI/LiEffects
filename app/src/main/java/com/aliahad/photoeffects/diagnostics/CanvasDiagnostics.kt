package com.aliahad.photoeffects.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.canvas.CanvasBitmapRenderer
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import kotlin.math.abs

/** Exercises production software geometry and the hardware canvas used around the GPU graph. */
internal object CanvasDiagnostics {
    fun run(context: Context) {
        val width = 257
        val height = 193
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            0xff000000.toInt() or ((x * 255 / (width - 1)) shl 16) or
                ((y * 255 / (height - 1)) shl 8) or (if ((x / 23 + y / 19) % 2 == 0) 50 else 180)
        }
        val program = AdjustEffectFactory.create(AdjustParams()).gpu
        var checked = 0
        var failed = 0
        for (transparent in listOf(false, true)) {
            val fixture = if (!transparent) pixels else IntArray(pixels.size) { i ->
                val x = i % width
                val y = i / width
                val alpha = when ((x / 31 + y / 27) % 4) { 0 -> 0; 1 -> 64; 2 -> 192; else -> 255 }
                (pixels[i] and 0x00ffffff) or (alpha shl 24)
            }
            val source = Bitmap.createBitmap(fixture, width, height, Bitmap.Config.ARGB_8888)
            source.setHasAlpha(transparent)
            try {
                val cases = CanvasRatio.entries.map { CanvasParams(it) } +
                    (0..3).flatMap { turn -> listOf(
                        CanvasParams(CanvasRatio.POST_4_5, 0.4, 0.6, 1.7, turn, -17.3, true),
                        CanvasParams(CanvasRatio.STORY, 0.6, 0.4, 1.3, turn, 45.0),
                    ) }
                for (params in cases) {
                    checked++
                    val g = params.geometry(width, height)
                    val cpu = CanvasBitmapRenderer.render(source, g)
                    try {
                        val expected = IntArray(cpu.width * cpu.height)
                        cpu.getPixels(expected, 0, cpu.width, 0, 0, cpu.width, cpu.height)
                        val gpu = GpuReadback.render(context, source, program, width, height, 42, 1f, g)
                        var max = 0
                        var alpha = 0
                        var sum = 0L
                        if (gpu != null) for (i in expected.indices) {
                            alpha = maxOf(alpha, abs((expected[i] ushr 24) - (gpu[i] ushr 24)))
                            for (channel in 0..2) {
                                val shift = channel * 8
                                val premultiplied = (((expected[i] ushr shift) and 255) * (expected[i] ushr 24) + 127) / 255
                                val error = abs(premultiplied - ((gpu[i] ushr shift) and 255))
                                max = maxOf(max, error)
                                sum += error
                            }
                        }
                        val mean = sum.toDouble() / (expected.size * 3)
                        // A fractional spatial pass can add one A8 quantization step to the
                        // upstream shader's one-step coverage error, as in resized Adjust checks.
                        val alphaLimit = if (transparent) 2 else 0
                        val passed = gpu != null && max <= 4 && alpha <= alphaLimit && mean < 0.8
                        if (!passed) failed++
                        Log.i("RenderDiagnostics", "Canvas ${params.ratio} transparent=$transparent turn=${params.quarterTurns} angle=${params.straighten} flip=${params.mirrored} zoom=${params.zoom}: ${cpu.width}x${cpu.height} mean=$mean max=$max alpha=$alpha passed=$passed")
                    } finally { cpu.recycle() }
                }
            } finally { source.recycle() }
        }
        Log.i("RenderDiagnostics", "Canvas geometry complete: checked=$checked failed=$failed")
    }
}
