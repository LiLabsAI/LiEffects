package com.aliahad.photoeffects.filter

import org.junit.Assert.*
import org.junit.Test

class FilterCatalogueTest {
    private val valid = """
        schemaVersion=4
        presets=custom
        custom.family=Original
        custom.variant=1
        custom.summary=custom colour
    """.trimIndent()

    @Test fun aNewRecipeLoadsWithoutChangingTheEngine() {
        val recipe = FilterCatalogue.read((valid + "\ncustom.exposure=1.2\ncustom.sharpen=0").reader()).single()
        assertEquals("Original #1", recipe.displayName)
        assertEquals(1.2f, recipe.exposure, 0f)
        assertEquals(1f, recipe.contrast, 0f)
        val source = intArrayOf(0xFF808080.toInt())
        val output = CpuFilterRenderer(recipe).apply(source, 1, 1, 1f, 0L)
        assertEquals(0xFF9A9A9A.toInt(), output.single())
        assertEquals(1.2f, recipe.uniforms().getValue("uExposure"), 0f)
    }

    @Test fun luxuryRecipesExposeSingleVariantFamiliesWithOnlyColourStrengthAndBoundUniforms() {
        val expected = listOf("black_tie_1" to "Black Tie", "old_money_1" to "Old Money")
        assertTrue(FilterRegistry.presets.map { it.id }.containsAll(expected.map { it.first }))
        for ((id, family) in expected) {
            val p = FilterRegistry.paramsById(id)!!
            val filter = FilterRegistry.byId(id)!!
            assertEquals(family, p.family)
            assertEquals(1, p.variant)
            assertEquals(listOf(id), FilterRegistry.shelfById(family)!!.effects.map { it.id })
            assertEquals("$family #1", filter.displayName)
            assertEquals(p.summary, filter.summary)
            assertTrue(p.summary.isNotBlank())
            assertEquals(listOf(FilterControl.Strength), p.controls)
            assertEquals(1f, p.strength, 0f)
            assertEquals(0, p.sharpen)
            assertEquals(0, p.grain)
            assertEquals(0, p.highlights)
            assertEquals(0, p.shadows)
            assertEquals(0, p.aberration)
            assertEquals(0f, p.softFocus, 0f)
            assertEquals(16, filter.exportBytesPerPixel)
            val gpu = filter.gpu
            assertEquals(FILTER_AGSL, gpu.agslSource)
            assertTrue(gpu.internalPasses.isEmpty())
            assertNull(gpu.inputProgram)
            assertEquals(p.uniforms(), gpu.uniforms)
            val scalarUniforms = Regex("""uniform float (\w+);""").findAll(gpu.agslSource)
                .map { it.groupValues[1] }.toSet()
            // Frame intensity/resolution are renderer-owned; all recipe scalars must be bound.
            assertEquals(scalarUniforms - "uIntensity", gpu.uniforms.keys)
            assertEquals(0f, FilterRegistry.withControls(id, 0, 0)!!.gpu.uniforms.getValue("uFilterStrength"), 0f)
        }
    }

    @Test fun invalidCatalogueDataFailsWithAnActionableMessage() {
        val bad = listOf(
            valid.replace("schemaVersion=4", "schemaVersion=5"),
            valid.replace("presets=custom", "presets=custom,custom"),
            valid.replace("custom.family=Original", ""),
            valid.replace("custom.variant=1", "custom.variant=0"),
            valid + "\ncustom.exposure=NaN",
            valid + "\ncustom.gainR=Infinity",
            valid + "\ncustom.contrast=-1",
            valid + "\ncustom.saturaton=0.5",
            valid + "\ncustom.black=0.8\ncustom.white=0.2",
            valid + "\ncustom.sharpen=101",
            valid + "\ncustom.intensity=101",
            valid + "\ncustom.grain=-1",
            valid + "\ncustom.strength=1.1",
            valid + "\ncustom.grainSize=0",
            valid + "\ncustom.softFocus=97",
            valid + "\ncustom.focusSpread=-1",
            valid + "\ncustom.focusDetail=2.1",
            valid + "\ncustom.focusDetailRadius=Infinity",
            valid + "\ncustom.grainSeed=4091",
            valid + "\ncustom.highlights=-101",
            valid + "\ncustom.shadows=101",
            valid + "\ncustom.aberration=101",
            valid + "\ncustom.aberrationRadius=-1",
            valid + "\ncustom.controls=Filter,Filter",
            valid + "\ncustom.controls=Grain",
            valid + "\ncustom.controls=Filter,Unknown",
            valid + "\ncustom.sharpen=20.5",
            valid + "\ncustom.variant=2",
        )
        for (data in bad) {
            val error = runCatching { FilterCatalogue.read(data.reader()) }.exceptionOrNull()
            assertTrue("Accepted invalid catalogue: $data", error is IllegalArgumentException)
            assertFalse(error?.message.isNullOrBlank())
        }
    }

    @Test fun duplicateFamilyVariantsAreRejected() {
        val data = valid.replace("presets=custom", "presets=custom,other") + """

            other.family=Original
            other.variant=1
            other.summary=another recipe
        """.trimIndent()
        val error = runCatching { FilterCatalogue.read(data.reader()) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun sunstoneAndHalcyonBindMultiPassUniformsAndHaveUniqueGrainSeeds() {
        val addedIds = listOf("sunstone_1", "sunstone_2", "sunstone_3", "halcyon_1", "halcyon_2", "halcyon_3")
        val scalarPattern = Regex("""uniform float (\w+);""")
        for (id in addedIds) {
            val p = requireNotNull(FilterRegistry.paramsById(id)) { "Missing $id" }
            val effect = requireNotNull(FilterRegistry.byId(id)) { "Missing effect $id" }
            assertFalse("$id must not combine softFocus and aberration", p.softFocus > 0f && p.aberration > 0)
            assertTrue("$id summary should be non-blank", p.summary.isNotBlank())
            val gpu = effect.gpu
            assertEquals("$id uniforms map", p.uniforms(), gpu.uniforms)
            val declaredScalars = (listOf(gpu.agslSource) + gpu.internalPasses.map { it.agslSource })
                .flatMap { source -> scalarPattern.findAll(source).map { it.groupValues[1] }.toList() }
                .toSet()
            assertTrue(
                "$id undeclared scalar uniforms: ${declaredScalars - gpu.uniforms.keys - "uIntensity"}",
                (declaredScalars - "uIntensity").all { it in gpu.uniforms.keys },
            )
            assertEquals("$id grainSeed range", true, p.grainSeed in 1..4090)
        }
        val nonzeroSeeds = FilterRegistry.presets.map { it.grainSeed }.filter { it > 0 }
        assertEquals("Every seeded preset must have a unique grainSeed", nonzeroSeeds.size, nonzeroSeeds.distinct().size)
    }
}
