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

class HalcyonPipelineTest {
    private val ids = (1..3).map { "halcyon_$it" }
    private val width = 127
    private val height = 85

    private fun source() = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val alpha = if (i % 9 == 0) 128 else 255
        (alpha shl 24) or
            ((36 + x * 180 / (width - 1)) shl 16) or
            ((36 + y * 180 / (height - 1)) shl 8) or
            (if ((x + y) % 2 == 0) 72 else 196)
    }

    @Test
    fun catalogueExposesThreeHalcyonVariantsWithSeparableFocusPassAndSeededGrain() {
        val shelf = requireNotNull(FilterRegistry.shelfById("Halcyon"))
        assertEquals(ids, shelf.effects.map { it.id })
        assertEquals((1..3).map { "Halcyon #$it" }, shelf.effects.map { it.displayName })

        val expectedControls = mapOf(
            "halcyon_1" to listOf(FilterControl.Strength, FilterControl.Intensity, FilterControl.Grain, FilterControl.Shadows),
            "halcyon_2" to listOf(FilterControl.Strength, FilterControl.Intensity, FilterControl.Grain, FilterControl.Highlights),
            "halcyon_3" to listOf(FilterControl.Strength, FilterControl.Intensity, FilterControl.Sharpen, FilterControl.Grain),
        )
        for (id in ids) {
            val p = requireNotNull(FilterRegistry.paramsById(id))
            val filter = requireNotNull(FilterRegistry.byId(id))
            assertEquals(expectedControls.getValue(id), p.controls)
            assertEquals(0.8f, p.strength, 0f)
            assertTrue(p.softFocus > 0f)
            assertTrue(p.focusSpread >= 3f)
            assertEquals(0, p.aberration)
            assertEquals(32, filter.exportBytesPerPixel)
            assertEquals(listOf(FILTER_FOCUS_H_AGSL), filter.gpu.internalPasses.map { it.agslSource })
            assertTrue("uSource" in filter.gpu.baseShaderInputs)
            if (p.sharpen > 0) {
                assertTrue("uPointSource" in filter.gpu.baseShaderInputs)
                assertTrue("uPointSource" in filter.gpu.nearestBaseShaderInputs)
            }
        }
    }

    @Test
    fun strengthIntensityGrainAndToneControlsAreIndependentAndAlphaSafe() {
        val src = source()
        val before = src.copyOf()
        for (id in ids) {
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
            ).cpu.apply(src, width, height, 1f, 31L)
            assertArrayEquals(id, src, zeroed)
            assertArrayEquals(id, src, requireNotNull(FilterRegistry.byId(id)).cpu.apply(src, width, height, 0f, 31L))

            val colorOnly = requireNotNull(
                FilterRegistry.withControls(id, 80, 0, lookIntensity = 0, grain = 0, highlights = 0, shadows = 0),
            ).cpu.apply(src, width, height, 1f, 31L)
            val bloomOnly = requireNotNull(
                FilterRegistry.withControls(id, 0, 0, lookIntensity = 80, grain = 0, highlights = 0, shadows = 0),
            ).cpu.apply(src, width, height, 1f, 31L)
            val grainOnly = requireNotNull(
                FilterRegistry.withControls(id, 0, 0, lookIntensity = 0, grain = 80, highlights = 0, shadows = 0),
            ).cpu.apply(src, width, height, 1f, 31L)

            assertFalse(id, src.contentEquals(colorOnly))
            assertFalse(id, src.contentEquals(bloomOnly))
            assertFalse(id, src.contentEquals(grainOnly))
            assertFalse(
                id,
                grainOnly.contentEquals(
                    requireNotNull(
                        FilterRegistry.withControls(id, 0, 0, lookIntensity = 0, grain = 80, highlights = 0, shadows = 0),
                    ).cpu.apply(src, width, height, 1f, 32L),
                ),
            )
            val full = requireNotNull(FilterRegistry.byId(id)).cpu.apply(src, width, height, 1f, 31L)
            for (i in src.indices) {
                assertEquals(src[i] ushr 24, full[i] ushr 24)
            }
            assertArrayEquals(before, src)
        }
    }

    @Test
    fun coolSapphireVioletAndGlacierPalettesAreDistinctFromWarmAndDarkGrades() {
        fun colorOnly(id: String, color: Int): Int =
            requireNotNull(
                FilterRegistry.withControls(
                    id = id,
                    strength = 100,
                    sharpen = 0,
                    lookIntensity = 0,
                    grain = 0,
                    highlights = 0,
                    shadows = 0,
                ),
            ).cpu.apply(intArrayOf(color), 1, 1, 1f, 71L).single()

        for (id in ids) {
            val mid = colorOnly(id, 0xFF808080.toInt())
            assertTrue("$id cool midtone blue > red", PixelMath.blue(mid) > PixelMath.red(mid) + 0.06f)
        }
        val mint = colorOnly("halcyon_3", 0xFF808080.toInt())
        val violet = colorOnly("halcyon_2", 0xFF808080.toInt())
        assertTrue("Glacier Mint should carry higher green luminance than Violet Twilight", PixelMath.green(mint) > PixelMath.green(violet) + 0.05f)
    }

    @Test
    fun ovalDiffusionSpreadsBothAxesWhilePreservingVisibleLocalContrast() {
        val w = 321
        val h = 129
        val src = IntArray(w * h) { 0xFF181818.toInt() }
        for (dy in -2..2) for (dx in -2..2) {
            src[(h / 2 + dy) * w + (w / 2 + dx)] = 0xFFE8E8E8.toInt()
        }
        val halcyonGlow = requireNotNull(
            FilterRegistry.withControls("halcyon_1", 0, 0, lookIntensity = 100, grain = 0, shadows = 0),
        ).cpu.apply(src, w, h, 1f, 1L)
        fun b(x: Int, y: Int) = halcyonGlow[y * w + x] and 255
        val center = b(w / 2, h / 2)
        val verticalHalo = b(w / 2, h / 2 + 1)
        val horizontalHalo = b(w / 2 + 4, h / 2)
        assertTrue("Vertical mist spread should illuminate adjacent rows ($verticalHalo)", verticalHalo > 28)
        assertTrue("Horizontal glow should illuminate nearby columns ($horizontalHalo)", horizontalHalo > 28)
        assertTrue("Local detail recovery keeps impulse center brighter than surrounding halo", center > verticalHalo && center > horizontalHalo)
    }

    @Test
    fun halcyonPreservesThirtyTwoByteBudgetInFullCompositionAndCanvasPlan() {
        val adjust = AdjustEffectFactory.create(AdjustParams().with(AdjustControl.Clarity, 25))
        val effect = requireNotNull(EffectRegistry.byId("nocturne_afterhours"))
        val canvas = CanvasParams(CanvasRatio.SQUARE, zoom = 1.5, quarterTurns = 1)
        val memory = 512L * 1024 * 1024
        for (id in ids) {
            val filter = requireNotNull(FilterRegistry.byId(id))
            val composed = requireNotNull(
                EditComposition.compose(EditStage(filter, 100), EditStage(adjust, 100), EditStage(effect, 80)),
            )
            assertEquals(52, composed.exportBytesPerPixel)
            val plan = ExportPlanner.plan(4800, 3200, ExportOptions(), composed, memory, canvas)
            assertTrue(plan.renderWidth.toLong() * plan.renderHeight <= ExportBudget.maxPixels(composed, memory, 12))
            assertTrue(abs(plan.width - plan.height) <= 1)
        }
    }
}
