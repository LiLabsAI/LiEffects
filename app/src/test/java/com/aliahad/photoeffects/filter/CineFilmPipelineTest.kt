package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.*
import org.junit.Test

class CineFilmPipelineTest {
    private val width = 73
    private val height = 49
    private val ids = (1..3).map { "cine_film_$it" }

    private fun source() = IntArray(width * height) { i ->
        val alpha = if (i % 9 == 0) 128 else 255
        (alpha shl 24) or ((48 + i % width * 160 / (width - 1)) shl 16) or
            ((48 + i / width * 160 / (height - 1)) shl 8) or (64 + i * 17 % 128)
    }

    private fun render(id: String, strength: Int, grain: Int, src: IntArray, seed: Long = 97L) =
        requireNotNull(FilterRegistry.withControls(id, strength, 0, grain = grain))
            .cpu.apply(src, width, height, 1f, seed)

    @Test fun cineFilmExposesThreeVariantsWithIndependentColourAndGrain() {
        assertEquals(ids, FilterRegistry.shelfById("Cine Film")!!.effects.map { it.id })
        for (id in ids) {
            val p = FilterRegistry.paramsById(id)!!
            assertEquals(listOf(FilterControl.Strength, FilterControl.Grain), p.controls)
            assertEquals(0.7f, p.strength, 0f)
            assertEquals(40, p.grain)
            assertEquals(0, p.sharpen)
            assertEquals(0f, p.softFocus, 0f)
            assertEquals(16, FilterRegistry.byId(id)!!.exportBytesPerPixel)
        }
    }

    @Test fun grainWorksAtZeroColourStrengthAndRemainsSeededAndAlphaSafe() {
        val src = source()
        val before = src.copyOf()
        for (id in ids) {
            assertArrayEquals(id, src, render(id, 0, 0, src))
            val grainOnly = render(id, 0, 100, src)
            assertFalse(id, src.contentEquals(grainOnly))
            assertArrayEquals(id, grainOnly, render(id, 0, 100, src))
            assertFalse(id, grainOnly.contentEquals(render(id, 0, 100, src, 98L)))
            assertFalse(id, grainOnly.contentEquals(render(id, 100, 100, src)))
            for (i in src.indices) assertEquals(src[i] ushr 24, grainOnly[i] ushr 24)
            assertArrayEquals(before, src)
        }
    }

    @Test fun colourStrengthBlendsTheGradeWithoutIntroducingSpatialBlur() {
        val src = source()
        for (id in ids) {
            val full = render(id, 100, 0, src)
            val half = render(id, 50, 0, src)
            val filter = FilterRegistry.withControls(id, 100, 0, grain = 0)!!
            for (i in src.indices step 31) {
                // A pointwise grade gives the same pixel regardless of its neighbours.
                assertEquals(full[i], filter.cpu.apply(intArrayOf(src[i]), 1, 1, 1f, 97L).single())
                for (shift in intArrayOf(0, 8, 16)) {
                    val expected = (((src[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                    assertEquals(expected, ((half[i] ushr shift) and 255).toFloat(), 1f)
                }
            }
            assertTrue(id, full.indices.any { PixelMath.absDiff(src[it], full[it]) > 12 })
        }
    }
}
