package com.aliahad.photoeffects.effect

/** One immutable stage of the editor's filter → adjust → effect rendering pipeline. */
data class EditStage(val effect: PhotoEffect, val intensity: Int)

/**
 * Combines independent Filter / Adjust / Effect selections into the existing bitmap/export
 * contract. Both backends use precisely the same stage order and strengths. The extra pixel
 * allowance covers the intermediate ARGB buffers while the following CPU renderers run.
 */
object EditComposition {

    fun compose(filter: EditStage?, effect: EditStage?): PhotoEffect? =
        compose(filter, null, effect)

    fun compose(filter: EditStage?, adjust: EditStage?, effect: EditStage?): PhotoEffect? {
        val stages = listOfNotNull(filter, adjust, effect)
        if (stages.isEmpty()) return null
        var program: GpuProgram? = null
        for (stage in stages) {
            program = stage.effect.gpu.copy(
                inputProgram = program,
                intensityOverride = stage.intensity.coerceIn(0, 100) / 100f,
            )
        }
        val last = stages.last().effect
        val stampStage = stages.firstOrNull { it.effect.stamp != null }
        return PhotoEffect(
            spec = last.spec.copy(
                id = stages.joinToString("+") { it.effect.id },
                displayName = stages.joinToString(" · ") { it.effect.displayName },
            ),
            cpu = CpuEffect { input, width, height, intensity, seed ->
                val t = intensity.coerceIn(0f, 1f)
                if (t <= 0f) {
                    input.copyOf()
                } else {
                    var pixels = input
                    for (stage in stages) {
                        pixels = stage.effect.cpu.apply(
                            pixels, width, height, stage.intensity.coerceIn(0, 100) / 100f * t, seed,
                        )
                    }
                    pixels
                }
            },
            gpu = requireNotNull(program),
            summary = stages.joinToString(" · ") { it.effect.summary },
            stamp = stampStage?.effect?.stamp,
            stampIntensity = (stampStage?.intensity ?: 0).coerceIn(0, 100) / 100f,
            exportBytesPerPixel = stages.maxOf { it.effect.exportBytesPerPixel } +
                8 * (stages.size - 1),
        )
    }
}
