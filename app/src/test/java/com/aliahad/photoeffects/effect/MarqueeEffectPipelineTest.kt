package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MarqueeEffectPipelineTest {
    private val width = 193
    private val height = 127

    private fun source() = IntArray(width * height) { i ->
        val alpha = intArrayOf(0, 64, 128, 192, 255)[i % 5]
        (alpha shl 24) or (((i * 17) % 256) shl 16) or
            (((i * 31) % 256) shl 8) or ((i * 7) % 256)
    }

    /** Opaque probe colours: shadow, midtone, skin, foliage, sky. */
    private val palette = listOf(
        0xFF14161A.toInt(), // shadow
        0xFF808080.toInt(), // midtone
        0xFFD9A68C.toInt(), // skin
        0xFF4C8038.toInt(), // foliage
        0xFF598CD9.toInt(), // sky
    )

    /**
     * Renders each colour as a 1x1 image, which is exactly the frame centre (uv = 0) at any
     * resolution because the falloff is normalized. Callers pass grain = 0.
     */
    private fun render(params: MarqueeParams, colors: List<Int>): List<Int> {
        val effect = MarqueeEffectFactory.create(params)
        return colors.map { effect.cpu.apply(intArrayOf(it), 1, 1, 1f, 42L).single() }
    }

    @Test fun presetsAreRegisteredWithStableIdsAndExportBudgets() {
        assertEquals(listOf("marquee_premiere", "marquee_midnight"), MarqueeFamily.effects.map { it.id })
        assertEquals(listOf("Premiere", "Midnight"), MarqueeFamily.effects.map { it.displayName })
        assertEquals(MarqueeFamily.effects, EffectRegistry.shelfById("Marquee")!!.effects)
        for (effect in MarqueeFamily.effects) {
            assertEquals(effect, EffectRegistry.byId(effect.id))
            assertEquals(16, effect.exportBytesPerPixel)
            assertTrue(effect in EffectRegistry.forCategory(EffectCategory.New))
            assertTrue(effect in EffectRegistry.forCategory(EffectCategory.Films))
            assertTrue(effect.defaultIntensity in 1..100)
            assertTrue(effect.gpu.extraShaderAssets.isEmpty())
            assertTrue(effect.gpu.internalPasses.isEmpty())
        }
    }

    @Test fun everyUniformIsBoundByTheShaderAndEveryShaderUniformIsBound() {
        val declared = Regex("""uniform float (\w+);""").findAll(MARQUEE_AGSL)
            .map { it.groupValues[1] }.toSet() - setOf("uIntensity")
        for (params in MarqueeFamily.presets) {
            assertEquals("${params.id} uniform set", declared, params.toUniforms().keys)
        }
    }

    @Test fun endpointsAreClampedIdentityIsACopyAndInputIsUntouched() {
        val src = source()
        val original = src.copyOf()
        for (effect in MarqueeFamily.effects) {
            for (strength in listOf(-1f, 0f)) {
                val out = effect.cpu.apply(src, width, height, strength, 42L)
                assertArrayEquals(src, out)
                assertNotSame(src, out)
            }
            assertArrayEquals(
                effect.cpu.apply(src, width, height, 1f, 42L),
                effect.cpu.apply(src, width, height, 3f, 42L),
            )
        }
        assertArrayEquals(original, src)
    }

    @Test fun alphaAndHiddenTransparentPixelsArePreserved() {
        val src = source()
        for (effect in MarqueeFamily.effects) {
            val out = effect.cpu.apply(src, width, height, 1f, 42L)
            for (i in src.indices) {
                assertEquals(src[i] ushr 24, out[i] ushr 24)
                if (src[i] ushr 24 == 0) assertEquals(src[i], out[i])
            }
        }
    }

    @Test fun repeatedParallelRendersAreDeterministicAndSeedsChangeTexture() {
        val src = IntArray(width * height) { 0xFF808080.toInt() }
        for (effect in MarqueeFamily.effects) {
            val first = effect.cpu.apply(src, width, height, 1f, 42L)
            repeat(3) { assertArrayEquals(first, effect.cpu.apply(src, width, height, 1f, 42L)) }
            val other = effect.cpu.apply(src, width, height, 1f, 999L)
            assertTrue(first.indices.count { first[it] != other[it] } > src.size / 10)
        }
    }

    @Test fun intensityInterpolatesTheCompletedLook() {
        val src = source()
        for (effect in MarqueeFamily.effects) {
            val full = effect.cpu.apply(src, width, height, 1f, 42L)
            val half = effect.cpu.apply(src, width, height, 0.5f, 42L)
            for (i in src.indices) for (shift in listOf(0, 8, 16)) {
                val expected = (((src[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                assertTrue(abs(expected - ((half[i] ushr shift) and 255)) <= 1f)
            }
        }
    }

    @Test fun shadowsGoNavyWhileSkinStaysWarm() {
        for (params in MarqueeFamily.presets) {
            val out = render(params.copy(grain = 0f), palette)
            val shadow = out[0]
            val skin = out[2]
            assertTrue(
                "${params.id} shadows must be blue, not neutral: $shadow",
                PixelMath.blue(shadow) > PixelMath.red(shadow) + 0.015f,
            )
            assertTrue(
                "${params.id} skin must keep its warm ordering: $skin",
                PixelMath.red(skin) > PixelMath.green(skin) &&
                    PixelMath.green(skin) > PixelMath.blue(skin),
            )
            assertTrue(
                "${params.id} skin must stay warmer than the shadows",
                PixelMath.red(skin) - PixelMath.blue(skin) >
                    PixelMath.red(shadow) - PixelMath.blue(shadow) + 0.10f,
            )
        }
    }

    @Test fun skinProtectionRaisesWarmChromaWithoutTouchingCoolHues() {
        for (params in MarqueeFamily.presets) {
            val plain = params.copy(grain = 0f, skinProtect = 0f)
            val protected = params.copy(grain = 0f)
            val a = render(plain, palette)
            val b = render(protected, palette)
            assertTrue(
                "${params.id} skin must gain chroma from protection: ${chroma(a[2])} -> ${chroma(b[2])}",
                chroma(b[2]) > chroma(a[2]) * 1.3f,
            )
            for (index in listOf(0, 1, 3, 4)) {
                assertEquals(
                    "${params.id} protection must not touch patch $index",
                    a[index], b[index],
                )
            }
        }
    }

    @Test fun highlightShoulderRollsOffInsteadOfClipping() {
        val ramp = List(256) { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        for (params in MarqueeFamily.presets) {
            // Isolate the tone curve: no spatial falloff, no grain.
            val out = render(params.copy(grain = 0f, falloff = 0f), ramp)
            assertTrue("${params.id} white must roll off, not clip", ((out.last() ushr 16) and 255) < 250)
            val lowStep = luma(out[105]) - luma(out[100])
            val highStep = luma(out[255]) - luma(out[250])
            assertTrue(
                "${params.id} highlights must compress: $highStep vs $lowStep",
                highStep < lowStep * 0.8,
            )
        }
    }

    @Test fun toneCurveIsMonotoneOnANeutralRamp() {
        val ramp = List(256) { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        for (params in MarqueeFamily.presets) {
            val out = render(params.copy(grain = 0f, falloff = 0f), ramp)
            for (i in 1 until out.size) {
                assertTrue(
                    "${params.id} reversed the ramp at $i",
                    luma(out[i]) >= luma(out[i - 1]) - 1.0 / 255.0,
                )
            }
        }
    }

    @Test fun matteBlackFloorIsBlueBiasedAndBounded() {
        for (params in MarqueeFamily.presets) {
            val black = render(params.copy(grain = 0f), listOf(0xFF000000.toInt())).single()
            assertTrue("${params.id} black floor must be blue biased", PixelMath.blue(black) > PixelMath.red(black))
            assertTrue("${params.id} black floor must stay dark", luma(black) < 0.06)
            assertTrue("${params.id} black floor must lift off zero", luma(black) > 0.0)
        }
    }

    @Test fun extremeColoursStayInGamutAndKeepTheirHueOrdering() {
        val extremes = listOf(0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF0000FF.toInt(), 0xFF00FF00.toInt())
        for (params in MarqueeFamily.presets) {
            val out = render(params.copy(grain = 0f), extremes)
            for ((index, pixel) in out.withIndex()) {
                for (shift in listOf(0, 8, 16)) {
                    val channel = (pixel ushr shift) and 255
                    assertTrue("${params.id} patch $index clipped to white", channel < 255)
                }
            }
            assertTrue("${params.id} red lost its hue", PixelMath.red(out[1]) > PixelMath.blue(out[1]))
            assertTrue("${params.id} blue lost its hue", PixelMath.blue(out[2]) > PixelMath.red(out[2]))
        }
    }

    @Test fun falloffDarkensThePeripheryAndTheTwoGeometriesDiffer() {
        val src = IntArray(width * height) { 0xFF808080.toInt() }
        val centre = height / 2 * width + width / 2
        val outputs = MarqueeFamily.presets.map {
            MarqueeEffectFactory.create(it.copy(grain = 0f)).cpu.apply(src, width, height, 1f, 42L)
        }
        for ((index, out) in outputs.withIndex()) {
            val id = MarqueeFamily.presets[index].id
            for (corner in listOf(0, width - 1, (height - 1) * width, (height - 1) * width + width - 1)) {
                assertTrue("$id corner $corner must fall away", luma(out[corner]) < luma(out[centre]) - 0.03f)
            }
        }
        // Premiere keys from the upper left; Midnight keeps a narrow off-centre band.
        val premiere = outputs[0]
        val midnight = outputs[1]
        val upperLeft = height / 4 * width + width / 4
        val lowerRight = height * 3 / 4 * width + width * 3 / 4
        assertTrue(luma(premiere[upperLeft]) > luma(premiere[lowerRight]) + 0.02f)
        val band = height / 2 * width + (width * 0.38f).toInt()
        val rightEdge = height / 2 * width + width * 9 / 10
        assertTrue(luma(midnight[band]) > luma(midnight[rightEdge]) + 0.02f)
        assertNotEquals(premiere.toList(), midnight.toList())
    }

    @Test fun normalizedFalloffSurvivesExportScalingAndAspectRatios() {
        for (p in MarqueeFamily.presets) for ((w, h) in listOf(61 to 91, 91 to 61, 61 to 61)) {
            val effect = MarqueeEffectFactory.create(p.copy(grain = 0f))
            val small = effect.cpu.apply(IntArray(w * h) { 0xFF808080.toInt() }, w, h, 1f, 42L)
            val large = effect.cpu.apply(IntArray(w * h * 9) { 0xFF808080.toInt() }, w * 3, h * 3, 1f, 42L)
            for (y in 0 until h) for (x in 0 until w) {
                val a = small[y * w + x]
                val b = large[(y * 3 + 1) * w * 3 + x * 3 + 1]
                assertTrue("${p.id} scale at $x,$y", PixelMath.absDiff(a, b) <= 3)
            }
        }
    }

    @Test fun tinyImagesAndExtremePhotoSeedsStayDeterministic() {
        for (effect in MarqueeFamily.effects) for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) {
            val input = IntArray(w * h) { 0xFFC08866.toInt() }
            for (seed in listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)) {
                val output = effect.cpu.apply(input, w, h, 1f, seed)
                assertEquals(input.size, output.size)
                assertArrayEquals(output, effect.cpu.apply(input, w, h, 1f, seed))
                assertTrue(output.all { it ushr 24 == 255 })
            }
        }
    }

    private fun chroma(pixel: Int): Float {
        val luma = luma(pixel)
        val r = PixelMath.red(pixel) - luma
        val g = PixelMath.green(pixel) - luma
        val b = PixelMath.blue(pixel) - luma
        return r * r + g * g + b * b
    }

    private fun luma(pixel: Int) =
        PixelMath.luma(PixelMath.red(pixel), PixelMath.green(pixel), PixelMath.blue(pixel))
}