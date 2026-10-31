package com.aliahad.photoeffects.adjust

import com.aliahad.photoeffects.adjust.cpu.CpuAdjustRenderer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustSpatialRegressionTest {
    @Test fun opticalRenderingIsRepeatableAboveTheParallelThreshold() {
        val width = 384
        val height = 256
        val input = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            0xFF000000.toInt() or (((x * 37 + y * 11) and 255) shl 16) or
                (((x * 7 + y * 29) and 255) shl 8) or ((x * 17 + y * 13) and 255)
        }
        val params = AdjustParams().with(AdjustControl.Fisheye, 60)
            .with(AdjustControl.RgbSplit, 60).with(AdjustControl.Aberration, 60)
        val renderer = CpuAdjustRenderer(params, planFor(params))
        val expected = renderer.apply(input, width, height, 1f, 42L)
        repeat(8) {
            assertArrayEquals(expected, renderer.apply(input, width, height, 1f, 42L))
        }
    }

    @Test fun hiddenTransparentColoursDoNotBleedIntoSpatialAdjustments() {
        val width = 65
        val height = 101
        val first = IntArray(width * height) { i ->
            if ((i % width + i / width) % 3 == 0) 0x00FF0000 else 0xFF0040C0.toInt()
        }
        val second = IntArray(first.size) { i -> if (first[i] ushr 24 == 0) 0x0000FF00 else first[i] }
        for (control in listOf(
            AdjustControl.Fisheye, AdjustControl.RgbSplit, AdjustControl.Aberration,
            AdjustControl.GaussianBlur, AdjustControl.VignetteBlur, AdjustControl.DirectionalBlur,
            AdjustControl.Cleanup, AdjustControl.Clarity,
        )) {
            val params = AdjustParams().with(control, 80)
            val renderer = CpuAdjustRenderer(params, planFor(params))
            val a = renderer.apply(first, width, height, 1f, 42L)
            val b = renderer.apply(second, width, height, 1f, 42L)
            for (i in first.indices) if (first[i] ushr 24 != 0) {
                assertEquals("$control at $i", a[i], b[i])
                assertEquals(first[i] ushr 24, a[i] ushr 24)
            }
        }
    }

    @Test fun directionalBlurHasNoTranslationOnALinearGradient() {
        val width = 129
        val height = 129
        val input = IntArray(width * height) { i ->
            val gray = (128 + 2 * (i % width + i / width - 128)).coerceIn(0, 255)
            0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
        }
        val params = AdjustParams().with(AdjustControl.DirectionalBlur, 100)
        val output = CpuAdjustRenderer(params, planFor(params)).apply(input, width, height, 1f, 42L)
        val center = (height / 2) * width + width / 2
        assertEquals(input[center], output[center])
    }
}
