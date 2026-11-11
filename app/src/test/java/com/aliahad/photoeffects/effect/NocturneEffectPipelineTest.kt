package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NocturneEffectPipelineTest {
    private val width = 193
    private val height = 127
    private fun source() = IntArray(width * height) { i ->
        val alpha = intArrayOf(0, 64, 128, 192, 255)[i % 5]
        (alpha shl 24) or (((i * 17) % 256) shl 16) or
            (((i * 31) % 256) shl 8) or ((i * 7) % 256)
    }

    @Test fun presetsAreRegisteredWithStableIdsAndExportBudgets() {
        assertEquals(listOf("nocturne_gilded", "nocturne_afterhours"), NocturneFamily.effects.map { it.id })
        assertEquals(listOf("Gilded", "Afterhours"), NocturneFamily.effects.map { it.displayName })
        assertEquals(NocturneFamily.effects, EffectRegistry.shelfById("Nocturne")!!.effects)
        for (effect in NocturneFamily.effects) {
            assertEquals(effect, EffectRegistry.byId(effect.id))
            assertEquals(16, effect.exportBytesPerPixel)
            assertTrue(effect in EffectRegistry.forCategory(EffectCategory.New))
            assertTrue(effect.defaultIntensity in 1..100)
            assertTrue(effect.gpu.extraShaderAssets.isEmpty())
            assertTrue(effect.gpu.internalPasses.isEmpty())
        }
    }

    @Test fun endpointsAreClampedIdentityIsACopyAndInputIsUntouched() {
        val src = source()
        val original = src.copyOf()
        for (effect in NocturneFamily.effects) {
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
        for (effect in NocturneFamily.effects) {
            val out = effect.cpu.apply(src, width, height, 1f, 42L)
            for (i in src.indices) {
                assertEquals(src[i] ushr 24, out[i] ushr 24)
                if (src[i] ushr 24 == 0) assertEquals(src[i], out[i])
            }
        }
    }

    @Test fun repeatedParallelRendersAreDeterministicAndSeedsChangeTexture() {
        val src = IntArray(width * height) { 0xFF808080.toInt() }
        for (effect in NocturneFamily.effects) {
            val first = effect.cpu.apply(src, width, height, 1f, 42L)
            repeat(3) { assertArrayEquals(first, effect.cpu.apply(src, width, height, 1f, 42L)) }
            val other = effect.cpu.apply(src, width, height, 1f, 999L)
            assertTrue(first.indices.count { first[it] != other[it] } > src.size / 10)
        }
    }

    @Test fun intensityInterpolatesTheCompletedLook() {
        val src = source()
        for (effect in NocturneFamily.effects) {
            val full = effect.cpu.apply(src, width, height, 1f, 42L)
            val half = effect.cpu.apply(src, width, height, 0.5f, 42L)
            for (i in src.indices) for (shift in listOf(0, 8, 16)) {
                val expected = (((src[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                assertTrue(abs(expected - ((half[i] ushr shift) and 255)) <= 1f)
            }
        }
    }

    @Test fun lightingGeometryAndPalettesAreDistinct() {
        val src = IntArray(width * height) { 0xFF808080.toInt() }
        val outputs = NocturneFamily.presets.map {
            NocturneEffectFactory.create(it.copy(grain = 0f)).cpu.apply(src, width, height, 1f, 42L)
        }
        val center = height / 2 * width + width / 2
        val key = height / 2 * width + (width * 0.4f).toInt()
        val rim = height / 2 * width + (width * 0.76f).toInt()
        val gilded = outputs[0]
        val afterhours = outputs[1]
        assertTrue(PixelMath.red(gilded[key]) > PixelMath.blue(gilded[key]) + 0.035f)
        assertTrue(PixelMath.blue(afterhours[rim]) > PixelMath.red(afterhours[rim]) + 0.025f)
        assertTrue(luma(gilded[key]) > luma(gilded[0]) + 0.08f)
        assertTrue(luma(afterhours[rim]) > luma(afterhours[center]) + 0.02f)
        assertTrue(luma(gilded[key]) > luma(gilded[rim]))
        assertNotEquals(gilded.toList(), afterhours.toList())
    }

    @Test fun blacksStayBlackAndDarkSkinRetainsColorAndDetail() {
        val ramp = listOf(0xFF000000, 0xFF24160F, 0xFF432B20, 0xFF694632, 0xFF93664B)
        for (effect in NocturneFamily.effects) {
            val colors = ramp.map { color ->
                effect.cpu.apply(intArrayOf(color.toInt()), 1, 1, 1f, 42L).single()
            }
            assertEquals(0xFF000000.toInt(), colors.first())
            for (i in 1 until colors.size) {
                assertTrue("${effect.id} must retain shadow detail", luma(colors[i]) > luma(colors[i - 1]) + 0.02f)
                assertTrue(PixelMath.red(colors[i]) > PixelMath.green(colors[i]))
                assertTrue(PixelMath.green(colors[i]) > PixelMath.blue(colors[i]))
            }
        }
    }

    @Test fun normalizedLightPlacementSurvivesExportScalingAndAspectRatios() {
        for (p in NocturneFamily.presets) for ((w, h) in listOf(61 to 91, 91 to 61, 61 to 61)) {
            val effect = NocturneEffectFactory.create(p.copy(grain = 0f))
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
        for (effect in NocturneFamily.effects) for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) {
            val input = IntArray(w * h) { 0xFFC08866.toInt() }
            for (seed in listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)) {
                val output = effect.cpu.apply(input, w, h, 1f, seed)
                assertEquals(input.size, output.size)
                assertArrayEquals(output, effect.cpu.apply(input, w, h, 1f, seed))
                assertTrue(output.all { it ushr 24 == 255 })
            }
        }
    }

    private fun luma(pixel: Int) = PixelMath.luma(PixelMath.red(pixel), PixelMath.green(pixel), PixelMath.blue(pixel))
}
