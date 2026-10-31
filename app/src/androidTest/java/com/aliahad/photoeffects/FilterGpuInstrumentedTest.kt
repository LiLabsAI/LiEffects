package com.aliahad.photoeffects

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aliahad.photoeffects.effect.DustyEffectFactory
import com.aliahad.photoeffects.effect.DustyFamily
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.effect.GpuProgram
import com.aliahad.photoeffects.effect.PhotoEffect
import com.aliahad.photoeffects.effect.gpu.GpuEffectRenderer
import com.aliahad.photoeffects.effect.gpu.GpuReadback
import com.aliahad.photoeffects.filter.FilterRegistry
import com.aliahad.photoeffects.filter.FilterControl
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Real GPU readback; run on an emulator because the USB phone rejects test-APK installs. */
@RunWith(AndroidJUnit4::class)
class FilterGpuInstrumentedTest {
    private var width = 48
    private var height = 32
    private var transparentInput = false
    private fun source() = IntArray(width * height) { i ->
        val alpha = if (!transparentInput) 255 else when (i % 4) { 0 -> 0; 1 -> 64; 2 -> 128; else -> 255 }
        (alpha shl 24) or ((i % width * 255 / (width - 1)) shl 16) or
            ((i / width * 255 / (height - 1)) shl 8) or ((i * 17) % 256)
    }

    @Test fun allFilterShadersMatchTheirCpuMirrors() {
        for (preset in FilterRegistry.presets) {
            for ((strength, sharpen) in listOf(100 to preset.sharpen, 35 to 70, 0 to 0)) {
                val filter = FilterRegistry.withControls(preset.id, strength, sharpen)!!
                assertGpuMatchesCpu(filter, "${preset.id} filter=$strength sharpen=$sharpen")
            }
        }
    }

    @Test fun childShaderCompositionMatchesFilterBeforeEffect() {
        // An existing DUSTY affine grade with stochastic/spatial stages disabled isolates
        // stage order, strength and child-shader binding without CPU/GPU sin-hash variance.
        val grade = DustyEffectFactory.create(DustyFamily.presets.first().copy(
            aberration = 0f, blur = 0f, halation = 0f, grain = 0f,
            dust = 0f, scratches = 0f, texture = 0f, vignette = 0f,
        ))
        for (filter in FilterRegistry.all) {
            val edit = EditComposition.compose(EditStage(filter, 45), EditStage(grade, 65))!!
            assertGpuMatchesCpu(edit, "${filter.id} + affine DUSTY")
        }
    }

    @Test fun insomniaControlEndpointsMatchAtLargerResolution() {
        width = 320
        height = 213
        for (preset in FilterRegistry.presets.filter { it.family == "Insomnia" }) {
            for ((strength, softness, grain) in listOf(
                Triple(0, 0, 0), Triple(0, 100, 0), Triple(0, 0, 100),
                Triple(100, 0, 0), Triple(100, 100, 100),
            )) {
                val filter = FilterRegistry.withControls(preset.id, strength, 0, softness, grain)!!
                assertGpuMatchesCpu(filter, "${preset.id} F=$strength I=$softness G=$grain 320px")
            }
        }
    }

    @Test fun insomniaSoftFocusMatchesAtTransparentEdges() {
        width = 127
        height = 85
        transparentInput = true
        for (preset in FilterRegistry.presets.filter { it.family == "Insomnia" }) {
            val filter = FilterRegistry.withControls(preset.id, 70, 0, 100, 20)!!
            assertGpuMatchesCpu(filter, "${preset.id} transparent soft edges")
        }
    }

    @Test fun cineFilmColourAndGrainMatchAtTransparentEdges() {
        width = 160
        height = 107
        transparentInput = true
        for (preset in FilterRegistry.presets.filter { it.family == "Cine Film" }) {
            for ((strength, grain) in listOf(0 to 0, 0 to 100, 100 to 0, 70 to 40, 100 to 100)) {
                val filter = FilterRegistry.withControls(preset.id, strength, 0, grain = grain)!!
                assertGpuMatchesCpu(filter, "${preset.id} F=$strength G=$grain transparent")
            }
        }
    }

    @Test fun chicagoSignedToneControlsMatchAtTransparentEdges() {
        width = 193
        height = 127
        transparentInput = true
        val settings = listOf(
            listOf(0, 0, 0, 0, 0), listOf(0, 0, -100, 100, 100), listOf(0, 0, 100, -100, 20),
            listOf(100, 100, -100, -100, 100), listOf(100, 0, 100, 100, 0),
            listOf(100, 30, -40, 30, 20), listOf(40, 65, 75, -55, 100),
        )
        for (preset in FilterRegistry.presets.filter { it.family == "Chicago" }) {
            for ((f, g, h, s, a) in settings) {
                val filter = FilterRegistry.withControls(preset.id, f, 0, grain = g, highlights = h, shadows = s, aberration = a)!!
                assertGpuMatchesCpu(filter, "${preset.id} F=$f G=$g H=$h S=$s A=$a transparent")
            }
        }
    }

