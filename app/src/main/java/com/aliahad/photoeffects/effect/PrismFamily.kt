package com.aliahad.photoeffects.effect

/**
 * Six colour-lookup grades using the shared 33³ LUT atlas in assets/prism_luts.png.
 * Only the first variant adds portrait-ellipse soft focus. Retune these rows together
 * with the atlas data; both renderers consume the same variant indices.
 */
object PrismFamily {

    const val ID = "Prism"

    val presets: List<PrismParams> = listOf(
        PrismParams(
            id = "prism_1",
            displayName = "Prism #1",
            description = "Soft-focus portrait grade with a crisp centre and gently blurred edges",
            lutIndex = 0,
            ellipseBlur = true,
        ),
        PrismParams(
            id = "prism_2",
            displayName = "Prism #2",
            description = "Cool teal grade with deep shadows",
            lutIndex = 1,
        ),
        PrismParams(
            id = "prism_3",
            displayName = "Prism #3",
            description = "Clean neutral grade with a soft cool cast",
            lutIndex = 2,
        ),
        PrismParams(
            id = "prism_4",
            displayName = "Prism #4",
            description = "Warm neutral grade with creamy skin tones",
            lutIndex = 3,
        ),
        PrismParams(
            id = "prism_5",
            displayName = "Prism #5",
            description = "Bright sunlit grade with warm highlights",
            lutIndex = 4,
        ),
        PrismParams(
            id = "prism_6",
            displayName = "Prism #6",
            description = "Airy cyan-green grade with lifted midtones",
            lutIndex = 5,
        ),
    )

    /** Cross-list the colour-lookup presets on the For You shelf. */
    private val extraCategories: Map<String, Set<EffectCategory>> = presets.associate { params ->
        params.id to setOf(EffectCategory.ForYou)
    }

    val effects: List<PhotoEffect> = presets.map { params ->
        PrismEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
