package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test

class FilterSoftFocusTest {
    @Test fun zeroRadiusSamplesTheOriginalAndOverwritesTheOutput() {
        val source = intArrayOf(0xFF102030.toInt(), 0xFF80A0C0.toInt())
        val before = source.copyOf()
        val out = floatArrayOf(9f, 8f, 7f)
        FilterSoftFocus.sample(source, 2, 1, 1, 0, 0f, out)
        assertArrayEquals(floatArrayOf(128f / 255f, 160f / 255f, 192f / 255f), out, 0f)
        assertArrayEquals(before, source)
    }

    @Test fun flatFieldsAndNarrowImagesRemainFlatAtAnyRadius() {
        val expected = floatArrayOf(128f / 255f, 160f / 255f, 192f / 255f)
        val out = FloatArray(3)
        for ((width, height) in listOf(1 to 1, 1 to 13, 13 to 1, 7 to 5)) {
            val source = IntArray(width * height) { 0xFF80A0C0.toInt() }
            for (radius in listOf(0f, 0.5f, 1f, 12.5f)) {
                for (y in 0 until height) for (x in 0 until width) {
                    FilterSoftFocus.sample(source, width, height, x, y, radius, out)
                    assertArrayEquals(expected, out, 0.000001f)
                }
            }
        }
    }

    @Test fun aUnitRadiusUsesTheExpectedSeparableImpulseWeights() {
        val source = IntArray(25) { 0xFF000000.toInt() }.apply { this[12] = 0xFFFFFFFF.toInt() }
        val out = FloatArray(3)
        for ((x, y, expected) in listOf(Triple(2, 2, 0.25f), Triple(1, 2, 0.125f), Triple(1, 1, 0.0625f))) {
            FilterSoftFocus.sample(source, 5, 5, x, y, 1f, out)
            assertArrayEquals(floatArrayOf(expected, expected, expected), out, 0f)
        }
    }

    @Test fun fractionalRadiusInterpolatesBetweenPixelCentresAndClampsAtTheEdge() {
        val source = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        val out = FloatArray(3)
        // Centre weight 1/2 plus two half-white samples, each weighted 1/4.
        FilterSoftFocus.sample(source, 3, 1, 1, 0, 0.5f, out)
        assertArrayEquals(floatArrayOf(0.75f, 0.75f, 0.75f), out, 0f)
        // Outside-left samples clamp to black; the right sample at x=.5 is half white.
        FilterSoftFocus.sample(source, 3, 1, 0, 0, 0.5f, out)
        assertArrayEquals(floatArrayOf(0.125f, 0.125f, 0.125f), out, 0f)
    }

    @Test fun fractionalTransparentEdgesDoNotDarkenTheOpaqueNeighbourOrLeakHiddenColour() {
        val out = FloatArray(3)
        for (transparent in listOf(0x00000000, 0x00FF00FF)) {
            val source = intArrayOf(transparent, 0xFFFFFFFF.toInt())
            val before = source.copyOf()
            FilterSoftFocus.sample(source, 2, 1, 1, 0, 0.5f, out)
            assertArrayEquals(floatArrayOf(1f, 1f, 1f), out, 0f)
            assertArrayEquals(before, source)
        }
    }

    @Test fun fractionalMixedAlphaSamplesWeightColourByCoverageBeforeUnpremultiplying() {
        val source = intArrayOf(0x40FF0000, 0x800000FF.toInt())
        val out = FloatArray(3)
        // The fractional left tap is 1/3 red + 2/3 blue after coverage weighting.
        // Its kernel weight is 1/4; the other 3/4 of the kernel samples pure blue.
        FilterSoftFocus.sample(source, 2, 1, 1, 0, 0.5f, out)
        assertArrayEquals(floatArrayOf(1f / 12f, 0f, 11f / 12f), out, 0.000001f)
    }

    @Test fun fullyTransparentFilteredTapsReturnZeroColour() {
        val source = intArrayOf(0x00FF00FF, 0x0000FF00)
        val out = floatArrayOf(1f, 2f, 3f)
        FilterSoftFocus.sample(source, 2, 1, 1, 0, 0.5f, out)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), out, 0f)
    }
}
