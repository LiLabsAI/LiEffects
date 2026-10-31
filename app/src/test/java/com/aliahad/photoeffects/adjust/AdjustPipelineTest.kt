package com.aliahad.photoeffects.adjust

import com.aliahad.photoeffects.adjust.cpu.CpuAdjustRenderer
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.filter.FilterRegistry
import org.junit.Assert.*
import org.junit.Test

class AdjustPipelineTest {
    private val width = 64
    private val height = 64

    private fun source(): IntArray = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val r = x * 255 / (width - 1)
        val g = y * 255 / (height - 1)
        val b = (x + y) * 255 / (width + height - 2)
        val a = if ((x + y) % 11 == 0) 128 else 255
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun render(params: AdjustParams, input: IntArray = source()): IntArray =
        CpuAdjustRenderer(params, planFor(params)).apply(input, width, height, 1f, 42L)

    @Test fun catalogueHasEveryRequestedControl() {
        assertEquals(57, AdjustControl.entries.size)
        assertEquals(
            mapOf(
                AdjustCategory.Light to 10,
                AdjustCategory.Color to 7,
                AdjustCategory.Hsl to 18,
                AdjustCategory.Details to 5,
                AdjustCategory.Stylize to 4,
                AdjustCategory.Refocus to 3,
                AdjustCategory.Optical to 3,
                AdjustCategory.Blur to 7,
            ),
            AdjustCategory.entries.associateWith { AdjustControl.byCategory(it).size },
        )
        assertEquals(57, AdjustControl.entries.map { it.id }.toSet().size)
        for (control in AdjustControl.entries) {
            assertTrue(control.toString(), control.neutral in control.range)
            assertTrue(control.toString(), control.range.first <= control.range.last)
        }
        for (band in HslBand.entries) for (component in HslComponent.entries) {
            val control = AdjustControl.forHsl(band, component)
            assertEquals(band, AdjustControl.hslBandOf(control))
            assertEquals(component, AdjustControl.hslComponentOf(control))
        }
    }

    @Test fun identityAndIntensityZeroAreExactCopiesAndInputIsUntouched() {
        val src = source()
        val before = src.copyOf()
        val renderer = CpuAdjustRenderer(AdjustParams(), planFor(AdjustParams()))
        assertArrayEquals(src, renderer.apply(src, width, height, 1f, 7L))
        assertArrayEquals(src, renderer.apply(src, width, height, 0f, 7L))
        assertArrayEquals(src, renderer.apply(src, width, height, -1f, 7L))
        assertArrayEquals(before, src)
        val adjusted = AdjustParams().with(AdjustControl.Exposure, 40)
        val out = CpuAdjustRenderer(adjusted, planFor(adjusted)).apply(src, width, height, 1f, 7L)
        assertArrayEquals(out, CpuAdjustRenderer(adjusted, planFor(adjusted)).apply(src, width, height, 1f, 7L))
        for (i in src.indices) assertEquals(src[i] ushr 24, out[i] ushr 24)
        assertArrayEquals(before, src)
    }

    @Test fun everyControlChangesTheImageOrItsRefocusPartner() {
        val src = colorWheel()
        val failures = mutableListOf<String>()
        for (control in AdjustControl.entries) {
            if (control == AdjustControl.Dust) continue // sparse by design; covered separately
            val base = AdjustParams()
            val value = when (control) {
                AdjustControl.FocusPoint, AdjustControl.FocusRange -> 100
                else -> if (control.neutral != control.range.last) control.range.last else control.range.first
            }
            var params = base.with(control, value)
            if (control == AdjustControl.FocusPoint || control == AdjustControl.FocusRange) {
                params = params.with(AdjustControl.RefocusAmount, 100)
            }
            val input = if (control == AdjustControl.Skintone) {
                IntArray(width * height) { 0xFFD08A5A.toInt() }
            } else src
            if (input.contentEquals(render(params, input))) failures += control.name
        }
        assertTrue("controls with no pixel change: $failures", failures.isEmpty())
    }

    @Test fun dustProducesSparseSpecksOnALargerImage() {
        val w = 192
        val h = 192
        val src = colorWheel(w, h)
        val params = AdjustParams().with(AdjustControl.Dust, 100)
        val out = CpuAdjustRenderer(params, planFor(params)).apply(src, w, h, 1f, 17L)
        assertFalse(src.contentEquals(out))
        var changed = 0
        for (i in src.indices) if (src[i] != out[i]) changed++
        assertTrue("dust should stay sparse, changed=$changed", changed in 1 until w * h / 4)
    }

