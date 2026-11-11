package com.aliahad.photoeffects.effect

/**
 * Three highlight-bloom presets with warm, cyan and blue-violet halo palettes.
 * Each row supplies the shared CPU/GPU highlight, blur and grain controls.
 */
object LumenFamily {

    const val ID = "Lumen"

    /** Fixed per-variant seeds; combined with the per-photo seed at render time. */
    val presets: List<LumenParams> = listOf(
        LumenParams(
            id = "lumen_1",
            displayName = "Lumen #1",
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
        LumenParams(
            id = "lumen_2",
            displayName = "Lumen #2",
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
        LumenParams(
            id = "lumen_3",
            displayName = "Lumen #3",
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

    /** photo-editor cross-listing (the family is always the primary shelf). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "lumen_1" to setOf(EffectCategory.ForYou),
        "lumen_2" to setOf(EffectCategory.New),
        "lumen_3" to setOf(EffectCategory.ForYou, EffectCategory.Films),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        LumenEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
