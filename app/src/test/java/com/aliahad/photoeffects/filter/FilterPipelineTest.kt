package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.EditComposition
import com.aliahad.photoeffects.effect.EditStage
import com.aliahad.photoeffects.effect.EffectRegistry
import com.aliahad.photoeffects.effect.cpu.PixelMath
import com.aliahad.photoeffects.photo.ExportBudget
import com.aliahad.photoeffects.photo.ExportOptions
import com.aliahad.photoeffects.photo.ExportPlanner
import org.junit.Assert.*
import org.junit.Test

class FilterPipelineTest {
    private val width = 48
    private val height = 32
    private fun source() = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        ((if (i % 7 == 0) 128 else 255) shl 24) or
            ((x * 255 / (width - 1)) shl 16) or
            ((y * 255 / (height - 1)) shl 8) or ((x * 13 + y * 7) % 256)
    }

    @Test fun catalogueContainsEveryRequestedVariant() {
        assertEquals(listOf("G7X", "Dim", "Vanilla", "Insomnia", "Cine Film", "Chicago", "Teal Tone", "Burgundy"), FilterRegistry.shelves.map { it.id })
        assertEquals(32, FilterRegistry.all.size)
        assertEquals(32, FilterRegistry.all.map { it.id }.toSet().size)
        for (shelf in FilterRegistry.shelves) {
            val count = when (shelf.id) { "Insomnia" -> 6; "Chicago" -> 8; else -> 3 }
            assertEquals(count, shelf.effects.size)
            assertEquals((1..count).map { "${shelf.id} #$it" }, shelf.effects.map { it.displayName })
            for (effect in shelf.effects) {
                assertEquals(shelf.id, effect.spec.familyId)
                assertEquals(100, effect.defaultIntensity)
                assertTrue(effect.exportBytesPerPixel > 0)
            }
        }
        assertEquals(25, EffectRegistry.all.size)
    }

    @Test fun everyVariantPreservesIdentityAtZeroAndClampsStrength() {
        val src = source()
        for (filter in FilterRegistry.all) {
            assertArrayEquals(filter.id, src, filter.cpu.apply(src, width, height, 0f, 7L))
            assertArrayEquals(filter.id, src, filter.cpu.apply(src, width, height, -1f, 7L))
            assertArrayEquals(filter.id,
                filter.cpu.apply(src, width, height, 1f, 7L),
                filter.cpu.apply(src, width, height, 2f, 7L))
        }
    }

    @Test fun everyVariantIsDeterministicPreservesAlphaAndLeavesInputUntouched() {
        val src = source()
        val before = src.copyOf()
        for (filter in FilterRegistry.all) {
            val a = filter.cpu.apply(src, width, height, 1f, 9L)
            assertArrayEquals(filter.id, a, filter.cpu.apply(src, width, height, 1f, 9L))
            for (i in a.indices) assertEquals(src[i] ushr 24, a[i] ushr 24)
            assertArrayEquals(before, src)
        }
    }

    @Test fun allRecipesProduceDifferentPixels() {
        val src = source()
        val outputs = FilterRegistry.all.map { it.cpu.apply(src, width, height, 1f, 0L) }
        for (i in outputs.indices) for (j in i + 1 until outputs.size) {
            val meanDifference = outputs[i].indices.sumOf { PixelMath.absDiff(outputs[i][it], outputs[j][it]).toLong() }
                .toDouble() / (src.size * 3)
            assertTrue("${FilterRegistry.all[i].id}/${FilterRegistry.all[j].id}: $meanDifference", meanDifference > 1.0)
        }
    }

    @Test fun dimDarkensAndVanillaLiftsAMiddleGray() {
        val gray = intArrayOf(0xFF808080.toInt())
        fun brightness(id: String): Float {
            val p = requireNotNull(FilterRegistry.byId(id)).cpu.apply(gray, 1, 1, 1f, 0L)[0]
            return PixelMath.luma(PixelMath.red(p), PixelMath.green(p), PixelMath.blue(p))
        }
        for (v in 1..3) {
            assertTrue(brightness("dim_$v") < 0.47f)
            assertTrue(brightness("vanilla_$v") > 0.51f)
        }
    }

    @Test fun sharpenChangesEdgesWithoutChangingFlatColour() {
        val base = FilterRegistry.paramsById("g7x_1")!!
        val soft = CpuFilterRenderer(base.copy(sharpen = 0))
        val sharp = CpuFilterRenderer(base.copy(sharpen = 100))
        val flat = IntArray(width * height) { 0xFF808080.toInt() }
        assertArrayEquals(soft.apply(flat, width, height, 1f, 0L), sharp.apply(flat, width, height, 1f, 0L))
        val edges = IntArray(width * height) { if (it % width < width / 2) 0xFF606060.toInt() else 0xFFA0A0A0.toInt() }
        val a = soft.apply(edges, width, height, 1f, 0L)
        val b = sharp.apply(edges, width, height, 1f, 0L)
        val left = height / 2 * width + width / 2 - 1
        val right = left + 1
        assertTrue(PixelMath.red(b[left]) < PixelMath.red(a[left]))
        assertTrue(PixelMath.red(b[right]) > PixelMath.red(a[right]))
    }

    @Test fun compositionAppliesFilterBeforeEffectAtIndependentStrengths() {
        val src = source()
        val before = src.copyOf()
        val filter = FilterRegistry.byId("vanilla_2")!!
        val effect = EffectRegistry.byId("dusty_2")!!
        val edit = EditComposition.compose(EditStage(filter, 37), EditStage(effect, 65))!!
        val filtered = filter.cpu.apply(src, width, height, 0.37f, 42L)
        val expected = effect.cpu.apply(filtered, width, height, 0.65f, 42L)
        assertArrayEquals(expected, edit.cpu.apply(src, width, height, 1f, 42L))
        assertArrayEquals(src, edit.cpu.apply(src, width, height, 0f, 42L))
        assertArrayEquals(before, src)
        val reversed = filter.cpu.apply(effect.cpu.apply(src, width, height, 0.65f, 42L), width, height, 0.37f, 42L)
        assertFalse("test must distinguish render order", expected.contentEquals(reversed))
        assertEquals(effect.gpu.agslSource, edit.gpu.agslSource)
        assertEquals(0.65f, edit.gpu.intensityOverride!!, 0.00001f)
        assertEquals(filter.gpu.agslSource, edit.gpu.inputProgram!!.agslSource)
        assertEquals(0.37f, edit.gpu.inputProgram!!.intensityOverride!!, 0.00001f)
    }

    @Test fun filterAndSharpenControlsAreIndependent() {
        val src = source()
        val off = FilterRegistry.withControls("g7x_1", 0, 0)!!
        assertArrayEquals(src, off.cpu.apply(src, width, height, 1f, 0L))
        val sharpenOnly = FilterRegistry.withControls("g7x_1", 0, 80)!!
        val sharpened = sharpenOnly.cpu.apply(src, width, height, 1f, 0L)
        assertFalse(src.contentEquals(sharpened))
        val graded = FilterRegistry.withControls("g7x_1", 100, 0)!!.cpu.apply(src, width, height, 1f, 0L)
        val half = FilterRegistry.withControls("g7x_1", 50, 0)!!.cpu.apply(src, width, height, 1f, 0L)
        for (i in src.indices) {
            assertEquals((PixelMath.red(src[i]) + PixelMath.red(graded[i])) / 2f, PixelMath.red(half[i]), 1f / 255f)
        }
    }

    @Test fun zeroStrengthStageLeavesTheOtherStageIntact() {
        val src = source()
        val filter = FilterRegistry.byId("g7x_3")!!
        val effect = EffectRegistry.byId("halation_1")!!
        val effectOnly = EditComposition.compose(EditStage(filter, 0), EditStage(effect, 65))!!
        assertArrayEquals(effect.cpu.apply(src, width, height, 0.65f, 7L), effectOnly.cpu.apply(src, width, height, 1f, 7L))
        val filterOnly = EditComposition.compose(EditStage(filter, 50), EditStage(effect, 0))!!
        assertArrayEquals(filter.cpu.apply(src, width, height, 0.5f, 7L), filterOnly.cpu.apply(src, width, height, 1f, 7L))
        assertNull(EditComposition.compose(null, null))
    }

    @Test fun combinedExportAccountsForIntermediatePixelsAndPreservesSourceLimit() {
        val filter = FilterRegistry.byId("g7x_1")!!
        val effect = EffectRegistry.byId("sensual_1")!!
        val edit = EditComposition.compose(EditStage(filter, 100), EditStage(effect, 100))!!
        assertTrue(edit.exportBytesPerPixel >= effect.exportBytesPerPixel + 8)
        val heap = 512L * 1024 * 1024
        assertTrue(ExportBudget.maxPixels(edit, heap) < ExportBudget.maxPixels(effect, heap))
        val plan = ExportPlanner.plan(4000, 3000, ExportOptions(), edit, heap)
        assertTrue(plan.width <= 4000 && plan.height <= 3000)
        assertTrue(plan.width.toLong() * plan.height <= ExportBudget.maxPixels(edit, heap) * 1.001)
    }

    @Test fun compositionPreservesDateOverlayAtTheEffectStrength() {
        val filter = FilterRegistry.byId("dim_1")!!
        val effect = EffectRegistry.byId("yesterday_1")!!
        val edit = EditComposition.compose(EditStage(filter, 100), EditStage(effect, 40))!!
        assertNotNull(edit.stamp)
        assertEquals(0.4f, edit.stampIntensity, 0f)
        assertNull(EditComposition.compose(EditStage(filter, 100), null)!!.stamp)
    }

    @Test fun narrowAndSinglePixelInputsAreSupported() {
        for ((w, h) in listOf(1 to 1, 1 to 17, 17 to 1)) {
            val src = IntArray(w * h) { 0xFF8090A0.toInt() }
            for (filter in FilterRegistry.all) assertEquals(src.size, filter.cpu.apply(src, w, h, 1f, 0L).size)
        }
    }
}