    private fun colorWheel(w: Int = width, h: Int = height): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val hue = x * 360f / w
        val sat = 0.9f
        val value = 0.35f + 0.65f * y / h
        val c = value * sat
        val hp = hue / 60f
        val xx = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val m = value - c
        val rgb = when {
            hp < 1f -> floatArrayOf(c, xx, 0f)
            hp < 2f -> floatArrayOf(xx, c, 0f)
            hp < 3f -> floatArrayOf(0f, c, xx)
            hp < 4f -> floatArrayOf(0f, xx, c)
            hp < 5f -> floatArrayOf(xx, 0f, c)
            else -> floatArrayOf(c, 0f, xx)
        }
        0xFF000000.toInt() or
            (((rgb[0] + m) * 255).toInt().coerceIn(0, 255) shl 16) or
            (((rgb[1] + m) * 255).toInt().coerceIn(0, 255) shl 8) or
            ((rgb[2] + m) * 255).toInt().coerceIn(0, 255)
    }

    @Test fun exposureContrastAndSaturationDirectionsArePredictable() {
        val gray = IntArray(16) { 0xFFA0A0A0.toInt() }
        fun mean(params: AdjustParams): Float {
            val out = CpuAdjustRenderer(params, planFor(params)).apply(gray, 4, 4, 1f, 1L)
            return out.map { ((it ushr 16) and 255) / 255f }.average().toFloat()
        }
        assertTrue(mean(AdjustParams().with(AdjustControl.Exposure, 50)) > mean(AdjustParams()))
        assertTrue(mean(AdjustParams().with(AdjustControl.Exposure, -50)) < mean(AdjustParams()))
        assertTrue(mean(AdjustParams().with(AdjustControl.Contrast, 100)) > mean(AdjustParams()))
        assertTrue(mean(AdjustParams().with(AdjustControl.Contrast, -100)) < mean(AdjustParams()))

        val color = IntArray(16) { 0xFF804020.toInt() }
        fun chroma(params: AdjustParams): Int {
            val out = CpuAdjustRenderer(params, planFor(params)).apply(color, 4, 4, 1f, 1L)
            return (out.first() ushr 16 and 255) - (out.first() and 255)
        }
        assertTrue(chroma(AdjustParams().with(AdjustControl.Saturation, 100)) >
            chroma(AdjustParams().with(AdjustControl.Saturation, -100)))
    }

    @Test fun hslRedBandDoesNotTouchAGreenPatch() {
        val w = 32
        val h = 32
        val red = IntArray(w * h) { 0xFFFF0000.toInt() }
        val green = IntArray(w * h) { 0xFF00FF00.toInt() }
        val params = AdjustParams().with(AdjustControl.HslRedHue, 100)
        val redOut = CpuAdjustRenderer(params, planFor(params)).apply(red, w, h, 1f, 3L)
        val greenOut = CpuAdjustRenderer(params, planFor(params)).apply(green, w, h, 1f, 3L)
        assertFalse(red.contentEquals(redOut))
        assertArrayEquals(green, greenOut)
    }

    @Test fun planAndBytesPerPixelTrackSpatialControls() {
        val identity = AdjustParams()
        assertEquals(AdjustPlan(false, false), planFor(identity))
        assertEquals(24, adjustBytesPerPixel(planFor(identity)))
        val detail = identity.with(AdjustControl.Sharpen, 30)
        assertEquals(AdjustPlan(true, false), planFor(detail))
        assertEquals(36, adjustBytesPerPixel(planFor(detail)))
        val blur = identity.with(AdjustControl.GaussianBlur, 30)
        assertEquals(AdjustPlan(false, true), planFor(blur))
        assertEquals(56, adjustBytesPerPixel(planFor(blur)))
        val both = detail.with(AdjustControl.GaussianBlur, 30)
        assertEquals(AdjustPlan(true, true), planFor(both))
        assertEquals(68, adjustBytesPerPixel(planFor(both)))
        assertEquals(AdjustPlan(false, false), planFor(blur.reset(AdjustCategory.Blur)))
    }

    @Test fun threeStageCompositionUsesFilterAdjustEffectOrderAndBudget() {
        val src = source()
        val filter = FilterRegistry.byId("g7x_1")!!
        val effect = EffectRegistry.byId("yesterday_1")!!
        val params = AdjustParams().with(AdjustControl.Exposure, 25)
        val adjust = AdjustEffectFactory.create(params)
        val edit = EditComposition.compose(
            EditStage(filter, 100),
            EditStage(adjust, 100),
            EditStage(effect, 40),
        )!!
        val expected = effect.cpu.apply(
            adjust.cpu.apply(
                filter.cpu.apply(src, width, height, 1f, 9L),
                width, height, 1f, 9L,
            ),
            width, height, 0.4f, 9L,
        )
        assertArrayEquals(expected, edit.cpu.apply(src, width, height, 1f, 9L))
        assertEquals(filter.id + "+" + adjust.id + "+" + effect.id, edit.id)
        assertTrue(edit.displayName.contains("G7X"))
        assertTrue(edit.displayName.contains("Adjust"))
        assertTrue(edit.displayName.contains("Yesterday"))
        assertNotNull(edit.stamp)
        assertEquals(0.4f, edit.stampIntensity, 0f)
        assertEquals(
            maxOf(filter.exportBytesPerPixel, adjust.exportBytesPerPixel, effect.exportBytesPerPixel) + 16,
            edit.exportBytesPerPixel,
        )
        assertEquals(effect.gpu.agslSource, edit.gpu.agslSource)
        assertEquals(adjust.gpu.agslSource, edit.gpu.inputProgram!!.agslSource)
        assertEquals(filter.gpu.agslSource, edit.gpu.inputProgram!!.inputProgram!!.agslSource)
    }

    @Test fun narrowImagesStaySafeAndDeterministic() {
        for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) {
            val src = IntArray(w * h) { 0xFF8090A0.toInt() }
            val params = AdjustParams().with(AdjustControl.Exposure, 20)
                .with(AdjustControl.Sharpen, 30)
                .with(AdjustControl.GaussianBlur, 10)
            val a = CpuAdjustRenderer(params, planFor(params)).apply(src, w, h, 1f, 5L)
            val b = CpuAdjustRenderer(params, planFor(params)).apply(src, w, h, 1f, 5L)
            assertArrayEquals(a, b)
            assertEquals(src.size, a.size)
            for (i in src.indices) assertEquals(src[i] ushr 24, a[i] ushr 24)
        }
    }

    @Test fun detailControlsPreserveFlatFieldsAndBlurReducesVariance() {
        val flat = IntArray(width * height) { 0xFF808080.toInt() }
        val flatParams = AdjustParams()
            .with(AdjustControl.Sharpen, 60)
            .with(AdjustControl.Clarity, 50)
            .with(AdjustControl.Ultrasharp, 40)
        val flatOut = CpuAdjustRenderer(flatParams, planFor(flatParams)).apply(flat, width, height, 1f, 2L)
        assertArrayEquals(flat, flatOut)

        val checker = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if ((x / 4 + y / 4) % 2 == 0) 0xFF202020.toInt() else 0xFFE0E0E0.toInt()
        }
        fun variance(pixels: IntArray): Double {
            val lumas = pixels.map { PixelMathLuma(it) }
            val mean = lumas.average()
            return lumas.sumOf { (it - mean) * (it - mean) } / lumas.size
        }
        val blurred = AdjustParams().with(AdjustControl.GaussianBlur, 100)
        val blurredOut = CpuAdjustRenderer(blurred, planFor(blurred)).apply(checker, width, height, 1f, 2L)
        assertTrue(variance(blurredOut) < variance(checker))
    }

    @Test fun opticalCentreIsInvariant() {
        val w = 65
        val h = 65
        val src = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            0xFF000000.toInt() or ((x * 255 / (w - 1)) shl 16) or
                ((y * 255 / (h - 1)) shl 8) or ((x + y) % 256)
        }
        val center = (h / 2) * w + w / 2
        for (params in listOf(
            AdjustParams().with(AdjustControl.Fisheye, 60),
            AdjustParams().with(AdjustControl.RgbSplit, 60),
            AdjustParams().with(AdjustControl.Aberration, 60),
        )) {
            val out = CpuAdjustRenderer(params, planFor(params)).apply(src, w, h, 1f, 2L)
            assertEquals(src[center], out[center])
        }
    }

    private fun PixelMathLuma(p: Int): Double =
        (0.2126 * ((p ushr 16) and 255) + 0.7152 * ((p ushr 8) and 255) + 0.0722 * (p and 255)) / 255.0

    @Test fun summaryAndResetAreConsistent() {
        val params = AdjustParams()
            .with(AdjustControl.Exposure, 20)
            .with(AdjustControl.HslRedHue, -30)
            .with(AdjustControl.Clarity, 40)
        assertFalse(params.isIdentity)
        assertEquals(3, params.editedControlCount)
        assertTrue(AdjustSummary.describe(params).contains("exposure"))
        val reset = params.reset(AdjustCategory.Light)
        assertFalse(reset.isIdentity)
        assertEquals(2, reset.editedControlCount)
        assertTrue(reset.reset(null).isIdentity)
    }
}
