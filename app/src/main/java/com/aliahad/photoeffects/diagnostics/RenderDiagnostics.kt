package com.aliahad.photoeffects.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.cpu.BoxBlur
import com.aliahad.photoeffects.effect.cpu.NativeBlur
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import com.aliahad.photoeffects.filter.FilterRegistry
import kotlin.math.abs

/**
 * Debug-only, on-device render diagnostics.
 *
 * Test-APK installation is blocked on the target ROM, so this harness lives in the app and is
 * triggered with:
 *
 *   adb shell am force-stop com.aliahad.photoeffects
 *   adb shell am start -n com.aliahad.photoeffects/.MainActivity --ez render_diagnostics true
 *
 * It compares every GPU program with its CPU mirror on the same synthetic transparent-edge image
 * (the same tolerance as the instrumented test) and logs CPU/GPU timings for representative
 * presets and compositions. See `PhotoEffectsGpu` / `RenderDiagnostics` in logcat.
 */
object RenderDiagnostics {
    private const val TAG = "RenderDiagnostics"
    private const val WIDTH = 160
    private const val HEIGHT = 107
    private const val BENCH_WIDTH = 640
    private const val BENCH_HEIGHT = 427
    private const val SEED = 42L

    fun run(context: Context) {
        Log.i(TAG, "Starting render diagnostics (native blur=${NativeBlur.available})")
        checkNativeBlur()
        val width = WIDTH
        val height = HEIGHT
        val transparent = syntheticSource(width, height, transparent = true)
        val transparentBitmap = Bitmap.createBitmap(transparent, width, height, Bitmap.Config.ARGB_8888)
        val transparentPixels = IntArray(transparent.size)
        transparentBitmap.getPixels(transparentPixels, 0, width, 0, 0, width, height)

        val opaque = syntheticSource(width, height, transparent = false)
        val opaqueBitmap = Bitmap.createBitmap(opaque, width, height, Bitmap.Config.ARGB_8888)
        val opaquePixels = IntArray(opaque.size)
        opaqueBitmap.getPixels(opaquePixels, 0, width, 0, 0, width, height)

        val filterIds = FilterRegistry.all.map { it.id }.toSet()
        var checked = 0
        var failed = 0
        for (effect in FilterRegistry.all + EffectRegistry.all) {
            // Filters already have transparent-edge coverage; legacy effects are evaluated on an
            // opaque image so alpha semantics do not mask the look math.
            val isFilter = effect.id in filterIds
            val result = compare(
                context,
                effect,
                if (isFilter) transparentBitmap else opaqueBitmap,
                if (isFilter) transparentPixels else opaquePixels,
                width,
                height,
                effect.displayName,
            )
            checked++
            if (result?.passes != true) failed++
        }

        val compositions = listOf(
            "insomnia_1+dusty_1" to pair("insomnia_1", "dusty_1"),
            "chicago_2+halation_1" to pair("chicago_2", "halation_1"),
            "g7x_1+yesterday_1" to pair("g7x_1", "yesterday_1"),
        )
        for ((label, pair) in compositions) {
            val composed = pair ?: continue
            val result = compare(context, composed, opaqueBitmap, opaquePixels, width, height, label)
            checked++
            if (result?.passes != true) failed++
        }

        val adjustCases = listOf(
            "adjust_color" to AdjustParams()
                .with(AdjustControl.Exposure, 35)
                .with(AdjustControl.Contrast, 20)
                .with(AdjustControl.Saturation, 25),
            "adjust_hsl" to AdjustParams()
                .with(AdjustControl.HslRedHue, 100)
                .with(AdjustControl.HslBlueSaturation, 70),
            "adjust_detail" to AdjustParams()
                .with(AdjustControl.Sharpen, 60)
                .with(AdjustControl.Clarity, 40)
                .with(AdjustControl.Dehaze, 30),
            "adjust_blur" to AdjustParams()
                .with(AdjustControl.GaussianBlur, 40)
                .with(AdjustControl.Mist, 30)
                .with(AdjustControl.RefocusAmount, 50)
                .with(AdjustControl.FocusPoint, 35),
            "adjust_optical" to AdjustParams()
                .with(AdjustControl.Fisheye, 30)
                .with(AdjustControl.RgbSplit, 40)
                .with(AdjustControl.Aberration, 30),
            "adjust_stylize" to AdjustParams()
                .with(AdjustControl.Grain, 40)
                .with(AdjustControl.Dust, 40)
                .with(AdjustControl.Vignette, 40)
                .with(AdjustControl.Vintage, 40),
        )
        for ((label, params) in adjustCases) {
            val effect = AdjustEffectFactory.create(params)
            val result = compare(
                context,
                effect,
                opaqueBitmap,
                opaquePixels,
                width,
                height,
                label,
            )
            checked++
            if (result?.passes != true) failed++
        }

        Log.i(TAG, "Parity complete: checked=$checked failed=$failed")
        logTimings(context)
        logAdjustTimings(context)
        transparentBitmap.recycle()
        opaqueBitmap.recycle()
    }

