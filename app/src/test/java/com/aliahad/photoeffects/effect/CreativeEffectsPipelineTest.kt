package com.aliahad.photoeffects.effect

import com.aliahad.photoeffects.adjust.AdjustControl
import com.aliahad.photoeffects.adjust.AdjustEffectFactory
import com.aliahad.photoeffects.adjust.AdjustParams
import com.aliahad.photoeffects.canvas.CanvasParams
import com.aliahad.photoeffects.canvas.CanvasRatio
import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.effect.cpu.RenderCancellation
import com.aliahad.photoeffects.filter.FilterRegistry
import com.aliahad.photoeffects.photo.ExportBudget
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlanner
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

class CreativeEffectsPipelineTest {
    private val effects = SolsticeFamily.effects + ScreenprintFamily.effects
    private val width = 193
    private val height = 127
    private fun source() = IntArray(width * height) { i ->
        (intArrayOf(0, 32, 128, 192, 255)[i % 5] shl 24) or
            (((i * 17) % 256) shl 16) or (((i * 31) % 256) shl 8) or ((i * 7) % 256)
    }

    @Test fun allSixPresetsHaveStableDistinctNamesAndAreAvailableOnTheirShelves() {
        assertEquals(listOf("solstice_ember", "solstice_tidal", "solstice_rosette",
            "screenprint_carbon", "screenprint_duet", "screenprint_terracotta"), effects.map { it.id })
        assertEquals(listOf("Ember", "Tidal", "Rosette", "Carbon", "Duet", "Terracotta"), effects.map { it.displayName })
        assertEquals(SolsticeFamily.effects, EffectRegistry.shelfById("Solstice")!!.effects)
        assertEquals(ScreenprintFamily.effects, EffectRegistry.shelfById("Screenprint")!!.effects)
        assertEquals(35, EffectRegistry.all.map { it.id }.distinct().size)
        for (effect in effects) {
            assertEquals(effect, EffectRegistry.byId(effect.id))
            assertTrue(effect in EffectRegistry.forCategory(EffectCategory.New))
            assertTrue(effect.defaultIntensity in 1..100)
            assertEquals(16, effect.exportBytesPerPixel)
            assertTrue(effect.gpu.extraShaderAssets.isEmpty())
            assertTrue(effect.gpu.internalPasses.isEmpty())
        }
    }

    @Test fun zeroIsAnExactCopyStrengthIsClampedAndInputIsNeverMutated() {
        val src = source()
        val before = src.copyOf()
        for (effect in effects) {
            val zero = effect.cpu.apply(src, width, height, -1f, 42L)
            assertArrayEquals(src, zero)
            assertNotSame(src, zero)
            assertArrayEquals(effect.cpu.apply(src, width, height, 1f, 42L),
                effect.cpu.apply(src, width, height, 2f, 42L))
        }
        assertArrayEquals(before, src)
    }