    @Test fun colourOnlyStrengthMatchesAtTransparentEdges() {
        width = 160
        height = 107
        transparentInput = true
        for (preset in FilterRegistry.presets.filter { it.controls == listOf(FilterControl.Strength) }) {
            for (strength in listOf(0, 35, 100)) {
                val filter = FilterRegistry.withControls(preset.id, strength, 0)!!
                assertGpuMatchesCpu(filter, "${preset.id} F=$strength transparent")
            }
        }
    }

    @Test fun sharpeningMatchesAtTransparentEdgesForEveryG7xVariant() {
        width = 160
        height = 107
        transparentInput = true
        for (preset in FilterRegistry.presets.filter { it.family == "G7X" }) {
            for (sharpen in listOf(0, 20, 100)) {
                val effect = FilterRegistry.withControls(preset.id, 100, sharpen)!!
                assertGpuMatchesCpu(effect, "${preset.id} transparent sharpen=$sharpen")
            }
        }
    }

    @Test fun pointSharpenUsesTheResizedSourcePixelGrid() {
        width = 193
        height = 127
        transparentInput = true
        val original = Bitmap.createBitmap(source(), width, height, Bitmap.Config.ARGB_8888)
        width = 160
        height = 107
        try {
            val scaled = Bitmap.createScaledBitmap(original, width, height, true)
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            scaled.recycle()
            val effect = FilterRegistry.withControls("g7x_1", 100, 100)!!
            assertPixelsMatchCpu(effect, "resized transparent sharpen", pixels, original, alphaLimit = 2)
        } finally {
            original.recycle()
        }
    }

    @Test fun compiledProgramsHaveIndependentMutableBindings() {
        val program = FilterRegistry.byId("g7x_1")!!.gpu
        val first = checkNotNull(GpuEffectRenderer.compile(program))
        val second = checkNotNull(GpuEffectRenderer.compile(program))
        assertNotSame("Each graph must own its uniforms and child bindings", first, second)
    }

    private fun assertGpuMatchesCpu(effect: PhotoEffect, label: String) {
        val src = source()
        // Canonicalize the bitmap's 8-bit premultiplied storage before either backend reads it.
        Bitmap.createBitmap(src, width, height, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.getPixels(src, 0, width, 0, 0, width, height)
            bitmap.recycle()
        }
        assertPixelsMatchCpu(effect, label, src)
    }

    private fun assertPixelsMatchCpu(
        effect: PhotoEffect,
        label: String,
        src: IntArray,
        bitmapSource: Bitmap? = null,
        alphaLimit: Int = 1,
    ) {
        val expected = effect.cpu.apply(src, width, height, 1f, 42L)
        val actual = renderGpu(effect.gpu, src, bitmapSource)
        var sum = 0L
        var max = 0
        for (i in expected.indices) {
            val alpha = expected[i] ushr 24
            // Bilinear texture sampling can quantize coverage by one 8-bit level.
            assertTrue("$label alpha at $i", abs(alpha - (actual[i] ushr 24)) <= alphaLimit)
            for (shift in intArrayOf(0, 8, 16)) {
                // ImageReader gives premultiplied RGBA; CPU renderers return straight ARGB.
                val channel = (((expected[i] ushr shift) and 255) * alpha + 127) / 255
                val delta = abs(channel - ((actual[i] ushr shift) and 255))
                sum += delta
                max = maxOf(max, delta)
            }
        }
        val mean = sum.toDouble() / (expected.size * 3)
        Log.i("FilterParity", "$label mean=$mean max=$max")
        assertTrue("$label GPU/CPU mean=$mean max=$max", mean < 0.8 && max <= 4)
    }

    private fun renderGpu(program: GpuProgram, pixels: IntArray, bitmapSource: Bitmap? = null): IntArray {
        // AndroidJUnitRunner can disable hardware output in headless environments.
        val wasDrawingEnabled = HardwareRenderer.isDrawingEnabled()
        HardwareRenderer.setDrawingEnabled(true)
        val source = bitmapSource ?: Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            return checkNotNull(
                GpuReadback.render(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    source,
                    program,
                    width,
                    height,
                    42L,
                    1f,
                ),
            ) { "GPU readback produced no image" }
        } finally {
            if (bitmapSource == null) source.recycle()
            HardwareRenderer.setDrawingEnabled(wasDrawingEnabled)
        }
    }
}
