package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test

class GraphitePipelineTest {
    private val ids = (1..8).map { "graphite_$it" }
    private val width = 83
    private val height = 47
    private fun source() = IntArray(width * height) { i ->
        ((if (i % 7 == 0) 128 else 255) shl 24) or
            ((i % width * 255 / (width - 1)) shl 16) or
            ((i / width * 255 / (height - 1)) shl 8) or (i * 13 % 256)
    }
    private fun render(id: String, f: Int, g: Int, h: Int, s: Int, input: IntArray = source(), seed: Long = 17L, a: Int = 0) =
        FilterRegistry.withControls(id, f, 0, grain = g, highlights = h, shadows = s, aberration = a)!!
            .cpu.apply(input, width, height, 1f, seed)

    @Test fun eightPrintRecipesExposeFourControlsWithSignedToneRanges() {
        assertEquals(ids, FilterRegistry.shelfById("Graphite")!!.effects.map { it.id })
        val controls = mapOf(
            1 to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Highlights, FilterControl.Shadows),
            2 to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Highlights, FilterControl.Aberration),
            4 to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Aberration),
            5 to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Aberration),
        )
        for (id in ids) {
            val p = FilterRegistry.paramsById(id)!!
            assertEquals(controls[p.variant] ?: listOf(FilterControl.Strength, FilterControl.Grain), p.controls)
            assertEquals(1f, p.strength, 0f)
            assertEquals(30, p.grain)
            assertEquals(when (p.variant) { 1 -> -40; 2 -> -50; else -> 0 }, p.highlights)
            assertEquals(if (p.variant == 1) 30 else 0, p.shadows)
            assertEquals(if (p.variant in listOf(2, 4, 5)) 20 else 0, p.aberration)
            assertEquals(0f, p.saturation, 0f)
            assertEquals(0, p.sharpen)
            assertEquals(0f, p.softFocus, 0f)
            // Aberration keeps one graded ARGB frame for channel separation.
            assertEquals(if (p.aberration > 0) 20 else 16, FilterRegistry.byId(id)!!.exportBytesPerPixel)
        }
        assertEquals(-100..100, FilterControl.Highlights.range)
        assertEquals(-100..100, FilterControl.Shadows.range)
        assertEquals(0..100, FilterControl.Strength.range)
        assertEquals(0..100, FilterControl.Grain.range)
        for (p in FilterRegistry.presets) {
            if (FilterControl.Highlights !in p.controls) assertEquals(p.id, 0, p.highlights)
            if (FilterControl.Shadows !in p.controls) assertEquals(p.id, 0, p.shadows)
        }
    }

    @Test fun colourGrainAndToneControlsRemainIndependentAndGlobalZeroBypassesAll() {
        val src = source()
        for (id in ids) {
            assertArrayEquals(id, src, render(id, 0, 0, 0, 0, src))
            assertArrayEquals(id, src, FilterRegistry.byId(id)!!.cpu.apply(src, width, height, 0f, 17L))
            assertFalse(id, src.contentEquals(render(id, 100, 0, 0, 0, src)))
            assertFalse(id, src.contentEquals(render(id, 0, 100, 0, 0, src)))
            assertFalse(id, src.contentEquals(render(id, 0, 0, 100, 0, src)))
            assertFalse(id, src.contentEquals(render(id, 0, 0, 0, -100, src)))
        }
    }

    @Test fun signedToneCurvesKeepNeutralRampsMonotoneAndCanLiftBlackOrCompressWhite() {
        val ramp = IntArray(256) { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        for (h in listOf(-100, -40, 0, 30, 100)) for (s in listOf(-100, -40, 0, 30, 100)) {
            val out = FilterRegistry.withControls("graphite_1", 0, 0, grain = 0, highlights = h, shadows = s)!!
                .cpu.apply(ramp, 256, 1, 1f, 17L)
            if (s > 0) assertTrue((out.first() and 255) > 0) else assertEquals(ramp.first(), out.first())
            if (h < 0) assertTrue((out.last() and 255) < 255) else assertEquals(ramp.last(), out.last())
            for (i in out.indices) {
                val gray = out[i] and 255
                assertEquals(gray, (out[i] ushr 8) and 255)
                assertEquals(gray, (out[i] ushr 16) and 255)
                if (i > 0) assertTrue("H=$h S=$s at $i", gray >= (out[i - 1] and 255))
            }
            if (h == 0 && s == 0) assertArrayEquals(ramp, out)
        }
    }

    @Test fun highlightsAndShadowsTargetDifferentTonalRegionsInBothDirections() {
        fun at(gray: Int, h: Int, s: Int): Int {
            val p = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            return FilterRegistry.withControls("graphite_1", 0, 0, grain = 0, highlights = h, shadows = s)!!
                .cpu.apply(intArrayOf(p), 1, 1, 1f, 17L).single() and 255
        }
        for (direction in listOf(-100, 100)) {
            val shadowChange = kotlin.math.abs(at(32, 0, direction) - 32)
            val highlightOnShadow = kotlin.math.abs(at(32, direction, 0) - 32)
            val highlightChange = kotlin.math.abs(at(224, direction, 0) - 224)
            val shadowOnHighlight = kotlin.math.abs(at(224, 0, direction) - 224)
            assertTrue(shadowChange > highlightOnShadow * 4)
            assertTrue(highlightChange > shadowOnHighlight * 4)
            assertTrue(if (direction > 0) at(32, 0, direction) > 32 else at(32, 0, direction) < 32)
            assertTrue(if (direction > 0) at(224, direction, 0) > 224 else at(224, direction, 0) < 224)
        }
    }

    @Test fun signedControlsClampAndCombinedRenderingPreservesAlphaInputAndSeedDeterminism() {
        val src = source()
        val before = src.copyOf()
        for (id in ids) {
            assertArrayEquals(id, render(id, 100, 100, -100, 100, src), render(id, 200, 200, -200, 200, src))
            assertArrayEquals(id, render(id, 100, 100, -100, 100, src, a = 100), render(id, 200, 200, -200, 200, src, a = 200))
            val a = render(id, 100, 75, -80, 60, src, a = 70)
            assertArrayEquals(id, a, render(id, 100, 75, -80, 60, src, a = 70))
            assertFalse(id, a.contentEquals(render(id, 100, 75, -80, 60, src, 18L, a = 70)))
            for (i in src.indices) assertEquals(src[i] ushr 24, a[i] ushr 24)
            assertArrayEquals(before, src)
        }
    }

    @Test fun aberrationSeparatesANeutralEdgeButKeepsTheOpticalCentreAligned() {
        val w = 401
        val h = 81
        val src = IntArray(w * h) { i -> if (i % w < 100) 0xFF202020.toInt() else 0xFFE0E0E0.toInt() }
        val p = FilterParams("test", "Original", 1, sharpen = 0, saturation = 0f, aberrationRadius = 32f)
        fun output(a: Int) = CpuFilterRenderer(p.copy(aberration = a)).apply(src, w, h, 1f, 17L)
        val zero = output(0)
        assertArrayEquals(src, zero)
        val low = output(20)
        val high = output(100)
        fun coloured(pixels: IntArray) = pixels.count {
            val r = (it ushr 16) and 255; val g = (it ushr 8) and 255; val b = it and 255
            maxOf(r, g, b) - minOf(r, g, b) > 16
        }
        assertTrue(coloured(low) > 0)
        assertTrue(coloured(high) > coloured(low))
        assertEquals(zero[(h / 2) * w + w / 2], high[(h / 2) * w + w / 2])
        assertArrayEquals(src, IntArray(w * h) { i -> if (i % w < 100) 0xFF202020.toInt() else 0xFFE0E0E0.toInt() })
    }

    @Test fun transparentSamplesDoNotCreateDarkOrColouredRimsOnAConstantSurface() {
        val w = 401
        val h = 31
        val src = IntArray(w * h) { i -> if (i % w < 100) 0x00FF00FF else 0xFF5A5A5A.toInt() }
        val p = FilterParams("test", "Original", 1, sharpen = 0, saturation = 0f,
            aberration = 100, aberrationRadius = 32f)
        val out = CpuFilterRenderer(p).apply(src, w, h, 1f, 17L)
        for (i in src.indices) {
            assertEquals(src[i] ushr 24, out[i] ushr 24)
            if ((src[i] ushr 24) != 0) assertEquals(src[i], out[i])
        }
    }
}
