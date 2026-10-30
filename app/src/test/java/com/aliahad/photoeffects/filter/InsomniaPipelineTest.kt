package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Behavioural contracts for independent colour, soft-focus and grain controls. */
class InsomniaPipelineTest {
    private val width = 160
    private val height = 96
    private val ids = (1..6).map { "insomnia_$it" }

    private fun source() = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val alpha = if (i % 11 == 0) 128 else 255
        val r = 48 + x * 144 / (width - 1)
        val g = 48 + y * 144 / (height - 1)
        val b = if ((x + y) % 2 == 0) 64 else 192
        (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun render(
        id: String,
        strength: Int,
        softness: Int,
        grain: Int,
        input: IntArray = source(),
        seed: Long = 103L,
    ): IntArray = requireNotNull(FilterRegistry.withControls(id, strength, 0, softness, grain))
        .cpu.apply(input, width, height, 1f, seed)

    @Test fun catalogueExposesSixVariantsWithTheObservedIndependentControlsAndDefaults() {
        val shelf = requireNotNull(FilterRegistry.shelfById("Insomnia"))
        assertEquals(ids, shelf.effects.map { it.id })
        assertEquals((1..6).map { "Insomnia #$it" }, shelf.effects.map { it.displayName })
        for (id in ids) {
            val p = requireNotNull(FilterRegistry.paramsById(id))
            assertEquals(listOf(FilterControl.Strength, FilterControl.Intensity, FilterControl.Grain), p.controls)
            assertEquals(0.7f, p.strength, 0f)
            assertEquals(70, p.intensity)
            assertEquals(20, p.grain)
            assertEquals(0, p.sharpen)
            assertTrue("Softness must have a nonzero radius", p.softFocus > 0f)
            assertTrue(requireNotNull(FilterRegistry.byId(id)).exportBytesPerPixel > 0)
        }
    }

    @Test fun zeroingAllThreeControlsRestoresTheSourceAndGlobalZeroAlwaysBypassesTheLook() {
        val src = source()
        for (id in ids) {
            assertArrayEquals(id, src, render(id, 0, 0, 0, src))
            val preset = requireNotNull(FilterRegistry.byId(id))
            assertArrayEquals(id, src, preset.cpu.apply(src, width, height, 0f, 103L))
        }
    }

    @Test fun zeroSoftnessRetainsColourAndAllowsGrainToChangeTheImage() {
        val src = source()
        for (id in ids) {
            val colour = render(id, 70, 0, 0, src)
            val colourAndGrain = render(id, 70, 0, 80, src)
            assertFalse("$id colour should remain at Intensity 0", src.contentEquals(colour))
            assertFalse("$id grain should remain adjustable at Intensity 0", colour.contentEquals(colourAndGrain))
        }
    }

    @Test fun zeroColourStrengthRetainsSoftnessAndGrainAsIndependentOperations() {
        val src = source()
        for (id in ids) {
            assertFalse("$id soft focus is independent of colour", src.contentEquals(render(id, 0, 100, 0, src)))
            assertFalse("$id grain is independent of colour", src.contentEquals(render(id, 0, 0, 100, src)))
        }
    }

    @Test fun softFocusReducesHighFrequencyDetailWhenGrainAndColourAreDisabled() {
        val src = IntArray(width * height) { i ->
            if (((i % width) + (i / width)) % 2 == 0) 0xFF606060.toInt() else 0xFFA0A0A0.toInt()
        }
        fun edgeEnergy(pixels: IntArray): Long {
            var energy = 0L
            for (y in 0 until height) for (x in 1 until width) {
                val i = y * width + x
                energy += abs((pixels[i] and 255) - (pixels[i - 1] and 255))
            }
            return energy
        }
        for (id in ids) {
            val sharp = render(id, 0, 0, 0, src)
            val soft = render(id, 0, 100, 0, src)
            assertArrayEquals(src, sharp)
            assertTrue("$id should soften fine detail", edgeEnergy(soft) < edgeEnergy(sharp) * 0.6)
        }
    }

    @Test fun grainIsRepeatableSeededAndPreservesInputAndAlpha() {
        val src = source()
        val before = src.copyOf()
        for (id in ids) {
            val a = render(id, 70, 70, 70, src, 91L)
            val b = render(id, 70, 70, 70, src, 92L)
            assertArrayEquals(id, a, render(id, 70, 70, 70, src, 91L))
            assertTrue("$id should vary its grain with the source seed", a.indices.count { a[it] != b[it] } > a.size / 3)
            for (i in a.indices) assertEquals("$id alpha at $i", src[i] ushr 24, a[i] ushr 24)
            assertArrayEquals(before, src)
        }
    }

    @Test fun independentControlValuesAreClampedToTheirSupportedRanges() {
        val src = source()
        for (id in ids) {
            assertArrayEquals(id, render(id, 0, 0, 0, src), render(id, -20, -40, -60, src))
            assertArrayEquals(id, render(id, 100, 100, 100, src), render(id, 180, 220, 250, src))
        }
    }

    @Test fun theEarlierNineRecipesKeepTheirOriginalControlSetAndDisableNewOperations() {
        for (family in listOf("G7X", "Dim", "Vanilla")) {
            val shelf = requireNotNull(FilterRegistry.shelfById(family))
            assertEquals(3, shelf.effects.size)
            for (effect in shelf.effects) {
                val p = requireNotNull(FilterRegistry.paramsById(effect.id))
                assertEquals(listOf(FilterControl.Strength, FilterControl.Sharpen), p.controls)
                assertEquals(0, p.grain)
                assertEquals(0f, p.softFocus, 0f)
                assertEquals(1f, p.strength, 0f)
                assertEquals(if (family == "G7X") 20 else 0, p.sharpen)
            }
        }
    }

    @Test fun diagnosticPointsKeepTheMeasuredCoreAndHorizontalWings() {
        // Independently generated chart: 3x3 white point on dark gray. Broad bands account
        // for JPEG variation in the ordinary reference exports; no reference image is bundled.
        val w = 1600
        val h = 81
        val x = 800
        val y = 40
        val source = IntArray(w * h) { 0xFF202020.toInt() }
        for (dy in -1..1) for (dx in -1..1) source[(y + dy) * w + x + dx] = 0xFFE0E0E0.toInt()
        fun at(pixels: IntArray, dx: Int, dy: Int) = pixels[(y + dy) * w + x + dx] and 255
        val low = FilterRegistry.withControls("insomnia_1", 0, 0, 25, 0)!!.cpu.apply(source, w, h, 1f, 1L)
        val high = FilterRegistry.withControls("insomnia_1", 0, 0, 100, 0)!!.cpu.apply(source, w, h, 1f, 1L)
        assertTrue("Low-intensity sharp core", at(low, 0, 0) in 112..135)
        assertTrue("High-intensity retained core", at(high, 0, 0) in 82..105)
        assertTrue("Horizontal wing grows with Intensity", at(high, 20, 0) > at(low, 20, 0))
        assertTrue("Transverse spread stays narrow", at(high, 0, 8) <= 34)
        assertTrue(at(high, 0, 0) < at(low, 0, 0))
    }

    @Test fun directionalFiltersReserveTheirFloatingPointWorkingBuffer() {
        for (id in ids) assertTrue(FilterRegistry.byId(id)!!.exportBytesPerPixel >= 32)
        for (id in listOf("g7x_1", "dim_1", "vanilla_1")) {
            assertEquals(16, FilterRegistry.byId(id)!!.exportBytesPerPixel)
        }
    }
}
