package com.aliahad.photoeffects.effect

/**
 * The YESTERDAY family (PREQUEL "yesterday" pack).
 *
 * PREQUEL ships a SINGLE preset for yesterday (no numbered variant strip), so this family is
 * exactly one card. The look is the Lead-fitted rewrite from the same-photo PREQUEL export
 * (`docs/prequel-research/device-packs/exports/yesterday_fullhd.jpg`); see
 * `docs/prequel-research/spec/yesterday-rewrite-spec.md`.
 *
 * RETUNING = EDITING THIS ROW ONLY.
 */
object YesterdayFamily {

    const val ID = "Yesterday"

    val presets: List<YesterdayParams> = listOf(
        YesterdayParams(
            id = "yesterday_1",
            displayName = "Yesterday",
            description = "Warm faded film with grain, strong vignette and the amber dot-matrix date stamp",
            seed = 301,
            defaultIntensity = 100,
        ),
    )

    /** PREQUEL-style cross-listing (YESTERDAY was found under TRENDS on the device). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "yesterday_1" to setOf(EffectCategory.Trends, EffectCategory.ForYou),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        YesterdayEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
