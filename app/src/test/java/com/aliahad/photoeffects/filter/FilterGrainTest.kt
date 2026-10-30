package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FilterGrainTest {
    @Test fun noiseIsRepeatableBoundedAndChangesWithTheSeed() {
        var changed = 0
        for (y in 0 until 64) for (x in 0 until 96) {
            val n = FilterGrain.noise(x, y, 96, 64, 65432, 1.7f)
            assertEquals(n, FilterGrain.noise(x, y, 96, 64, 65432, 1.7f), 0f)
            assertTrue(n.isFinite() && n in -1f..1f)
            if (abs(n - FilterGrain.noise(x, y, 96, 64, 65433, 1.7f)) > 0.01f) changed++
        }
        assertTrue("Changing the photo seed must change grain", changed > 96 * 64 * 0.95)
    }

    @Test fun grainHasLowBiasAndUsefulVariationAcrossTheImage() {
        var sum = 0.0
        var squares = 0.0
        var count = 0
        var low = 0
        var high = 0
        for (y in 0 until 768 step 3) for (x in 0 until 1024 step 3) {
            val n = FilterGrain.noise(x, y, 1024, 768, 21987, 1.7f).toDouble()
            sum += n
            squares += n * n
            if (n < -0.25) low++
            if (n > 0.25) high++
            count++
        }
        val mean = sum / count
        val variance = squares / count - mean * mean
        assertTrue("Grain mean $mean should be near zero", abs(mean) < 0.015)
        assertTrue("Grain variance $variance should avoid a flat/noisy extreme", variance in 0.07..0.22)
        assertTrue(low > count / 8 && high > count / 8)
    }

    @Test fun resizingPreservesTheGrainFieldAtMatchingPixelCentres() {
        // At scale 3, centre x+0.5 maps exactly to centre (3*x+1)+0.5.
        for (y in 0 until 53 step 3) for (x in 0 until 79 step 3) {
            val a = FilterGrain.noise(x, y, 79, 53, 175, 2f)
            val b = FilterGrain.noise(3 * x + 1, 3 * y + 1, 237, 159, 175, 2f)
            assertEquals("Pixel centre $x,$y", a, b, 0.0005f)
        }
    }

    @Test fun largerGrainHasSmootherNeighbouringSamples() {
        fun variation(size: Float): Double {
            var total = 0.0
            for (y in 0 until 100) for (x in 0 until 200) {
                val a = FilterGrain.noise(x, y, 1600, 1000, 79, size)
                val b = FilterGrain.noise(x + 1, y, 1600, 1000, 79, size)
                total += abs(a - b)
            }
            return total
        }
        assertTrue(variation(4f) < variation(1f) * 0.65)
    }

    @Test fun narrowImagesAndSeedExtremesStayFinite() {
        for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) {
            for (seed in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE)) {
                for (size in listOf(0f, 0.25f, 100f)) {
                    for (y in 0 until h) for (x in 0 until w) {
                        val n = FilterGrain.noise(x, y, w, h, seed, size)
                        assertTrue(n.isFinite() && n in -1f..1f)
                    }
                }
            }
        }
    }
}
