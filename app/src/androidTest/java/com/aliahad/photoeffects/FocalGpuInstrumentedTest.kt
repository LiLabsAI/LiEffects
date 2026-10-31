package com.aliahad.photoeffects

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.HardwareRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.effect.FocalFamily
import com.aliahad.photoeffects.effect.cpu.FocalLutData
import com.aliahad.photoeffects.effect.cpu.FocalLutStore
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Includes the outermost rows/columns, where the ellipse blur previously sampled transparent black. */
@RunWith(AndroidJUnit4::class)
class FocalGpuInstrumentedTest {
    @Test fun ellipseBlurMatchesTheCpuAtImageBorders() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val atlas = context.assets.open("focal_luts.png").use { BitmapFactory.decodeStream(it) }!!
        try {
            val nodes = IntArray(atlas.width * atlas.height)
            atlas.getPixels(nodes, 0, atlas.width, 0, 0, atlas.width, atlas.height)
            for (params in FocalFamily.presets) {
                FocalLutStore.put(params.id, FocalLutData.fromAtlas(nodes, atlas.width, params.lutIndex))
            }
        } finally {
            atlas.recycle()
        }
        val width = 160
        val height = 107
        val pixels = IntArray(width * height) { i ->
            0xFF000000.toInt() or ((i % width * 255 / (width - 1)) shl 16) or
                ((i / width * 255 / (height - 1)) shl 8) or ((i * 17 + i / width * 13) % 256)
        }
        val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        val effect = FocalFamily.effects.first()
        val expected = effect.cpu.apply(pixels, width, height, 1f, 42L)
        val drawingEnabled = HardwareRenderer.isDrawingEnabled()
        HardwareRenderer.setDrawingEnabled(true)
        val actual = try {
            checkNotNull(GpuReadback.render(context, source, effect.gpu, width, height, 42L, 1f))
        } finally {
            source.recycle()
            HardwareRenderer.setDrawingEnabled(drawingEnabled)
        }
        var max = 0
        var sum = 0L
        for (i in pixels.indices) for (shift in intArrayOf(0, 8, 16)) {
            val delta = abs(((expected[i] ushr shift) and 255) - ((actual[i] ushr shift) and 255))
            max = maxOf(max, delta)
            sum += delta
        }
        // Small outliers follow LUT/texture quantization; the old boundary defect reached 112.
        assertTrue("Focal mean=${sum.toDouble() / (pixels.size * 3)} max=$max",
            sum.toDouble() / (pixels.size * 3) < 0.8 && max <= 8)
    }
}