    private fun checkNativeBlur() {
        if (!NativeBlur.available) return
        val width = 37
        val height = 23
        val radius = 3
        val floats = FloatArray(width * height) { ((it * 37) % 101) / 101f }
        val nativeFloats = FloatArray(floats.size)
        val referenceFloats = BoxBlur.blurFloatFastReference(floats, width, height, radius)
        NativeBlur.blurFloatFast(floats, nativeFloats, width, height, radius)
        var floatMax = 0f
        for (i in floats.indices) {
            floatMax = maxOf(floatMax, abs(nativeFloats[i] - referenceFloats[i]))
        }

        val argb = IntArray(width * height) { i ->
            val a = (i * 7) and 0xFF
            (a shl 24) or (((i * 13) and 0xFF) shl 16) or
                (((i * 29) and 0xFF) shl 8) or ((i * 41) and 0xFF)
        }
        val nativeArgb = IntArray(argb.size)
        val referenceArgb = BoxBlur.blurArgbReference(argb, width, height, radius)
        NativeBlur.blurArgb(argb, nativeArgb, width, height, radius)
        val argbExact = nativeArgb.contentEquals(referenceArgb)
        Log.i(TAG, "native blur check: floatMax=$floatMax argbExact=$argbExact")
    }

    private data class Parity(val passes: Boolean, val mean: Double, val max: Int)

    private fun compare(
        context: Context,
        effect: PhotoEffect,
        sourceBitmap: Bitmap,
        pixels: IntArray,
        width: Int,
        height: Int,
        label: String,
    ): Parity? {
        val expected = effect.cpu.apply(pixels, width, height, 1f, SEED)
        val actual = GpuReadback.render(
            context,
            sourceBitmap,
            effect.gpu,
            width,
            height,
            SEED,
            1f,
        )
        if (actual == null) {
            Log.e(TAG, "$label: GPU readback failed")
            return null
        }
        var sum = 0L
        var max = 0
        var alphaMax = 0
        for (i in expected.indices) {
            val alpha = expected[i] ushr 24
            alphaMax = maxOf(alphaMax, abs(alpha - (actual[i] ushr 24)))
            for (shift in intArrayOf(0, 8, 16)) {
                val channel = (((expected[i] ushr shift) and 255) * alpha + 127) / 255
                val delta = abs(channel - ((actual[i] ushr shift) and 255))
                sum += delta
                max = maxOf(max, delta)
            }
        }
        val mean = sum.toDouble() / (expected.size * 3)
        val passes = mean < 0.8 && max <= 4 && alphaMax <= 1
        if (!passes) {
            Log.e(TAG, "$label: mean=$mean max=$max alpha=$alphaMax")
        } else {
            Log.i(TAG, "$label: mean=$mean max=$max")
        }
        return Parity(passes, mean, max)
    }

    private fun pair(filterId: String, effectId: String): PhotoEffect? {
        val filter = FilterRegistry.byId(filterId) ?: return null
        val effect = EffectRegistry.byId(effectId) ?: return null
        return EditComposition.compose(EditStage(filter, 100), EditStage(effect, 65))
    }

