package com.aliahad.photoeffects.effect

/**
 * The DUSTY family (PREQUEL's "dusty" pack, 5 numbered variants).
 *
 * RETUNING = EDITING THESE FIVE ROWS ONLY. The [DustyParams.grade] matrices are fitted from
 * PREQUEL same-photo exports (`docs/prequel-research/app/verification/variant-fidelity-report.md`);
 * all renderers are data-driven.
 */
object DustyFamily {

    const val ID = "Dusty"

    /** Fixed per-variant seeds (spec §2.1): combined with a per-photo seed at render time. */
    val presets: List<DustyParams> = listOf(
        DustyParams(
            id = "dusty_1",
            displayName = "Dusty #1",
            description = "Cinematic teal cast, soft focus, fine grain, light vignette",
            seed = 101,
            grade = listOf(
                1.697f, -0.867f, -0.057f, 0.049f,
                0.292f, 0.562f, -0.031f, 0.032f,
                -0.260f, 0.748f, 0.341f, 0.050f,
            ),
            aberration = 0.05f,
            blur = 0.08f,
            halation = 0.18f,
            grain = 0.30f,
            dust = 0.45f,
            dustWhite = true,
            dustSize = 1.0f,
            dustOpacity = 0.70f,
            scratches = 0.05f,
            texture = 0.08f,
            vignette = 0.15f,
        ),
        DustyParams(
            id = "dusty_2",
            displayName = "Dusty #2",
            description = "Deep blue cast, lifted shadows, heavy RGB fringing, dense grain",
            seed = 102,
            grade = listOf(
                1.219f, -0.746f, 0.349f, 0.082f,
                0.296f, 0.200f, 0.392f, 0.059f,
                -0.271f, 0.298f, 1.015f, 0.074f,
            ),
            aberration = 0.40f,
            blur = 0.10f,
            halation = 0.10f,
            grain = 0.40f,
            dust = 0.55f,
            dustWhite = true,
            dustSize = 0.95f,
            dustOpacity = 0.65f,
            scratches = 0.0f,
            texture = 0.05f,
            vignette = 0.20f,
        ),
        DustyParams(
            id = "dusty_3",
            displayName = "Dusty #3",
            description = "Green/olive film cast, cyan shadows, flecks and scratches",
            seed = 103,
            grade = listOf(
                1.461f, -0.660f, -0.022f, 0.024f,
                0.366f, 0.386f, 0.222f, 0.016f,
                0.515f, -0.651f, 0.988f, 0.002f,
            ),
            aberration = 0.10f,
            blur = 0.12f,
            halation = 0.12f,
            grain = 0.35f,
            dust = 0.50f,
            dustWhite = true,
            dustSize = 1.1f,
            dustOpacity = 0.68f,
            scratches = 0.15f,
            texture = 0.18f,
            vignette = 0.25f,
        ),
        DustyParams(
            id = "dusty_4",
            displayName = "Dusty #4",
            description = "Warm golden cast, halation bloom, faded matte, sparse flecks",
            seed = 104,
            grade = listOf(
                0.892f, -0.343f, 0.432f, 0.075f,
                0.001f, 0.610f, 0.263f, 0.082f,
                -0.131f, 0.334f, 0.573f, 0.102f,
            ),
            aberration = 0.10f,
            blur = 0.28f,
            halation = 0.60f,
            grain = 0.25f,
            dust = 0.35f,
            dustWhite = true,
            dustSize = 1.2f,
            dustOpacity = 0.75f,
            scratches = 0.10f,
            texture = 0.20f,
            vignette = 0.28f,
        ),
        DustyParams(
            id = "dusty_5",
            displayName = "Dusty #5",
            description = "Warm brown faded old-film grade, heavy grain, dust + scratches, strong vignette",
            seed = 105,
            grade = listOf(
                0.772f, -0.032f, 0.049f, 0.109f,
                0.221f, 0.279f, 0.233f, 0.085f,
                0.199f, -0.240f, 0.706f, 0.063f,
            ),
            aberration = 0.12f,
            blur = 0.15f,
            halation = 0.16f,
            grain = 0.80f,
            dust = 0.70f,
            dustWhite = true,
            dustSize = 0.75f,
            dustOpacity = 0.78f,
            scratches = 0.45f,
            texture = 0.40f,
            vignette = 0.52f,
        ),
    )

    /** PREQUEL-style cross-listed shelves (DUSTY is always the primary family shelf). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "dusty_1" to setOf(EffectCategory.ForYou),
        "dusty_2" to setOf(EffectCategory.ForYou, EffectCategory.Trends),
        "dusty_3" to setOf(EffectCategory.Trends, EffectCategory.Films),
        "dusty_4" to setOf(EffectCategory.ForYou, EffectCategory.New),
        "dusty_5" to setOf(EffectCategory.Films, EffectCategory.New),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        DustyEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
