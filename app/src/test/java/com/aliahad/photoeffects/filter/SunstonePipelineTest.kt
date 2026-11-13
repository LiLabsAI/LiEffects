package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.photo.ExportBudget
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlanner
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SunstonePipelineTest {
    private val ids = (1..3).map { "sunstone_$it" }
    private val width = 89
    private val height = 53

    private fun source() = IntArray(width * height) { i ->
        val alpha = if (i % 7 == 0) 128 else 255
        (alpha shl 24) or
            ((i % width * 255 / (width - 1)) shl 16) or
            ((i / width * 255 / (height - 1)) shl 8) or
            ((i * 19 + 37) % 256)
    }

    @Test
    fun catalogueExposesThreeDistinctSunstoneVariantsAndPassGraphs() {
        val shelf = requireNotNull(FilterRegistry.shelfById("Sunstone"))
        assertEquals(ids, shelf.effects.map { it.id })
        assertEquals((1..3).map { "Sunstone #$it" }, shelf.effects.map { it.displayName })

        val expectedControls = mapOf(
            "sunstone_1" to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Highlights, FilterControl.Shadows),
            "sunstone_2" to listOf(FilterControl.Strength, FilterControl.Grain, FilterControl.Highlights, FilterControl.Aberration),
            "sunstone_3" to listOf(FilterControl.Strength, FilterControl.Sharpen, FilterControl.Shadows, FilterControl.Grain),
        )
        for (id in ids) {
            val p = requireNotNull(FilterRegistry.paramsById(id))
            val filter = requireNotNull(FilterRegistry.byId(id))
            assertEquals(expectedControls.getValue(id), p.controls)
            assertEquals(0.85f, p.strength, 0f)
            assertEquals(0f, p.softFocus, 0f)
            assertEquals(if (id == "sunstone_2") 20 else 16, filter.exportBytesPerPixel)
            assertTrue(filter.summary.isNotBlank())
        }

        val prismGpu = requireNotNull(FilterRegistry.byId("sunstone_2")).gpu
        assertEquals(FILTER_ABERRATION_AGSL, prismGpu.agslSource)
        assertEquals(listOf(FILTER_GRADE_AGSL), prismGpu.internalPasses.map { it.agslSource })
        assertTrue("uOriginal" in prismGpu.baseShaderInputs)

        val copperGpu = requireNotNull(FilterRegistry.byId("sunstone_3")).gpu
        assertTrue("uPointSource" in copperGpu.baseShaderInputs)
        assertTrue("uPointSource" in copperGpu.nearestBaseShaderInputs)
    }

    @Test
    fun zeroControlsAndGlobalZeroRestoreTheSourceAndPreserveAlpha() {
        val src = source()
        val before = src.copyOf()
        for (id in ids) {
            val defaultFilter = requireNotNull(FilterRegistry.byId(id))
            assertArrayEquals(id, src, defaultFilter.cpu.apply(src, width, height, 0f, 13L))
            val zeroed = requireNotNull(
                FilterRegistry.withControls(
                    id = id,
                    strength = 0,
                    sharpen = 0,
                    lookIntensity = 0,
                    grain = 0,
                    highlights = 0,
                    shadows = 0,
                    aberration = 0,
                ),
            )
            assertArrayEquals(id, src, zeroed.cpu.apply(src, width, height, 1f, 13L))

            val rendered = defaultFilter.cpu.apply(src, width, height, 1f, 13L)
            assertArrayEquals(id, rendered, defaultFilter.cpu.apply(src, width, height, 1f, 13L))
            for (i in src.indices) {
                assertEquals(src[i] ushr 24, rendered[i] ushr 24)
            }
            assertArrayEquals(before, src)
        }
    }

    @Test
    fun warmSlidePalettesSeparateAcrossVariantsWithoutClippingNeutralShadows() {
        fun gradePixel(id: String, color: Int): Int =
            requireNotNull(
                FilterRegistry.withControls(
                    id = id,
                    strength = 100,
                    sharpen = 0,
                    grain = 0,
                    highlights = 0,
                    shadows = 0,
                    aberration = 0,
                ),
            ).cpu.apply(intArrayOf(color), 1, 1, 1f, 71L).single()

        for (id in ids) {
            val mid = gradePixel(id, 0xFF808080.toInt())
            assertTrue("$id warm midtone red > blue", PixelMath.red(mid) > PixelMath.blue(mid) + 0.08f)
            val deep = gradePixel(id, 0xFF282828.toInt())
            assertTrue("$id preserves deep shadow tone", (deep and 255) > 0)
        }

        val crispLuma = luma(gradePixel("sunstone_3", 0xFF505050.toInt()))
        val deepLuma = luma(gradePixel("sunstone_2", 0xFF505050.toInt()))
        assertTrue("Copper variant should lift shadows above terracotta variant", crispLuma > deepLuma + 0.05f)
    }

    @Test
    fun chromaticAberrationAndSharpeningBehaveAsAuthoredOnVariantTwoAndThree() {
        val w = 321
        val h = 65
        val stepEdge = IntArray(w * h) { if (it % w < w / 3) 0xFF282828.toInt() else 0xFFD8D8D8.toInt() }

        val noAberration = requireNotNull(
            FilterRegistry.withControls("sunstone_2", 100, 0, grain = 0, highlights = 0, aberration = 0),
        ).cpu.apply(stepEdge, w, h, 1f, 19L)
        val withAberration = requireNotNull(
            FilterRegistry.withControls("sunstone_2", 100, 0, grain = 0, highlights = 0, aberration = 80),
        ).cpu.apply(stepEdge, w, h, 1f, 19L)
        fun chromaCount(pixels: IntArray) = pixels.count { px ->
            val r = (px ushr 16) and 255
            val g = (px ushr 8) and 255
            val b = px and 255
            maxOf(r, g, b) - minOf(r, g, b) > 40
        }
        assertTrue(chromaCount(withAberration) > chromaCount(noAberration))

        val noSharpen = requireNotNull(
            FilterRegistry.withControls("sunstone_3", 100, 0, grain = 0, shadows = 0),
        ).cpu.apply(stepEdge, w, h, 1f, 19L)
        val withSharpen = requireNotNull(
            FilterRegistry.withControls("sunstone_3", 100, 100, grain = 0, shadows = 0),
        ).cpu.apply(stepEdge, w, h, 1f, 19L)
        val left = (h / 2) * w + (w / 3) - 1
        val right = left + 1
        assertTrue(luma(withSharpen[left]) < luma(noSharpen[left]))
        assertTrue(luma(withSharpen[right]) > luma(noSharpen[right]))
    }

    @Test
    fun compositionAndCanvasExportBudgetRespectSunstoneMemoryFootprint() {
        val adjust = AdjustEffectFactory.create(AdjustParams().with(AdjustControl.Exposure, 15))
        val effect = requireNotNull(EffectRegistry.byId("solstice_ember"))
        val canvas = CanvasParams(CanvasRatio.POST_4_5, straighten = 7.5)
        val memory = 512L * 1024 * 1024
        for (id in ids) {
            val filter = requireNotNull(FilterRegistry.byId(id))
            val composed = requireNotNull(
                EditComposition.compose(EditStage(filter, 100), EditStage(adjust, 100), EditStage(effect, 75)),
            )
            assertEquals(40, composed.exportBytesPerPixel)
            val plan = ExportPlanner.plan(6000, 4000, ExportOptions(), composed, memory, canvas)
            assertTrue(plan.renderWidth.toLong() * plan.renderHeight <= ExportBudget.maxPixels(composed, memory, 12))
            assertTrue(abs(plan.width - plan.renderWidth * 4 / 5) <= 2 || plan.height > 0)
        }
    }

    private fun luma(pixel: Int): Float =
        PixelMath.luma(PixelMath.red(pixel), PixelMath.green(pixel), PixelMath.blue(pixel))
}