    private fun logTimings(context: Context) {
        val ids = listOf(
            "insomnia_1", "chicago_2", "g7x_1",
            "dusty_1", "halation_1", "yesterday_1", "sensual_1", "kuji_1", "focal_1",
        )
        val effects = ids.mapNotNull { id ->
            FilterRegistry.byId(id) ?: EffectRegistry.byId(id)
        }
        val pixels = syntheticSource(BENCH_WIDTH, BENCH_HEIGHT, transparent = false)
        val bitmap = Bitmap.createBitmap(pixels, BENCH_WIDTH, BENCH_HEIGHT, Bitmap.Config.ARGB_8888)
        val megapixels = BENCH_WIDTH.toDouble() * BENCH_HEIGHT / 1_000_000.0
        for (effect in effects) {
            val cpuStart = System.nanoTime()
            repeat(2) { effect.cpu.apply(pixels, BENCH_WIDTH, BENCH_HEIGHT, 1f, SEED) }
            val cpuMs = (System.nanoTime() - cpuStart) / 2_000_000.0
            val gpuStart = System.nanoTime()
            val gpuPixels = GpuReadback.render(
                context,
                bitmap,
                effect.gpu,
                BENCH_WIDTH,
                BENCH_HEIGHT,
                SEED,
                1f,
            )
            val gpuMs = (System.nanoTime() - gpuStart) / 1_000_000.0
            val parity = if (gpuPixels == null) " GPU=FAILED" else ""
            Log.i(
                TAG,
                "timing ${effect.displayName}: CPU=${"%.1f".format(cpuMs)}ms " +
                    "(${"%.1f".format(cpuMs / megapixels)}ms/MP) " +
                    "GPU(readback)=${"%.1f".format(gpuMs)}ms$parity",
            )
        }
        bitmap.recycle()
    }

    private fun logAdjustTimings(context: Context) {
        val cases = listOf(
            "Adjust colour" to AdjustParams().with(AdjustControl.Exposure, 25)
                .with(AdjustControl.Saturation, 20),
            "Adjust detail" to AdjustParams().with(AdjustControl.Sharpen, 50)
                .with(AdjustControl.Clarity, 40),
            "Adjust blur" to AdjustParams().with(AdjustControl.GaussianBlur, 40)
                .with(AdjustControl.Mist, 30),
        )
        val pixels = syntheticSource(BENCH_WIDTH, BENCH_HEIGHT, transparent = false)
        val bitmap = Bitmap.createBitmap(pixels, BENCH_WIDTH, BENCH_HEIGHT, Bitmap.Config.ARGB_8888)
        val megapixels = BENCH_WIDTH.toDouble() * BENCH_HEIGHT / 1_000_000.0
        for ((label, params) in cases) {
            val effect = AdjustEffectFactory.create(params)
            val cpuStart = System.nanoTime()
            repeat(2) { effect.cpu.apply(pixels, BENCH_WIDTH, BENCH_HEIGHT, 1f, SEED) }
            val cpuMs = (System.nanoTime() - cpuStart) / 2_000_000.0
            val gpuStart = System.nanoTime()
            val gpu = GpuReadback.render(
                context, bitmap, effect.gpu, BENCH_WIDTH, BENCH_HEIGHT, SEED, 1f,
            )
            val gpuMs = (System.nanoTime() - gpuStart) / 1_000_000.0
            Log.i(
                TAG,
                "timing $label: CPU=${"%.1f".format(cpuMs)}ms " +
                    "(${"%.1f".format(cpuMs / megapixels)}ms/MP) " +
                    "GPU(readback)=${"%.1f".format(gpuMs)}ms${if (gpu == null) " GPU=FAILED" else ""}",
            )
        }
        bitmap.recycle()
    }

    private fun syntheticSource(width: Int, height: Int, transparent: Boolean): IntArray =
        IntArray(width * height) { i ->
            val alpha = if (!transparent) 255 else when (i % 7) {
                0 -> 0; 1 -> 64; 2 -> 128; 3 -> 192; else -> 255
            }
            (alpha shl 24) or
                ((i % width * 255 / (width - 1)) shl 16) or
                ((i / width * 255 / (height - 1)) shl 8) or
                ((i * 17 + i / width * 13) % 256)
        }
}
