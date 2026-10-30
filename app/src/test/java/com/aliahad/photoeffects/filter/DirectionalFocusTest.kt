package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DirectionalFocusTest {
    @Test fun zeroControlsReturnAnUnmodifiedIndependentCopy() {
        val input = intArrayOf(0x00FF00FF, 0x80A05020.toInt(), 0xFF123456.toInt())
        val out = DirectionalFocus.render(input, 3, 1, 0f, 0f, 0f, 1f)
        assertArrayEquals(input, out)
        assertNotSame(input, out)
    }

    @Test fun flatFieldsStayFlatIncludingPartialOpacityAndLocalDetail() {
        for (pixel in listOf(0xFF80A0C0.toInt(), 0x8080A0C0.toInt())) {
            val input = IntArray(31 * 17) { pixel }
            val out = DirectionalFocus.render(input, 31, 17, 8.5f, 1.3f, 1.5f, 0.8f)
            assertArrayEquals(input, out)
        }
    }

    @Test fun impulseEnergySpreadsStronglyAlongTheHorizontalAxis() {
        val w = 129
        val h = 65
        val input = IntArray(w * h) { 0xFF000000.toInt() }.apply { this[(h / 2) * w + w / 2] = 0xFFFFFFFF.toInt() }
        val out = DirectionalFocus.render(input, w, h, 18f, 1.4f, 0f, 1f)
        var sum = 0.0
        var horizontalMoment = 0.0
        var verticalMoment = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val brightness = (out[y * w + x] and 255).toDouble()
            sum += brightness
            horizontalMoment += brightness * (x - w / 2) * (x - w / 2)
            verticalMoment += brightness * (y - h / 2) * (y - h / 2)
        }
        assertTrue(sum > 100)
        assertTrue("Horizontal variance must dominate: $horizontalMoment vs $verticalMoment", horizontalMoment > verticalMoment * 8)
        assertTrue(horizontalMoment / sum > 20)
    }

    @Test fun fractionalFilteringDoesNotLeakTransparentHiddenColoursOrDarkenWhite() {
        val w = 40
        val h = 9
        val input = IntArray(w * h) { if (it % w < w / 2) 0x00FF00FF else 0xFFFFFFFF.toInt() }
        val out = DirectionalFocus.render(input, w, h, 7.5f, 1.2f, 0f, 0f)
        assertEquals(0xFFFFFFFF.toInt(), out[(h / 2) * w + w / 2])
        for (i in input.indices) assertEquals(input[i] ushr 24, out[i] ushr 24)
        val transparent = IntArray(9) { 0x00FF00FF }
        assertArrayEquals(IntArray(9), DirectionalFocus.render(transparent, 3, 3, 2f, 1f, 0f, 0f))
    }

    @Test fun opacityInputAndDeterminismSurviveTheDirectionalAndDetailStages() {
        val w = 47
        val h = 13
        val input = IntArray(w * h) { i ->
            val alpha = listOf(0, 64, 128, 255)[i % 4]
            (alpha shl 24) or (((i * 31) % 256) shl 16) or (((i * 17) % 256) shl 8) or ((i * 7) % 256)
        }
        val before = input.copyOf()
        val a = DirectionalFocus.render(input, w, h, 5.3f, 0.8f, 0.7f, 1.1f)
        val b = DirectionalFocus.render(input, w, h, 5.3f, 0.8f, 0.7f, 1.1f)
        assertArrayEquals(a, b)
        assertArrayEquals(before, input)
        for (i in input.indices) assertEquals(input[i] ushr 24, a[i] ushr 24)
    }

    @Test fun skinnyImagesAndLargeClampedOffsetsAreSupported() {
        for ((w, h) in listOf(1 to 1, 1 to 19, 19 to 1)) {
            val input = IntArray(w * h) { 0xFF708090.toInt() }
            assertArrayEquals(input, DirectionalFocus.render(input, w, h, 20f, 10f, 0.5f, 3f))
        }
    }

    @Test fun smallRadiusChangesAreSmootherThanLargeChanges() {
        val w = 129
        val input = IntArray(w) { if (it < w / 2) 0xFF404040.toInt() else 0xFFB0B0B0.toInt() }
        val a = DirectionalFocus.render(input, w, 1, 6f, 0f, 0f, 0f)
        val b = DirectionalFocus.render(input, w, 1, 6.1f, 0f, 0f, 0f)
        val c = DirectionalFocus.render(input, w, 1, 12f, 0f, 0f, 0f)
        fun difference(p: IntArray, q: IntArray) = p.indices.sumOf { abs((p[it] and 255) - (q[it] and 255)) }
        assertTrue(difference(a, b) < difference(a, c) / 5)
    }
}
