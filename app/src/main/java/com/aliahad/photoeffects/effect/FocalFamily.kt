package com.aliahad.photoeffects.effect

/**
 * The FOCAL family (PREQUEL "Focal" pack, 6 numbered variants, found on the FOR YOU shelf).
 *
 * Every fitted constant comes from PREQUEL 2.5.0's own full-resolution exports of the SAME demo
 * photo (`docs/prequel-research/device-focal/exports/`): six hue-dependent 33^3 colour LUTs
 * packed into `assets/focal_luts.png`, plus the #1-only portrait-ellipse soft focus
 * (see [FocalParams]). Measured tool rows: Blur 100 / Filter 100 on all six.
 *
 * RETUNING = EDITING THESE SIX ROWS ONLY (the LUT atlas is data, regenerated from the exports).
 */
object FocalFamily {

    const val ID = "Focal"

    val presets: List<FocalParams> = listOf(
        FocalParams(
            id = "focal_1",
            displayName = "Focal #1",
            description = "Soft-focus portrait grade with a crisp centre and gently blurred edges",
            lutIndex = 0,
            ellipseBlur = true,
        ),
        FocalParams(
            id = "focal_2",
            displayName = "Focal #2",
            description = "Cool teal grade with deep shadows",
            lutIndex = 1,
        ),
        FocalParams(
            id = "focal_3",
            displayName = "Focal #3",
            description = "Clean neutral grade with a soft cool cast",
            lutIndex = 2,
        ),
        FocalParams(
            id = "focal_4",
            displayName = "Focal #4",
            description = "Warm neutral grade with creamy skin tones",
            lutIndex = 3,
        ),
        FocalParams(
            id = "focal_5",
            displayName = "Focal #5",
            description = "Bright sunlit grade with warm highlights",
            lutIndex = 4,
        ),
        FocalParams(
            id = "focal_6",
            displayName = "Focal #6",
            description = "Airy cyan-green grade with lifted midtones",
            lutIndex = 5,
        ),
    )

    /** PREQUEL listed the FOCAL pack on the FOR YOU shelf. */
    private val extraCategories: Map<String, Set<EffectCategory>> = presets.associate { params ->
        params.id to setOf(EffectCategory.ForYou)
    }

    val effects: List<PhotoEffect> = presets.map { params ->
        FocalEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
