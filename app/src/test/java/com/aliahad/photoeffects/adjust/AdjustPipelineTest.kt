package com.aliahad.photoeffects.adjust

import com.aliahad.photoeffects.adjust.cpu.CpuAdjustRenderer
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.filter.FilterRegistry
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToInt
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
        assertEquals(59, AdjustControl.entries.size)
        assertEquals(
            mapOf(
                AdjustCategory.Light to 11,
                AdjustCategory.Color to 7,
                AdjustCategory.Hsl to 18,
                AdjustCategory.Details to 6,
                AdjustCategory.Stylize to 4,
                AdjustCategory.Refocus to 3,
                AdjustCategory.Optical to 3,
                AdjustCategory.Blur to 7,
            ),
            AdjustCategory.entries.associateWith { AdjustControl.byCategory(it).size },
        )
        assertEquals(59, AdjustControl.entries.map { it.id }.toSet().size)
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

    @Test fun whitesAndColorDenoiseMetadataAndDefaultsAreExplicit() {
        val whites = AdjustControl.Whites
        assertEquals(AdjustCategory.Light, whites.category)
        assertEquals("Whites", whites.label)
        assertEquals(-100..100, whites.range)
        assertEquals(0, whites.neutral)
        assertEquals(AdjustPass.Color, whites.pass)
        assertTrue(whites.signed)
        val light = AdjustControl.byCategory(AdjustCategory.Light)
        assertEquals(whites, light[light.indexOf(AdjustControl.Highlights) + 1])

        val denoise = AdjustControl.ChromaDenoise
        assertEquals(AdjustCategory.Details, denoise.category)
        assertEquals("Color denoise", denoise.label)
        assertEquals(0..100, denoise.range)
        assertEquals(0, denoise.neutral)
        assertEquals(AdjustPass.Detail, denoise.pass)
        assertFalse(denoise.signed)
        val defaults = AdjustParams()
        assertEquals(0, defaults.light.whites)
        assertEquals(0, defaults.details.chromaDenoise)
        assertEquals(0, defaults.value(whites))
        assertEquals(0, defaults.value(denoise))
        assertTrue(defaults.isIdentity)
        assertEquals(0, defaults.editedControlCount)
        assertEquals("uWhites", AdjustUniforms.WHITES)
        assertEquals("uChromaDenoise", AdjustUniforms.CHROMA_DENOISE)
    }

    @Test fun whitesAndColorDenoiseClampValuesAndNormalizeUniforms() {
        val defaults = AdjustParams()
        for ((control, uniform) in listOf(
            AdjustControl.Whites to AdjustUniforms.WHITES,
            AdjustControl.ChromaDenoise to AdjustUniforms.CHROMA_DENOISE,
        )) {
            assertEquals(0f, defaults.scalarUniforms().getValue(uniform), 0f)
            for (value in listOf(Int.MIN_VALUE, -100, -37, 0, 42, 100, Int.MAX_VALUE)) {
                val expected = value.coerceIn(control.range)
                val params = defaults.with(control, value)
                assertEquals(control.name, expected, params.value(control))
                assertEquals(expected / 100f, params.scalarUniforms().getValue(uniform), 0f)
                assertEquals(expected == 0, params.isIdentity)
                assertEquals(if (expected == 0) 0 else 1, params.editedControlCount)
                assertEquals(0, defaults.value(control))
            }
        }
        val both = defaults.with(AdjustControl.Whites, -37).with(AdjustControl.ChromaDenoise, 42)
        assertEquals(-37, both.light.whites)
        assertEquals(42, both.details.chromaDenoise)
        assertEquals(-0.37f, both.scalarUniforms().getValue(AdjustUniforms.WHITES), 0f)
        assertEquals(0.42f, both.scalarUniforms().getValue(AdjustUniforms.CHROMA_DENOISE), 0f)
        assertEquals(2, both.editedControlCount)
    }

    @Test fun whitesAndColorDenoiseUseExpectedBudgetAndStickyDetailUntilReset() {
        val whites = AdjustParams().with(AdjustControl.Whites, 60)
        assertEquals(AdjustPlan(detail = false, blur = false, color = true), planFor(whites))
        assertEquals(24, adjustBytesPerPixel(planFor(whites)))
        assertEquals(AdjustParams(), whites.with(AdjustControl.Whites, 0))
        val denoise = AdjustParams().with(AdjustControl.ChromaDenoise, 60)
        assertEquals(AdjustPlan(detail = true, blur = false), planFor(denoise))
        assertEquals(36, adjustBytesPerPixel(planFor(denoise)))
        val both = whites.with(AdjustControl.ChromaDenoise, 60)
        assertEquals(AdjustPlan(detail = true, blur = false, color = true), planFor(both))
        assertEquals(36, adjustBytesPerPixel(planFor(both)))
        assertEquals(denoise, both.reset(AdjustCategory.Light))
        assertEquals(whites, both.reset(AdjustCategory.Details))

        val neutral = denoise.with(AdjustControl.ChromaDenoise, 0)
        assertTrue(neutral.isIdentity)
        assertEquals(0, neutral.editedControlCount)
        assertTrue(neutral.layers.detail)
        assertEquals(planFor(denoise), planFor(neutral))
        assertEquals(36, adjustBytesPerPixel(planFor(neutral)))
        assertEquals(AdjustParams(), neutral.reset(AdjustCategory.Details))
        assertEquals(24, adjustBytesPerPixel(planFor(neutral.reset(AdjustCategory.Details))))
        assertEquals(AdjustParams(), both.reset(null))

        // Cleanup shares Detail but lives in Light; resetting Details must not unmount it.
        val cleanup = denoise.with(AdjustControl.Cleanup, 25).reset(AdjustCategory.Details)
        assertEquals(0, cleanup.details.chromaDenoise)
        assertEquals(25, cleanup.light.cleanup)
        assertTrue(cleanup.layers.detail)
        assertEquals(AdjustParams(), cleanup.reset(AdjustCategory.Light))
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

    @Test fun newControlsPreserveIdentityAlphaAndInputAndRenderDeterministically() {
        val src = chromaNoise()
        val alphas = intArrayOf(0, 1, 64, 128, 255)
        for (i in src.indices) src[i] = (alphas[i % alphas.size] shl 24) or (src[i] and 0xFFFFFF)
        val before = src.copyOf()
        for (control in listOf(AdjustControl.Whites, AdjustControl.ChromaDenoise)) {
            val neutral = AdjustParams().with(control, 100).with(control, 0)
            val identity = render(neutral, src)
            assertArrayEquals(control.name, src, identity)
            assertNotSame(src, identity)
        }
        for (params in listOf(
            AdjustParams().with(AdjustControl.Whites, -100),
            AdjustParams().with(AdjustControl.Whites, 100),
            AdjustParams().with(AdjustControl.ChromaDenoise, 100),
            AdjustParams().with(AdjustControl.Whites, 60).with(AdjustControl.ChromaDenoise, 70),
        )) {
            val renderer = CpuAdjustRenderer(params, planFor(params))
            val out = renderer.apply(src, width, height, 1f, 42L)
            assertNotSame(src, out)
            assertArrayEquals(out, renderer.apply(src, width, height, 1f, 42L))
            assertArrayEquals(out, renderer.apply(src, width, height, 1f, 99L))
            assertArrayEquals(src, renderer.apply(src, width, height, 0f, 42L))
            for (i in src.indices) assertEquals("alpha at $i", src[i] ushr 24, out[i] ushr 24)
            assertArrayEquals(before, src)
        }
    }

    @Test fun newControlsRenderClampedEndpointsExactly() {
        val src = chromaNoise()
        for (control in listOf(AdjustControl.Whites, AdjustControl.ChromaDenoise)) {
            for ((requested, endpoint) in listOf(Int.MIN_VALUE to control.range.first, Int.MAX_VALUE to control.range.last)) {
                val clamped = AdjustParams().with(control, requested)
                val expected = AdjustParams().with(control, endpoint)
                assertEquals(expected, clamped)
                assertArrayEquals("${control.name} at $requested", render(expected, src), render(clamped, src))
            }
        }
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
            val input = when (control) {
                AdjustControl.Skintone -> IntArray(width * height) { 0xFFD08A5A.toInt() }
                AdjustControl.ChromaDenoise -> chromaNoise()
                else -> src
            }
            val output = render(params, input)
            if (control == AdjustControl.ChromaDenoise) {
                assertFalse(
                    "Color denoise must change chroma speckles; flat/local chroma may correctly be unchanged",
                    input.contentEquals(output),
                )
            }
            if (input.contentEquals(output)) failures += control.name
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

    private fun chromaNoise(): IntArray {
        val random = Random(73L)
        return IntArray(width * height) {
            // Opposite low-amplitude chroma speckles around gray: luma differs by only 0.0416/255.
            val sign = if (random.nextBoolean()) 1 else -1
            val r = 128 + 12 * sign
            val g = 128 - 4 * sign
            val b = 128 + 4 * sign
            0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
        }
    }

    private fun chromaEnergy(pixels: IntArray): Double = pixels.sumOf { pixel ->
        val luma = PixelMathLuma(pixel)
        val r = ((pixel ushr 16) and 255) / 255.0 - luma
        val g = ((pixel ushr 8) and 255) / 255.0 - luma
        val b = (pixel and 255) / 255.0 - luma
        r * r + g * g + b * b
    } / pixels.size

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

    @Test fun whitesPreserveBlackAndNeutralMonotoneRampsWhileTargetingHighlights() {
        val ramp = IntArray(256) { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        for (value in listOf(-100, -50, 50, 100)) {
            val params = AdjustParams().with(AdjustControl.Whites, value)
            val out = CpuAdjustRenderer(params, planFor(params)).apply(ramp, 256, 1, 1f, 42L)
            assertEquals("Whites $value must preserve black", ramp[0], out[0])
            var previous = 0
            for (i in ramp.indices) {
                val r = (out[i] ushr 16) and 255
                val g = (out[i] ushr 8) and 255
                val b = out[i] and 255
                val level = i / 255.0
                val expected = (i * (1.0 + 0.20 * (value / 100.0) * level * level * level))
                    .roundToInt().coerceIn(0, 255)
                assertTrue("Whites $value at $i: expected $expected, got $r", abs(expected - r) <= 1)
                assertEquals(r, g)
                assertEquals(r, b)
                assertTrue("Whites $value reversed ramp at $i", r >= previous)
                previous = r
            }
            val midtoneDelta = (out[128] and 255) - 128
            val highlightDelta = (out[224] and 255) - 224
            assertTrue("Whites $value must follow its signed direction", highlightDelta * value > 0)
            assertTrue("Whites must affect highlights much more than midtones", abs(highlightDelta) > 4 * abs(midtoneDelta))
        }
    }

    @Test fun whitesApplyCommonRgbGainAfterBlackPointAndBeforeFade() {
        val colors = intArrayOf(0xFFCC8844.toInt(), 0xFF4080C0.toInt())
        val src = IntArray(width * height) { colors[it % colors.size] }
        for (value in listOf(-100, 100)) for ((blackPoint, fade) in listOf(0 to 0, 50 to 60)) {
            val params = AdjustParams().with(AdjustControl.Whites, value)
                .with(AdjustControl.BlackPoint, blackPoint)
                .with(AdjustControl.Fade, fade)
            val out = render(params, src)
            for (i in colors.indices) {
                val bp = 0.12 * blackPoint / 100.0
                val channels = listOf(16, 8, 0).map { shift ->
                    ((((src[i] ushr shift) and 255) / 255.0 - bp) / (1.0 - bp)).coerceAtLeast(0.0)
                }
                val peak = channels.max().coerceIn(0.0, 1.0)
                val gain = 1.0 + 0.20 * (value / 100.0) * peak * peak * peak
                val f = 0.14 * fade / 100.0
                for ((channel, shift) in listOf(16, 8, 0).withIndex()) {
                    // One RGB gain preserves channel ratios until clipping; fade follows that gain.
                    val expected = ((f + channels[channel] * gain * (1.0 - f)) * 255)
                        .roundToInt().coerceIn(0, 255)
                    val actual = (out[i] ushr shift) and 255
                    assertTrue("Whites $value, black point $blackPoint, fade $fade, channel $channel", abs(expected - actual) <= 1)
                }
            }
        }
    }

    @Test fun colorDenoiseReducesChromaEnergyMonotonicallyWithoutChangingLuma() {
        val src = chromaNoise()
        val initialEnergy = chromaEnergy(src)
        var previousEnergy = initialEnergy
        for (strength in listOf(25, 50, 75, 100)) {
            val out = render(AdjustParams().with(AdjustControl.ChromaDenoise, strength), src)
            for (i in src.indices) {
                assertEquals("Color denoise $strength changed luma at $i", PixelMathLuma(src[i]), PixelMathLuma(out[i]), 1.0 / 255.0)
            }
            val energy = chromaEnergy(out)
            assertTrue("Color denoise $strength must reduce chroma energy ($energy vs $previousEnergy)", energy < previousEnergy)
            previousEnergy = energy
        }
        assertTrue("Full color denoise must meaningfully reduce speckles", previousEnergy < initialEnergy * 0.75)
    }

    @Test fun colorDenoisePreservesLumaWhenChromaNeedsGamutCompression() {
        // Zero-blue-headroom yellows force gamut compression rather than channel clipping.
        val src = IntArray(width * height) { i ->
            if ((i % width + i / width) % 2 == 0) 0xFFC0C000.toInt() else 0xFFD0D000.toInt()
        }
        val out = render(AdjustParams().with(AdjustControl.ChromaDenoise, 100), src)
        for (i in src.indices) {
            assertEquals("Gamut compression changed luma at $i", PixelMathLuma(src[i]), PixelMathLuma(out[i]), 1.0 / 255.0)
        }
    }

    @Test fun colorDenoisePreservesMonochromeTextureFlatColorsAndStrongEdges() {
        val fixtures = linkedMapOf(
            "monochrome texture" to IntArray(width * height) { i ->
                val gray = (i % width * 37 + i / width * 17) and 255
                0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            },
            "strong luma edge" to IntArray(width * height) { i ->
                if (i % width < width / 2) 0xFF503028.toInt() else 0xFFE0C8B8.toInt()
            },
            "strong nearly equal-luma chroma edge" to IntArray(width * height) { i ->
                if (i % width < width / 2) 0xFFFF0000.toInt() else 0xFF004C00.toInt()
            },
        )
        for (color in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF806040.toInt(), 0xFFFF2080.toInt())) {
            fixtures["flat color $color"] = IntArray(width * height) { color }
        }
        val params = AdjustParams().with(AdjustControl.ChromaDenoise, 100)
        for ((name, src) in fixtures) assertArrayEquals(name, src, render(params, src))
    }

    @Test fun colorDenoiseIgnoresInvisibleNeighborChroma() {
        val noise = chromaNoise()
        val src = IntArray(width * height) { i ->
            if (i % 3 == 0) noise[i] and 0xFFFFFF else 0xFF808080.toInt()
        }
        val out = render(AdjustParams().with(AdjustControl.ChromaDenoise, 100), src)
        for (i in src.indices) {
            assertEquals(src[i] ushr 24, out[i] ushr 24)
            if (src[i] ushr 24 != 0) {
                assertEquals("Invisible chroma must not bleed into visible pixel $i", src[i], out[i])
            }
        }
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
        val filter = FilterRegistry.byId("lucent_1")!!
        val effect = EffectRegistry.byId("daymark_1")!!
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
        assertTrue(edit.displayName.contains("Lucent"))
        assertTrue(edit.displayName.contains("Adjust"))
        assertTrue(edit.displayName.contains("Daymark"))
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
                .with(AdjustControl.Whites, 40)
                .with(AdjustControl.ChromaDenoise, 60)
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
