package com.aliahad.photoeffects.effect.cpu

import com.aliahad.photoeffects.effect.EffectRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyAlphaRegressionTest {
    @Test fun bilinearSamplingIgnoresHiddenRgbAndPreservesCoverage() {
        val hiddenRedAndBlue = intArrayOf(0x00FF0000, 0xFF0000FF.toInt())
        assertEquals(0x800000FF.toInt(), PixelMath.sampleArgb(hiddenRedAndBlue, 2, 1, 1f, 0.5f))
        assertEquals(0, PixelMath.sampleArgb(hiddenRedAndBlue, 2, 1, -1f, 0.5f))
        val opaque = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        assertEquals(0xFF800080.toInt(), PixelMath.sampleArgb(opaque, 2, 1, 1f, 0.5f))
    }

    @Test fun boxBlurDoesNotSpreadHiddenColours() {
        val first = intArrayOf(0x00FFFFFF, 0xFF004080.toInt(), 0x00FFFFFF)
        val second = intArrayOf(0, 0xFF004080.toInt(), 0)
        assertArrayEquals(
            BoxBlur.blurArgbReference(first, 3, 1, 1),
            BoxBlur.blurArgbReference(second, 3, 1, 1),
        )
    }

    @Test fun everyLegacyPresetIgnoresInvisibleRgbWithoutChangingAlphaOrInput() {
        // Large enough to activate Yesterday's preblur and the parallel CPU branch.
        val width = 129
        val height = 193
        val first = IntArray(width * height) { i ->
            if ((i % width + i / width) % 3 == 0) 0x00FFFFFF else 0x800040C0.toInt()
        }
        val second = IntArray(first.size) { i -> if (first[i] ushr 24 == 0) 0 else first[i] }
        val original = first.copyOf()
        for (effect in EffectRegistry.all) {
            val a = effect.cpu.apply(first, width, height, 1f, 42L)
            val b = effect.cpu.apply(second, width, height, 1f, 42L)
            for (i in first.indices) {
                assertEquals("${effect.id} alpha at $i", first[i] ushr 24, a[i] ushr 24)
                if (first[i] ushr 24 != 0) assertEquals("${effect.id} colour at $i", a[i], b[i])
            }
        }
        assertArrayEquals(original, first)
    }
}
