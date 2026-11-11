package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.*
import org.junit.Test

class EstuaryPipelineTest {
    private val ids = (1..3).map { "estuary_$it" }

    @Test fun threeVariantsExposeColourStrengthWithoutHiddenSpatialOrTextureStages() {
        assertEquals(ids, FilterRegistry.shelfById("Estuary")!!.effects.map { it.id })
        for (id in ids) {
            val p = FilterRegistry.paramsById(id)!!
            assertEquals(listOf(FilterControl.Strength), p.controls)
            assertEquals(1f, p.strength, 0f)
            assertEquals(0, p.sharpen)
            assertEquals(0, p.grain)
            assertEquals(0, p.highlights)
            assertEquals(0, p.shadows)
            assertEquals(0, p.aberration)
            assertEquals(0f, p.softFocus, 0f)
            assertEquals(16, FilterRegistry.byId(id)!!.exportBytesPerPixel)
        }
    }

    @Test fun strengthZeroRestoresTheSourceAndHalfStrengthBlendsTheCompleteLook() {
        val w = 61
        val h = 43
        val input = IntArray(w * h) { i ->
            ((if (i % 5 == 0) 128 else 255) shl 24) or
                ((i % w * 255 / (w - 1)) shl 16) or
                ((i / w * 255 / (h - 1)) shl 8) or (i * 19 % 256)
        }
        val before = input.copyOf()
        for (id in ids) {
            fun render(strength: Int) = FilterRegistry.withControls(id, strength, 0)!!
                .cpu.apply(input, w, h, 1f, 71L)
            assertArrayEquals(id, input, render(0))
            val full = render(100)
            val half = render(50)
            for (i in input.indices) {
                assertEquals(input[i] ushr 24, half[i] ushr 24)
                for (shift in intArrayOf(0, 8, 16)) {
                    val expected = (((input[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                    assertEquals(expected, ((half[i] ushr shift) and 255).toFloat(), 1f)
                }
            }
            assertArrayEquals(before, input)
        }
    }

    @Test fun authoredPalettesCoolNeutralShadowsWhileKeepingAWarmInputWarm() {
        fun pixel(id: String, color: Int) = FilterRegistry.byId(id)!!.cpu
            .apply(intArrayOf(color), 1, 1, 1f, 71L).single()
        for (id in ids) {
            val shadow = pixel(id, 0xFF404040.toInt())
            assertTrue(id, PixelMath.green(shadow) > PixelMath.red(shadow))
            assertTrue(id, PixelMath.blue(shadow) > PixelMath.red(shadow))
            val warm = pixel(id, 0xFFB4785A.toInt())
            assertTrue(id, PixelMath.red(warm) > PixelMath.green(warm))
            assertTrue(id, PixelMath.green(warm) > PixelMath.blue(warm))
        }
        fun brightness(id: String): Float {
            val c = pixel(id, 0xFF606060.toInt())
            return PixelMath.luma(PixelMath.red(c), PixelMath.green(c), PixelMath.blue(c))
        }
        assertTrue(brightness("estuary_3") > brightness("estuary_2") + 0.08f)
    }
}
