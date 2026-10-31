package com.aliahad.photoeffects

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Legacy spatial/color approximations remain characterized separately from this alpha contract. */
@RunWith(AndroidJUnit4::class)
class LegacyAlphaGpuInstrumentedTest {
    @Test fun everyEffectPreservesCoverageAndReturnsValidPremultipliedColor() {
        val width = 160
        val height = 107
        val input = IntArray(width * height) { i ->
            val alpha = when (i % 7) { 0 -> 0; 1 -> 64; 2 -> 128; 3 -> 192; else -> 255 }
            (alpha shl 24) or ((i % width * 255 / (width - 1)) shl 16) or
                ((i / width * 255 / (height - 1)) shl 8) or ((i * 17 + i / width * 13) % 256)
        }
        val source = Bitmap.createBitmap(input, width, height, Bitmap.Config.ARGB_8888)
        val canonical = IntArray(input.size)
        source.getPixels(canonical, 0, width, 0, 0, width, height)
        val enabled = HardwareRenderer.isDrawingEnabled()
        HardwareRenderer.setDrawingEnabled(true)
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            for (effect in EffectRegistry.all) for (intensity in listOf(0f, 1f)) {
                val actual = checkNotNull(GpuReadback.render(context, source, effect.gpu,
                    width, height, 42L, intensity))
                for (i in canonical.indices) {
                    val alpha = actual[i] ushr 24
                    assertTrue("${effect.id} coverage at $i", abs(alpha - (canonical[i] ushr 24)) <= 1)
                    for (shift in intArrayOf(0, 8, 16)) {
                        val channel = (actual[i] ushr shift) and 255
                        assertTrue("${effect.id} invalid premultiplied channel at $i", channel <= alpha + 1)
                        if (intensity == 0f) {
                            val expected = ((((canonical[i] ushr shift) and 255) *
                                (canonical[i] ushr 24)) + 127) / 255
                            assertTrue("${effect.id} zero intensity at $i", abs(expected - channel) <= 1)
                        }
                    }
                }
            }
        } finally {
            source.recycle()
            HardwareRenderer.setDrawingEnabled(enabled)
        }
    }
}
