package com.aliahad.photoeffects

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Adjust GPU/CPU parity; run on an emulator because the USB phone rejects test-APK installs. */
@RunWith(AndroidJUnit4::class)
class AdjustGpuInstrumentedTest {
    private var width = 160
    private var height = 107

    @Test fun colourAndHslMatchTheirCpuMirrors() {
        assertMatches(AdjustParams().with(AdjustControl.Exposure, 35)
            .with(AdjustControl.Contrast, 20)
            .with(AdjustControl.Saturation, 25))
        assertMatches(AdjustParams().with(AdjustControl.HslRedHue, 100)
            .with(AdjustControl.HslBlueSaturation, 70))
    }

    @Test fun detailAndBlurMatchTheirCpuMirrors() {
        assertMatches(AdjustParams().with(AdjustControl.Sharpen, 60)
            .with(AdjustControl.Clarity, 40)
            .with(AdjustControl.Dehaze, 30))
        assertMatches(AdjustParams().with(AdjustControl.GaussianBlur, 40)
            .with(AdjustControl.Mist, 30)
            .with(AdjustControl.RefocusAmount, 50)
            .with(AdjustControl.FocusPoint, 35))
    }

    @Test fun stylizeMatchesItsCpuMirror() {
        assertMatches(AdjustParams().with(AdjustControl.Grain, 40)
            .with(AdjustControl.Vignette, 40)
            .with(AdjustControl.Vintage, 40))
    }

    @Test fun opticalWarpMatchesWithinResamplingTolerance() {
        assertMatches(
            AdjustParams().with(AdjustControl.Fisheye, 30)
                .with(AdjustControl.RgbSplit, 40)
                .with(AdjustControl.Aberration, 30),
        )
    }

    @Test fun directionalAndVignetteBlurEndpointsMatchTheirCpuMirrors() {
        width = 320
        height = 213
        assertMatches(AdjustParams().with(AdjustControl.DirectionalBlur, 100))
        assertMatches(AdjustParams().with(AdjustControl.VignetteBlur, 100))
    }

    @Test fun sharpenClipsBeforeNegativeDehazeOnBothBackends() {
        val impulse = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        impulse[(height / 2) * width + width / 2] = 0xFF000000.toInt()
        assertMatches(AdjustParams().with(AdjustControl.Sharpen, 100)
            .with(AdjustControl.Dehaze, -100), input = impulse)
    }

    @Test fun opticalWarpMatchesAtTransparentEdgesAndParallelResolution() {
        width = 320
        height = 213
        assertMatches(AdjustParams().with(AdjustControl.Fisheye, 100)
            .with(AdjustControl.RgbSplit, 100).with(AdjustControl.Aberration, 100),
            input = source(transparent = true), alphaLimit = 2)
    }

    @Test fun processedTransparentLayersKeepTheirColoursWhenOpticallyResampled() {
        width = 320
        height = 213
        for (base in listOf(
            AdjustParams().with(AdjustControl.Exposure, 35),
            AdjustParams().with(AdjustControl.GaussianBlur, 40),
        )) {
            assertMatches(base.with(AdjustControl.Fisheye, 100)
                .with(AdjustControl.RgbSplit, 100).with(AdjustControl.Aberration, 100),
                input = source(transparent = true), alphaLimit = 2)
        }
    }

    @Test fun opaquePhotosRetainTheHardwareSamplingFastPath() {
        width = 320
        height = 213
        assertMatches(AdjustParams().with(AdjustControl.GaussianBlur, 40)
            .with(AdjustControl.Fisheye, 100).with(AdjustControl.RgbSplit, 100),
            opaqueSource = true)
    }

    private fun assertMatches(
        params: AdjustParams,
        maxLimit: Int = 4,
        input: IntArray = source(),
        alphaLimit: Int = 1,
        opaqueSource: Boolean = false,
    ) {
        val effect = AdjustEffectFactory.create(params)
        val src = input
        val bitmap = Bitmap.createBitmap(src, width, height, Bitmap.Config.ARGB_8888)
        val canonical = IntArray(src.size)
        bitmap.getPixels(canonical, 0, width, 0, 0, width, height)
        bitmap.recycle()

        val expected = effect.cpu.apply(canonical, width, height, 1f, 42L)
        val wasDrawingEnabled = HardwareRenderer.isDrawingEnabled()
        HardwareRenderer.setDrawingEnabled(true)
        val actual = try {
            val sourceBitmap = Bitmap.createBitmap(canonical, width, height, Bitmap.Config.ARGB_8888)
            if (opaqueSource) sourceBitmap.setHasAlpha(false)
            try {
                checkNotNull(
                    GpuReadback.render(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                        sourceBitmap,
                        effect.gpu,
                        width,
                        height,
                        42L,
                        1f,
                    ),
                )
            } finally {
                sourceBitmap.recycle()
            }
        } finally {
            HardwareRenderer.setDrawingEnabled(wasDrawingEnabled)
        }

        var sum = 0L
        var max = 0
        for (i in expected.indices) {
            val alpha = expected[i] ushr 24
            assertTrue("alpha at $i", abs(alpha - (actual[i] ushr 24)) <= alphaLimit)
            for (shift in intArrayOf(0, 8, 16)) {
                val channel = (((expected[i] ushr shift) and 255) * alpha + 127) / 255
                val delta = abs(channel - ((actual[i] ushr shift) and 255))
                sum += delta
                max = maxOf(max, delta)
            }
        }
        val mean = sum.toDouble() / (expected.size * 3)
        Log.i("AdjustParity", "${effect.summary} mean=$mean max=$max")
        assertTrue("Adjust GPU/CPU mean=$mean max=$max", mean < 0.8 && max <= maxLimit)
    }

    private fun source(transparent: Boolean = false): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val alpha = if (!transparent) 255 else when (i % 7) {
            0 -> 0; 1 -> 64; 2 -> 128; 3 -> 192; else -> 255
        }
        (alpha shl 24) or
            ((x * 255 / (width - 1)) shl 16) or
            ((y * 255 / (height - 1)) shl 8) or
            ((x * 13 + y * 7) % 256)
    }
}
