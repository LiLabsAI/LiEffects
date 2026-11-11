package com.aliahad.photoeffects.effect

/**
 * A single warm film preset with a dot-matrix date stamp.
 * The single-card family has no numbered variant strip.
 */
object DaymarkFamily {

    const val ID = "Daymark"

    val presets: List<DaymarkParams> = listOf(
        DaymarkParams(
            id = "daymark_1",
            displayName = "Daymark",
            description = "Warm faded film with grain, strong vignette and the amber dot-matrix date stamp",
            seed = 301,
            defaultIntensity = 100,
        ),
    )

    /** photo-editor cross-listing (DAYMARK was found under TRENDS on the device). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "daymark_1" to setOf(EffectCategory.Trends, EffectCategory.ForYou),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        DaymarkEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
