package com.aliahad.photoeffects.effect

object NocturneFamily {
    const val ID = "Nocturne"

    val presets: List<NocturneParams> = listOf(
        NocturneParams(
            id = "nocturne_gilded",
            displayName = "Gilded",
            description = "Champagne key light over deep velvet shadows, with a fine satin texture",
            summary = "champagne spotlight · velvet shadows",
            gilded = true,
            seed = 701,
            exposure = 0.91f,
            saturation = 0.90f,
            edgeShade = 0.38f,
            light = 0.19f,
            grain = 0.012f,
            defaultIntensity = 80,
        ),
        NocturneParams(
            id = "nocturne_afterhours",
            displayName = "Afterhours",
            description = "Cool penthouse window light, platinum reflections and cinematic edge shading",
            summary = "platinum rim light · midnight shade",
            gilded = false,
            seed = 702,
            exposure = 0.88f,
            saturation = 0.76f,
            edgeShade = 0.32f,
            light = 0.16f,
            grain = 0.008f,
            defaultIntensity = 75,
        ),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        NocturneEffectFactory.create(params)
    }
}
