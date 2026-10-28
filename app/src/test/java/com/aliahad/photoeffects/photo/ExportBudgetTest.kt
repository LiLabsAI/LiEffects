package com.aliahad.photoeffects.photo

import com.aliahad.photoeffects.effect.EffectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportBudgetTest {

    @Test
    fun budgets_fit_the_heap_fraction_and_stay_bounded() {
        val heap = 512L * 1024 * 1024
        for (effect in EffectRegistry.all) {
            val pixels = ExportBudget.maxPixels(effect, heap)
            assertTrue("${effect.id}: $pixels below minimum", pixels >= ExportBudget.MIN_PIXELS)
            assertTrue("${effect.id}: $pixels above maximum", pixels <= ExportBudget.MAX_PIXELS)
            val needed = pixels.toLong() * effect.exportBytesPerPixel
            assertTrue(
                "${effect.id}: $needed bytes exceeds the heap fraction",
                needed <= (heap * 0.40).toLong() || pixels == ExportBudget.MAX_PIXELS,
            )
        }
    }

    @Test
    fun light_effects_export_larger_than_heavy_ones() {
        val heap = 512L * 1024 * 1024
        val focal = ExportBudget.maxPixels(EffectRegistry.byId("focal_1"), heap)
        val halation = ExportBudget.maxPixels(EffectRegistry.byId("halation_1"), heap)
        val sensual = ExportBudget.maxPixels(EffectRegistry.byId("sensual_1"), heap)
        assertTrue("focal ($focal) must allow a 12 MP photo", focal >= 12_000_000)
        assertTrue("focal ($focal) > halation ($halation)", focal > halation)
        assertTrue("halation ($halation) > sensual ($sensual)", halation > sensual)
    }

    @Test
    fun heavy_effect_export_beats_the_old_1440_cap() {
        val pixels = ExportBudget.maxPixels(EffectRegistry.byId("halation_1"), 512L * 1024 * 1024)
        val (w, h) = ExportBudget.targetSize(4000, 3000, pixels)
        assertTrue("12 MP export would still be $w x $h", w > 1440 && h > 1080)
    }

    @Test
    fun target_size_preserves_aspect_and_never_upscales() {
        assertEquals(4000 to 3000, ExportBudget.targetSize(4000, 3000, 24_000_000))
        val (w, h) = ExportBudget.targetSize(4000, 3000, 6_000_000)
        assertTrue("$w x $h exceeds 6 MP", w.toLong() * h <= 6_000_000)
        assertEquals(4000f / 3000f, w.toFloat() / h, 0.01f)
    }

    @Test
    fun tiny_heap_falls_back_to_the_previous_export_resolution() {
        val pixels = ExportBudget.maxPixels(EffectRegistry.byId("sensual_1"), 128L * 1024 * 1024)
        assertEquals(ExportBudget.MIN_PIXELS, pixels)
    }

    @Test
    fun no_effect_export_uses_a_larger_budget_than_any_effect() {
        val heap = 512L * 1024 * 1024
        val plain = ExportBudget.maxPixels(null, heap)
        for (effect in EffectRegistry.all) {
            assertTrue(
                "${effect.id} must not exceed the plain export budget",
                ExportBudget.maxPixels(effect, heap) <= plain,
            )
        }
    }
}