    @Test fun partialCoverageAndHiddenTransparentRgbArePreservedAndStrengthBlendsTheFinalLook() {
        val src = source()
        for (effect in effects) {
            val full = effect.cpu.apply(src, width, height, 1f, 42L)
            val half = effect.cpu.apply(src, width, height, 0.5f, 42L)
            for (i in src.indices) {
                assertEquals(src[i] ushr 24, full[i] ushr 24)
                assertEquals(src[i] ushr 24, half[i] ushr 24)
                if (src[i] ushr 24 == 0) assertEquals(src[i], full[i])
                for (shift in intArrayOf(0, 8, 16)) {
                    val midpoint = (((src[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                    assertTrue(abs(midpoint - ((half[i] ushr shift) and 255)) <= 1f)
                }
            }
        }
    }

    @Test fun parallelRenderingIsRepeatableAndPhotoSeedsControlThePaperAndFilmTexture() {
        val src = IntArray(width * height) { 0xFF808080.toInt() }
        for (effect in effects) {
            val first = effect.cpu.apply(src, width, height, 1f, 42L)
            repeat(3) { assertArrayEquals(first, effect.cpu.apply(src, width, height, 1f, 42L)) }
            val other = effect.cpu.apply(src, width, height, 1f, 999L)
            assertTrue(effect.id, first.indices.count { first[it] != other[it] } > src.size / 10)
            for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) for (seed in listOf(Long.MIN_VALUE, Long.MAX_VALUE)) {
                val tiny = IntArray(w * h) { 0xFF603020.toInt() }
                assertArrayEquals(effect.cpu.apply(tiny, w, h, 1f, seed), effect.cpu.apply(tiny, w, h, 1f, seed))
            }
        }
    }

    @Test fun cancelledThumbnailAndExportWorkStopsBeforeProcessingRows() {
        val src = source()
        val before = src.copyOf()
        val previous = RenderCancellation.current()
        try {
            RenderCancellation.set { true }
            for (effect in effects) {
                assertThrows(CancellationException::class.java) { effect.cpu.apply(src, width, height, 1f, 42L) }
            }
        } finally { RenderCancellation.set(previous) }
        assertArrayEquals(before, src)
    }

    @Test fun solsticeLeaksEnterFromTheirAuthoredEdgesWithDistinctColours() {
        val src = IntArray(width * height) { 0xFF606060.toInt() }
        val outputs = SolsticeFamily.presets.map { SolsticeEffectFactory.create(it.copy(grain = 0f))
            .cpu.apply(src, width, height, 1f, 42L) }
        val ember = outputs[0][(height * 0.42f).toInt() * width]
        val tidal = outputs[1][width - 1]
        val rose = outputs[2][(height - 1) * width + (width * 0.62f).toInt()]
        assertTrue(PixelMath.red(ember) > PixelMath.blue(ember) + 0.25f)
        assertTrue(PixelMath.blue(tidal) > PixelMath.red(tidal) + 0.20f)
        assertTrue(PixelMath.red(rose) > PixelMath.green(rose) + 0.20f)
        assertTrue(luma(ember) > luma(outputs[0][width - 1]) + 0.10f)
        assertTrue(luma(tidal) > luma(outputs[1][(height - 1) * width]) + 0.10f)
        assertTrue(luma(rose) > luma(outputs[2][(width * 0.62f).toInt()]) + 0.10f)
    }

    @Test fun lightLeakFramingIsStableAtExportResolutionInPortraitLandscapeAndSquare() {
        for (p in SolsticeFamily.presets) for ((w, h) in listOf(61 to 91, 91 to 61, 61 to 61)) {
            val effect = SolsticeEffectFactory.create(p.copy(grain = 0f))
            val small = effect.cpu.apply(IntArray(w * h) { 0xFF808080.toInt() }, w, h, 1f, 42L)
            val large = effect.cpu.apply(IntArray(w * h * 9) { 0xFF808080.toInt() }, w * 3, h * 3, 1f, 42L)
            for (y in 0 until h) for (x in 0 until w) {
                assertTrue(p.id, PixelMath.absDiff(small[y * w + x], large[(y * 3 + 1) * w * 3 + x * 3 + 1]) <= 3)
            }
        }
    }

    @Test fun screenprintKeepsWhitePaperCleanAndSolidBlackUsesTheSelectedInk() {
        for (p in ScreenprintFamily.presets) {
            val effect = ScreenprintEffectFactory.create(p.copy(grain = 0f))
            val white = effect.cpu.apply(IntArray(width * height) { -1 }, width, height, 1f, 42L)
            assertTrue(white.all { it == PixelMath.pack(p.paperR, p.paperG, p.paperB, 255) })
            if (!p.dual) {
                val black = effect.cpu.apply(IntArray(width * height) { 0xFF000000.toInt() }, width, height, 1f, 42L)
                val ink = PixelMath.pack(p.paperR * p.inkR, p.paperG * p.inkG, p.paperB * p.inkB, 255)
                assertTrue(black.all { it == ink })
            }
        }
    }

    @Test fun halftoneDotsAreVisibleAndTheirAverageTonePreservesTheLightnessOrdering() {
        val w = 641
        val h = 97
        val variants = ScreenprintFamily.presets.map { ScreenprintEffectFactory.create(it.copy(grain = 0f)) }
        for (effect in variants) {
            val averages = (0..4).map { step ->
                val c = (step * 255f / 4).roundToInt()
                val src = IntArray(w * h) { 0xFF000000.toInt() or (c shl 16) or (c shl 8) or c }
                val out = effect.cpu.apply(src, w, h, 1f, 42L)
                if (step == 2) assertTrue(effect.id, out.maxOf { luma(it) } - out.minOf { luma(it) } > 0.20f)
                out.sumOf { luma(it).toDouble() } / out.size
            }
            averages.zipWithNext().forEach { (dark, light) -> assertTrue(effect.id, light > dark + 0.03) }
        }
        assertEquals(3, variants.map { it.cpu.apply(IntArray(w * h) { 0xFF808080.toInt() }, w, h, 1f, 42L).toList() }.distinct().size)
    }

    @Test fun printScreenCountStaysStableWhenTheSourceIsDecodedAtThreeTimesTheSize() {
        val p = ScreenprintFamily.presets.first().copy(angle = 0f, grain = 0f, pitch = 32f)
        val effect = ScreenprintEffectFactory.create(p)
        val w = 640
        val h = 96
        val small = effect.cpu.apply(IntArray(w * h) { 0xFF808080.toInt() }, w, h, 1f, 42L)
        val large = effect.cpu.apply(IntArray(w * h * 9) { 0xFF808080.toInt() }, w * 3, h * 3, 1f, 42L)
        val midpoint = (luma(PixelMath.pack(p.paperR, p.paperG, p.paperB, 255)) +
            luma(PixelMath.pack(p.paperR * p.inkR, p.paperG * p.inkG, p.paperB * p.inkB, 255))) / 2
        fun crossings(row: List<Int>) = row.map { luma(it) < midpoint }.zipWithNext().count { it.first != it.second }
        // Cross an off-centre slice of the dots; their centre row can be almost solid ink.
        val a = crossings((0 until w).map { small[9 * w + it] })
        val b = crossings((0 until w * 3).map { large[28 * w * 3 + it] })
        assertTrue("Expected repeated ink/paper crossings, got $a", a > 50)
        assertTrue("Screen count changed from $a to $b", abs(a - b) <= 1)
    }

    @Test fun allVariantsComposeAfterFilterAndAdjustAndBudgetTheFullSourceBeforeCanvas() {
        val src = source()
        val filter = FilterRegistry.byId("lucent_1")!!
        val adjust = AdjustEffectFactory.create(AdjustParams().with(AdjustControl.Exposure, 20))
        for (effect in effects) {
            val composed = EditComposition.compose(EditStage(filter, 70), EditStage(adjust, 100), EditStage(effect, 60))!!
            val expected = effect.cpu.apply(adjust.cpu.apply(filter.cpu.apply(src, width, height, 0.7f, 42L),
                width, height, 1f, 42L), width, height, 0.6f, 42L)
            assertArrayEquals(expected, composed.cpu.apply(src, width, height, 1f, 42L))
            val memory = 512L * 1024 * 1024
            val canvas = CanvasParams(CanvasRatio.SQUARE, zoom = 2.0, straighten = 13.0)
            val plan = ExportPlanner.plan(6000, 4000, ExportOptions(), composed, memory, canvas)
            assertTrue(plan.renderWidth.toLong() * plan.renderHeight <= ExportBudget.maxPixels(composed, memory, 12))
            assertEquals(canvas.geometry(6000, 4000).outputSize(plan.renderWidth, plan.renderHeight), plan.width to plan.height)
        }
    }

    private fun luma(pixel: Int) = PixelMath.luma(PixelMath.red(pixel), PixelMath.green(pixel), PixelMath.blue(pixel))
}
