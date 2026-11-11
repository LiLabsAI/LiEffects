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
    private val luxuryIds = listOf("black_tie_1", "old_money_1")
    private fun luma(pixel: Int) = PixelMath.luma(PixelMath.red(pixel), PixelMath.green(pixel), PixelMath.blue(pixel))
    private fun source() = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        ((if (i % 7 == 0) 128 else 255) shl 24) or
            ((x * 255 / (width - 1)) shl 16) or
            ((y * 255 / (height - 1)) shl 8) or ((x * 13 + y * 7) % 256)
    }

    @Test fun catalogueContainsEveryRequestedVariant() {
        assertEquals(listOf("Lucent", "Nightfall", "Marzipan", "Driftglass", "Lantern", "Graphite", "Estuary", "Mulberry", "Black Tie", "Old Money"), FilterRegistry.shelves.map { it.id })
        assertEquals(34, FilterRegistry.all.size)
        assertEquals(34, FilterRegistry.all.map { it.id }.toSet().size)
        for (shelf in FilterRegistry.shelves) {
            val count = when (shelf.id) {
                "Driftglass" -> 6
                "Graphite" -> 8
                "Black Tie", "Old Money" -> 1
                else -> 3
            }
            assertEquals(count, shelf.effects.size)
            assertEquals((1..count).map { "${shelf.id} #$it" }, shelf.effects.map { it.displayName })
            for (effect in shelf.effects) {
                assertEquals(shelf.id, effect.spec.familyId)
                assertEquals(100, effect.defaultIntensity)
                assertTrue(effect.exportBytesPerPixel > 0)
            }
        }
        assertEquals(35, EffectRegistry.all.size)
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

    @Test fun nightfallDarkensAndMarzipanLiftsAMiddleGray() {
        val gray = intArrayOf(0xFF808080.toInt())
        fun brightness(id: String): Float {
            val p = requireNotNull(FilterRegistry.byId(id)).cpu.apply(gray, 1, 1, 1f, 0L)[0]
            return PixelMath.luma(PixelMath.red(p), PixelMath.green(p), PixelMath.blue(p))
        }
        for (v in 1..3) {
            assertTrue(brightness("nightfall_$v") < 0.47f)
            assertTrue(brightness("marzipan_$v") > 0.51f)
        }
    }

    @Test fun luxuryFilterStrengthBlendsDeterministicallyWithoutMutatingAnyAlphaLevel() {
        val src = source().mapIndexed { i, pixel -> (pixel and 0xFFFFFF) or ((i % 256) shl 24) }.toIntArray()
        val before = src.copyOf()
        for (id in luxuryIds) {
            fun render(strength: Int) = FilterRegistry.withControls(id, strength, 0)!!
                .cpu.apply(src, width, height, 1f, 71L)
            assertArrayEquals(id, src, render(0))
            val full = render(100)
            val half = render(50)
            assertArrayEquals(id, full, render(100))
            for (i in src.indices) {
                assertEquals(id, src[i] ushr 24, full[i] ushr 24)
                assertEquals(id, src[i] ushr 24, half[i] ushr 24)
                for (shift in intArrayOf(0, 8, 16)) {
                    val expected = (((src[i] ushr shift) and 255) + ((full[i] ushr shift) and 255)) / 2f
                    assertEquals(id, expected, ((half[i] ushr shift) and 255).toFloat(), 1f)
                }
            }
            assertArrayEquals(id, before, src)
        }
    }

    @Test fun luxuryGradesRetainShadowStepsAndHighlightHeadroom() {
        val ramp = IntArray(256) { 0xFF000000.toInt() or (it shl 16) or (it shl 8) or it }
        for (id in luxuryIds) {
            val output = FilterRegistry.byId(id)!!.cpu.apply(ramp, ramp.size, 1, 1f, 71L)
            for (shift in intArrayOf(0, 8, 16)) {
                for (i in 1 until output.size) {
                    assertTrue("$id channel $shift reverses at $i",
                        ((output[i] ushr shift) and 255) >= ((output[i - 1] ushr shift) and 255))
                }
                assertTrue("$id clips neutral shadows", ((output.first() ushr shift) and 255) > 0)
                assertTrue("$id clips highlights", ((output.last() ushr shift) and 255) < 255)
            }
            // Four-code-value steps must remain visible, including the deepest shadows.
            for (i in 4..64 step 4) {
                assertTrue("$id crushes shadow step $i", luma(output[i]) - luma(output[i - 4]) >= 2f / 255f)
            }
            assertTrue("$id lifts black too far", luma(output.first()) < 0.03f)
            assertTrue("$id should darken midtones", luma(output[128]) < 0.48f)
        }
    }

    @Test fun luxuryPalettesSeparateCoolSteelFromWarmBronzeWithoutLosingNaturalColour() {
        fun pixel(id: String, color: Int) = FilterRegistry.byId(id)!!.cpu
            .apply(intArrayOf(color), 1, 1, 1f, 71L).single()
        for (level in listOf(32, 64, 128, 224)) {
            val gray = 0xFF000000.toInt() or (level shl 16) or (level shl 8) or level
            val cool = pixel("black_tie_1", gray)
            assertTrue(PixelMath.blue(cool) > PixelMath.green(cool))
            assertTrue(PixelMath.green(cool) > PixelMath.red(cool))
            val warm = pixel("old_money_1", gray)
            assertTrue(PixelMath.red(warm) > PixelMath.green(warm))
            assertTrue(PixelMath.green(warm) > PixelMath.blue(warm))
        }
        for (id in luxuryIds) {
            for (skin in intArrayOf(0xFF6F4834.toInt(), 0xFFB4785A.toInt(), 0xFFE0B49C.toInt())) {
                val graded = pixel(id, skin)
                assertTrue("$id skin", PixelMath.red(graded) > PixelMath.green(graded))
                assertTrue("$id skin", PixelMath.green(graded) > PixelMath.blue(graded))
                if (id == "old_money_1") {
                    fun warmHue(c: Int) = (PixelMath.red(c) - PixelMath.green(c)) / (PixelMath.red(c) - PixelMath.blue(c))
                    assertEquals("$id natural skin hue", warmHue(skin), warmHue(graded), 0.15f)
                }
            }
            val foliage = pixel(id, 0xFF20C060.toInt())
            assertTrue("$id foliage", PixelMath.green(foliage) > PixelMath.red(foliage))
            assertTrue("$id foliage", PixelMath.green(foliage) > PixelMath.blue(foliage))
            val chroma = PixelMath.green(foliage) - minOf(PixelMath.red(foliage), PixelMath.blue(foliage))
            assertTrue("$id should mute foliage, not remove its colour", chroma in 0.20f..0.50f)
        }
    }

    @Test fun luxuryGradesArePointwiseAcrossPositionsDimensionsAndSeeds() {
        val src = source()
        for (id in luxuryIds) {
            val cpu = FilterRegistry.byId(id)!!.cpu
            val expected = IntArray(src.size) { cpu.apply(intArrayOf(src[it]), 1, 1, 1f, 0L).single() }
            assertArrayEquals(id, expected, cpu.apply(src, width, height, 1f, 71L))
            assertArrayEquals(id, expected, cpu.apply(src, height, width, 1f, Long.MAX_VALUE))
            assertArrayEquals(id, expected.reversedArray(), cpu.apply(src.reversedArray(), width, height, 1f, Long.MIN_VALUE))
        }
    }

    @Test fun sharpenChangesEdgesWithoutChangingFlatColour() {
        val base = FilterRegistry.paramsById("lucent_1")!!
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
        val filter = FilterRegistry.byId("marzipan_2")!!
        val effect = EffectRegistry.byId("motes_2")!!
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
        val off = FilterRegistry.withControls("lucent_1", 0, 0)!!
        assertArrayEquals(src, off.cpu.apply(src, width, height, 1f, 0L))
        val sharpenOnly = FilterRegistry.withControls("lucent_1", 0, 80)!!
        val sharpened = sharpenOnly.cpu.apply(src, width, height, 1f, 0L)
        assertFalse(src.contentEquals(sharpened))
        val graded = FilterRegistry.withControls("lucent_1", 100, 0)!!.cpu.apply(src, width, height, 1f, 0L)
        val half = FilterRegistry.withControls("lucent_1", 50, 0)!!.cpu.apply(src, width, height, 1f, 0L)
        for (i in src.indices) {
            assertEquals((PixelMath.red(src[i]) + PixelMath.red(graded[i])) / 2f, PixelMath.red(half[i]), 1f / 255f)
        }
    }

    @Test fun zeroStrengthStageLeavesTheOtherStageIntact() {
        val src = source()
        val filter = FilterRegistry.byId("lucent_3")!!
        val effect = EffectRegistry.byId("lumen_1")!!
        val effectOnly = EditComposition.compose(EditStage(filter, 0), EditStage(effect, 65))!!
        assertArrayEquals(effect.cpu.apply(src, width, height, 0.65f, 7L), effectOnly.cpu.apply(src, width, height, 1f, 7L))
        val filterOnly = EditComposition.compose(EditStage(filter, 50), EditStage(effect, 0))!!
        assertArrayEquals(filter.cpu.apply(src, width, height, 0.5f, 7L), filterOnly.cpu.apply(src, width, height, 1f, 7L))
        assertNull(EditComposition.compose(null, null))
    }

    @Test fun combinedExportAccountsForIntermediatePixelsAndPreservesSourceLimit() {
        val filter = FilterRegistry.byId("lucent_1")!!
        val effect = EffectRegistry.byId("velour_1")!!
        val edit = EditComposition.compose(EditStage(filter, 100), EditStage(effect, 100))!!
        assertTrue(edit.exportBytesPerPixel >= effect.exportBytesPerPixel + 8)
        val heap = 512L * 1024 * 1024
        assertTrue(ExportBudget.maxPixels(edit, heap) < ExportBudget.maxPixels(effect, heap))
        val plan = ExportPlanner.plan(4000, 3000, ExportOptions(), edit, heap)
        assertTrue(plan.width <= 4000 && plan.height <= 3000)
        assertTrue(plan.width.toLong() * plan.height <= ExportBudget.maxPixels(edit, heap) * 1.001)
    }

    @Test fun compositionPreservesDateOverlayAtTheEffectStrength() {
        val filter = FilterRegistry.byId("nightfall_1")!!
        val effect = EffectRegistry.byId("daymark_1")!!
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
