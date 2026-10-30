package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.cpu.PixelMath
import org.junit.Assert.*
import org.junit.Test

class BurgundyPipelineTest {
    private val ids = (1..3).map { "burgundy_$it" }

    @Test fun threeColourOnlyVariantsBlendFromTheUntouchedSourceAndPreserveAlpha() {
        assertEquals(ids, FilterRegistry.shelfById("Burgundy")!!.effects.map { it.id })
        val input = intArrayOf(0x80304050.toInt(), 0xFF404040.toInt(), 0xFFB4785A.toInt(),
            0xFF20C060.toInt(), 0xFFD0D0D0.toInt())
        val before = input.copyOf()
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
            fun render(strength: Int) = FilterRegistry.withControls(id, strength, 0)!!
                .cpu.apply(input, input.size, 1, 1f, 71L)
            assertArrayEquals(id, input, render(0))
            val full = render(100)
            val half = render(50)
            for (i in input.indices) {
                assertEquals(input[i] ushr 24, full[i] ushr 24)
                assertEquals(input[i] ushr 24, half[i] ushr 24)
                for (shift in intArrayOf(0, 8, 16)) {
                    val expected = (((input[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                    assertEquals(expected, ((half[i] ushr shift) and 255).toFloat(), 1f)
                }
            }
            assertArrayEquals(before, input)
        }
    }

    @Test fun winePalettesTintNeutralShadowsWhileRetainingWarmAndGreenColourSeparation() {
        fun pixel(id: String, color: Int) = FilterRegistry.byId(id)!!.cpu
            .apply(intArrayOf(color), 1, 1, 1f, 71L).single()
        for (id in ids) {
            val shadow = pixel(id, 0xFF404040.toInt())
            assertTrue(id, PixelMath.red(shadow) > PixelMath.blue(shadow))
            assertTrue(id, PixelMath.blue(shadow) > PixelMath.green(shadow))
            val warm = pixel(id, 0xFFB4785A.toInt())
            assertTrue(id, PixelMath.red(warm) > PixelMath.green(warm))
            val green = pixel(id, 0xFF20C060.toInt())
            assertTrue(id, PixelMath.green(green) > PixelMath.red(green))
            assertTrue(id, PixelMath.green(green) > PixelMath.blue(green))
        }
        fun brightness(id: String): Float {
            val c = pixel(id, 0xFF606060.toInt())
            return PixelMath.luma(PixelMath.red(c), PixelMath.green(c), PixelMath.blue(c))
        }
        assertTrue(brightness("burgundy_3") > brightness("burgundy_2") + 0.08f)
    }
}
