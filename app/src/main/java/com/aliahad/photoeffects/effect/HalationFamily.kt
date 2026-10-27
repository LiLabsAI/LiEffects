package com.aliahad.photoeffects.effect

/**
 * The HALATION family (PREQUEL's "HALATION" pack, 3 numbered variants).
 *
 * The parameter rows are the values measured on the live PREQUEL pack
 * (`docs/prequel-research/spec/halation-3-variants-spec.md` §3) and are pinned by
 * `DustyEffectPipelineTest.halationFamily_isWiredWithMeasuredRows`. Retuning = editing these
 * three rows. The differentiators across variants are the halo [HalationParams.hue]
 * (warm red-orange / cool cyan / cool blue-violet) and the fitted [HalationParams.haloGain].
 */
object HalationFamily {

    const val ID = "Halation"

    /** Fixed per-variant seeds; combined with the per-photo seed at render time. */
    val presets: List<HalationParams> = listOf(
        HalationParams(
            id = "halation_1",
            displayName = "Halation #1",
            description = "Warm red-orange film halation around highlights and edges",
            seed = 201,
            hue = 0.00f,
            brightThreshold = 0.82f,
            softKnee = 0.10f,
            opacity = 0.80f,
            haloGain = 7.0f,
            blur = 0.30f,
            haze = 0.03f,
            grain = 0.35f,
            warmth = 0.06f,
            tint = 0.02f,
            contrast = 0.02f,
            fade = 0.05f,
            saturation = 0.02f,
            vignette = 0.10f,
            defaultIntensity = 65,
        ),
        HalationParams(
            id = "halation_2",
            displayName = "Halation #2",
            description = "Cool cyan film glow around highlights and edges",
            seed = 202,
            hue = 0.50f,
            brightThreshold = 0.86f,
            softKnee = 0.12f,
            opacity = 0.80f,
            haloGain = 11.0f,
            blur = 0.30f,
            haze = 0.015f,
            grain = 0.35f,
            warmth = 0.02f,
            tint = 0.00f,
            contrast = 0.00f,
            fade = 0.04f,
            saturation = 0.00f,
            vignette = 0.08f,
            defaultIntensity = 60,
        ),
        HalationParams(
            id = "halation_3",
            displayName = "Halation #3",
            description = "Cool blue-violet film halation around highlights and edges",
            seed = 203,
            hue = 0.70f,
            brightThreshold = 0.80f,
            softKnee = 0.10f,
            opacity = 0.80f,
            haloGain = 12.0f,
            blur = 0.40f,
            haze = 0.03f,
            grain = 0.35f,
            warmth = -0.03f,
            tint = 0.02f,
            contrast = 0.02f,
            fade = 0.06f,
            saturation = -0.02f,
            vignette = 0.12f,
            defaultIntensity = 65,
        ),
    )

    /** PREQUEL-style cross-listing (the family is always the primary shelf). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "halation_1" to setOf(EffectCategory.ForYou),
        "halation_2" to setOf(EffectCategory.New),
        "halation_3" to setOf(EffectCategory.ForYou, EffectCategory.Films),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        HalationEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
