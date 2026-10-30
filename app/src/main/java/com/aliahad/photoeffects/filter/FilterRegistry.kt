package com.aliahad.photoeffects.filter

import com.aliahad.photoeffects.effect.EffectShelf
import com.aliahad.photoeffects.effect.EffectSpec
import com.aliahad.photoeffects.effect.GpuPass
import com.aliahad.photoeffects.effect.GpuProgram
import com.aliahad.photoeffects.effect.PhotoEffect

/** Independent Filters catalogue; existing Effects retain their IDs, shelves and recipes. */
object FilterRegistry {
    val presets: List<FilterParams> = FilterCatalogue.bundled()

    val all: List<PhotoEffect> = presets.map(::create)
    val shelves: List<EffectShelf> = presets.map { it.family }.distinct().map { family ->
        EffectShelf(family, family.uppercase(), all.filter { it.spec.familyId == family }, isFamily = true)
    }
    val defaultShelf: EffectShelf = shelves.first()
    fun shelfById(id: String): EffectShelf? = shelves.firstOrNull { it.id == id }
    fun byId(id: String): PhotoEffect? = all.firstOrNull { it.id == id }
    fun paramsById(id: String): FilterParams? = presets.firstOrNull { it.id == id }
    fun withControls(
        id: String, strength: Int, sharpen: Int, lookIntensity: Int? = null, grain: Int? = null,
        highlights: Int? = null, shadows: Int? = null,
        aberration: Int? = null,
    ): PhotoEffect? =
        paramsById(id)?.let { create(it.copy(
            strength = strength.coerceIn(0, 100) / 100f,
            sharpen = sharpen.coerceIn(0, 100),
            intensity = lookIntensity?.coerceIn(0, 100) ?: it.intensity,
            grain = grain?.coerceIn(0, 100) ?: it.grain,
            highlights = highlights?.coerceIn(-100, 100) ?: it.highlights,
            shadows = shadows?.coerceIn(-100, 100) ?: it.shadows,
            aberration = aberration?.coerceIn(0, 100) ?: it.aberration,
        )) }

    private fun create(p: FilterParams): PhotoEffect = PhotoEffect(
        spec = EffectSpec(p.id, p.displayName, p.family, "${p.family} colour grade, variant ${p.variant}"),
        cpu = CpuFilterRenderer(p),
        gpu = gpuProgram(p),
        summary = p.summary,
        // Directional CPU rendering retains a premultiplied floating-point intermediate; the
        // aberration path additionally keeps one graded ARGB frame for channel separation.
        exportBytesPerPixel = (if (p.softFocus > 0f) 32 else 16) + if (p.aberration > 0) 4 else 0,
    )

    private fun gpuProgram(p: FilterParams): GpuProgram {
        val uniforms = p.uniforms()
        return when {
            // Not used by the bundled catalogue, but keep the correct single-pass fallback.
            p.softFocus > 0f && p.aberration > 0 -> GpuProgram(FILTER_AGSL, uniforms)
            p.softFocus > 0f -> GpuProgram(
                agslSource = FILTER_FOCUS_FINISH_AGSL,
                uniforms = uniforms,
                internalPasses = listOf(GpuPass(FILTER_FOCUS_H_AGSL, uniforms)),
                baseShaderInputs = setOf("uSource"),
            )
            p.aberration > 0 -> GpuProgram(
                agslSource = FILTER_ABERRATION_AGSL,
                uniforms = uniforms,
                internalPasses = listOf(GpuPass(FILTER_GRADE_AGSL, uniforms)),
                baseShaderInputs = setOf("uOriginal"),
            )
            else -> GpuProgram(FILTER_AGSL, uniforms)
        }
    }
}
