package com.aliahad.photoeffects.diagnostics

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.effect.DotMatrixDate
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
 * Exact Filters/Adjust parity is checked separately from the explicitly approximate legacy
 * Effects. Legacy results retain measured differences and use family-specific regression
 * envelopes on this fixed fixture; meeting an envelope does not establish pixel parity.
 * GPU timings include graph construction and readback, so they are not live preview frame times. See `PhotoEffectsGpu` / `RenderDiagnostics` in logcat.
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
        var characterized = 0
        var legacyRegressions = 0
        fun record(effect: PhotoEffect, result: Parity?) {
            if (policyFor(effect).approximate) {
                characterized++
                if (result?.passes != true) legacyRegressions++
            } else {
                checked++
                if (result?.passes != true) failed++
            }
        }
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
            record(effect, result)
        }

        val compositions = listOf(
            "insomnia_1+dusty_1" to pair("insomnia_1", "dusty_1"),
            "chicago_2+halation_1" to pair("chicago_2", "halation_1"),
            "g7x_1+yesterday_1" to pair("g7x_1", "yesterday_1"),
        )
        for ((label, pair) in compositions) {
            val composed = pair ?: continue
            val result = compare(context, composed, opaqueBitmap, opaquePixels, width, height, label)
            record(composed, result)
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
            record(effect, result)
        }

        val alphaContracts = legacyAlphaChecks(context)
        Log.i(TAG, "Legacy alpha contracts complete: checked=${alphaContracts.first} failed=${alphaContracts.second}")
        val focused = focusedChecks(context)
        checked += focused.first
        failed += focused.second
        Log.i(TAG, "Exact parity complete: checked=$checked failed=$failed")
        Log.i(TAG, "Legacy characterization complete: checked=$characterized regressions=$legacyRegressions")
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

    private data class Policy(
        val meanLimit: Double = 0.8,
        val maxLimit: Int = 4,
        val alphaLimit: Int = 1,
        val reason: String? = null,
    ) {
        val approximate: Boolean get() = reason != null
    }

    /**
     * Legacy envelopes characterize the fixed 160x107 adversarial fixture, with headroom over
     * the captured device baseline. These are regression alarms, not tolerances for exact mirrors.
     * Sparse GPU taps, CPU box/Gaussian kernels, sin-hash grain, and differently sized paper
     * frames remain visible in the measured values. Focal shares its kernel and only allows the
     * small LUT/texture quantization outliers measured on the target device.
     */
    private fun policyFor(effect: PhotoEffect): Policy = when (effect.spec.familyId) {
        "Dusty" -> Policy(2.5, 160, reason = "sparse blur and sin-hash grain")
        "Halation" -> Policy(1.5, 255, reason = "sparse halo and edge masks versus CPU box fields")
        "Yesterday" -> Policy(12.0, 160, reason = "sparse preblur and sin-hash grain; stamp region excluded")
        "Sensual" -> Policy(12.0, 150, reason = "sparse GPU blur versus three-box CPU Gaussian")
        "Kuji" -> Policy(2.0, 180, reason = "frame resampling and sin-hash grain")
        "Focal" -> Policy(0.8, 8, reason = "LUT and texture quantization")
        else -> Policy()
    }

    private fun compare(
        context: Context,
        effect: PhotoEffect,
        sourceBitmap: Bitmap,
        pixels: IntArray,
        width: Int,
        height: Int,
        label: String,
        policy: Policy = policyFor(effect),
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
        // The GPU preview adds the date as a Canvas overlay, outside the shader graph.
        // Exclude exactly its padded region rather than comparing stamped CPU pixels against
        // an unstamped readback or hiding the mismatch under a larger whole-image tolerance.
        val stampBounds = effect.stamp?.let {
            DotMatrixDate.bbox(it.dateProvider(), width, height)
        }
        var sum = 0L
        var max = 0
        var maxIndex = 0
        var alphaMax = 0
        var compared = 0
        var excluded = 0
        for (i in expected.indices) {
            val x = i % width + 0.5f
            val y = i / width + 0.5f
            if (stampBounds != null && x >= stampBounds[0] - 2f && x <= stampBounds[2] + 2f &&
                y >= stampBounds[1] - 2f && y <= stampBounds[3] + 2f
            ) {
                excluded++
                continue
            }
            compared++
            val alpha = expected[i] ushr 24
            alphaMax = maxOf(alphaMax, abs(alpha - (actual[i] ushr 24)))
            for (shift in intArrayOf(0, 8, 16)) {
                val channel = (((expected[i] ushr shift) and 255) * alpha + 127) / 255
                val delta = abs(channel - ((actual[i] ushr shift) and 255))
                sum += delta
                if (delta > max) { max = delta; maxIndex = i }
            }
        }
        val mean = sum.toDouble() / (compared.coerceAtLeast(1) * 3)
        val passes = mean < policy.meanLimit && max <= policy.maxLimit && alphaMax <= policy.alphaLimit
        val kind = if (policy.approximate) "legacy envelope" else "exact parity"
        val details = "$label [$kind]: mean=$mean max=$max at=${maxIndex % width},${maxIndex / width} " +
            "alpha=$alphaMax limits=${policy.meanLimit}/${policy.maxLimit}/${policy.alphaLimit} " +
            "stampExcluded=$excluded" + (policy.reason?.let { " reason=$it" } ?: "")
        if (!passes) Log.e(TAG, details) else Log.i(TAG, details)
        return Parity(passes, mean, max)
    }


    /** Color differences stay characterized; coverage and valid premultiplication are required. */
    private fun legacyAlphaChecks(context: Context): Pair<Int, Int> {
        val bitmap = Bitmap.createBitmap(syntheticSource(WIDTH, HEIGHT, true), WIDTH, HEIGHT,
            Bitmap.Config.ARGB_8888)
        var checked = 0
        var failed = 0
        try {
            val canonical = IntArray(WIDTH * HEIGHT)
            bitmap.getPixels(canonical, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            for (effect in EffectRegistry.all) {
                val input = canonical.copyOf()
                val expected = effect.cpu.apply(input, WIDTH, HEIGHT, 1f, SEED)
                val actual = GpuReadback.render(context, bitmap, effect.gpu, WIDTH, HEIGHT, SEED, 1f)
                checked++
                if (actual == null) {
                    failed++
                    Log.e(TAG, "${effect.displayName} transparent alpha contract: GPU readback failed")
                    continue
                }
                var alphaMax = 0
                var invalidPremul = 0
                var max = 0
                var sum = 0L
                for (i in canonical.indices) {
                    val alpha = canonical[i] ushr 24
                    val gpuAlpha = actual[i] ushr 24
                    alphaMax = maxOf(alphaMax, abs(alpha - gpuAlpha), abs(alpha - (expected[i] ushr 24)))
                    for (shift in intArrayOf(0, 8, 16)) {
                        val channel = (actual[i] ushr shift) and 255
                        if (channel > gpuAlpha + 1) invalidPremul++
                        val cpuPremul = ((((expected[i] ushr shift) and 255) * alpha) + 127) / 255
                        val delta = abs(cpuPremul - channel)
                        sum += delta
                        max = maxOf(max, delta)
                    }
                }
                val passes = input.contentEquals(canonical) && alphaMax <= 1 && invalidPremul == 0
                if (!passes) failed++
                val message = "${effect.displayName} transparent alpha contract: alpha=$alphaMax " +
                    "invalidPremul=$invalidPremul inputUntouched=${input.contentEquals(canonical)} " +
                    "characterizedColorMean=${sum.toDouble() / (canonical.size * 3)} max=$max"
                if (passes) Log.i(TAG, message) else Log.e(TAG, message)
            }
        } finally {
            bitmap.recycle()
        }
        return checked to failed
    }

    /** Endpoints and parallel-size fixtures that caught the reviewed regressions on the phone. */
    private fun focusedChecks(context: Context): Pair<Int, Int> {
        var checked = 0
        var failed = 0
        fun verify(label: String, effect: PhotoEffect, input: IntArray, w: Int, h: Int,
                   policy: Policy = Policy()) {
            val bitmap = Bitmap.createBitmap(input, w, h, Bitmap.Config.ARGB_8888)
            try {
                val canonical = IntArray(input.size)
                bitmap.getPixels(canonical, 0, w, 0, 0, w, h)
                val result = compare(context, effect, bitmap, canonical, w, h, label, policy)
                checked++
                if (result?.passes != true) failed++
            } finally {
                bitmap.recycle()
            }
        }
        for (transparent in listOf(false, true)) for (sharpen in listOf(0, 20, 100)) {
            for (variant in 1..3) {
                val effect = requireNotNull(FilterRegistry.withControls("g7x_$variant", 100, sharpen))
                verify("g7x_$variant alpha=$transparent sharpen=$sharpen", effect,
                    syntheticSource(WIDTH, HEIGHT, transparent), WIDTH, HEIGHT)
            }
        }
        val original = Bitmap.createBitmap(syntheticSource(193, 127, true), 193, 127,
            Bitmap.Config.ARGB_8888)
        try {
            val scaled = Bitmap.createScaledBitmap(original, WIDTH, HEIGHT, true)
            val canonical = IntArray(WIDTH * HEIGHT)
            try {
                scaled.getPixels(canonical, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            } finally {
                scaled.recycle()
            }
            val effect = requireNotNull(FilterRegistry.withControls("g7x_1", 100, 100))
            val result = compare(context, effect, original, canonical, WIDTH, HEIGHT,
                "g7x point sharpen resized source grid", Policy(alphaLimit = 2))
            checked++
            if (result?.passes != true) failed++
        } finally {
            original.recycle()
        }
        // Point-sharpen input also belongs to internal grade/focus programs when those controls
        // are supplied; protect the complete pass graph, not just the default G7X single pass.
        for (id in listOf("insomnia_1", "chicago_2")) {
            val effect = requireNotNull(FilterRegistry.withControls(id, 100, 100))
            verify("$id transparent sharpen=100", effect,
                syntheticSource(WIDTH, HEIGHT, true), WIDTH, HEIGHT)
        }
        val w = 320
        val h = 213 // PixelRunner enters its parallel branch at height >= 96.
        for ((label, control) in listOf("directional" to AdjustControl.DirectionalBlur,
                                       "vignette_blur" to AdjustControl.VignetteBlur)) {
            verify("adjust_${label}_100", AdjustEffectFactory.create(AdjustParams().with(control, 100)),
                syntheticSource(w, h, false), w, h)
        }
        val impulse = IntArray(WIDTH * HEIGHT) { 0xFFFFFFFF.toInt() }
        impulse[(HEIGHT / 2) * WIDTH + WIDTH / 2] = 0xFF000000.toInt()
        verify("adjust_sharpen100_dehaze_minus100", AdjustEffectFactory.create(AdjustParams()
            .with(AdjustControl.Sharpen, 100).with(AdjustControl.Dehaze, -100)), impulse, WIDTH, HEIGHT)
        val optical = AdjustEffectFactory.create(AdjustParams()
            .with(AdjustControl.Fisheye, 100).with(AdjustControl.RgbSplit, 100)
            .with(AdjustControl.Aberration, 100))
        for (transparent in listOf(false, true)) {
            verify("adjust_optical_100 alpha=$transparent", optical,
                syntheticSource(w, h, transparent), w, h,
                // Repeated offscreen alpha storage can round coverage by two 8-bit levels.
                Policy(alphaLimit = if (transparent) 2 else 1))
        }
        val composed = requireNotNull(EditComposition.compose(
            EditStage(requireNotNull(FilterRegistry.byId("dim_1")), 100),
            EditStage(optical, 100), null))
        verify("filter_then_optical transparent", composed, syntheticSource(w, h, true), w, h,
            Policy(alphaLimit = 2))
        for ((label, params) in listOf(
            "color_then_optical" to AdjustParams().with(AdjustControl.Exposure, 35),
            "blur_then_optical" to AdjustParams().with(AdjustControl.GaussianBlur, 40),
        )) {
            val effect = AdjustEffectFactory.create(params.with(AdjustControl.Fisheye, 100)
                .with(AdjustControl.RgbSplit, 100).with(AdjustControl.Aberration, 100))
            verify("$label transparent", effect, syntheticSource(w, h, true), w, h,
                Policy(alphaLimit = 2))
        }
        val input = syntheticSource(w, h, false)
        val first = optical.cpu.apply(input, w, h, 1f, SEED)
        repeat(5) { trial ->
            val next = optical.cpu.apply(input, w, h, 1f, SEED)
            val changed = first.indices.count { first[it] != next[it] }
            checked++
            if (changed != 0) failed++
            val message = "adjust optical repeat=$trial changedPixels=$changed"
            if (changed == 0) Log.i(TAG, message) else Log.e(TAG, message)
        }
        return checked to failed
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
