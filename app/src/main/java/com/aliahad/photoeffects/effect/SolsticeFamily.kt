package com.aliahad.photoeffects.effect

/** Asset-free light leaks with a soft colour field and a small neutral flare core. */
object SolsticeFamily {
    const val ID = "Solstice"

    val presets: List<SolsticeParams> = listOf(
        SolsticeParams(
            id = "solstice_ember", displayName = "Ember",
            description = "Amber and red film-edge light entering from the left",
            summary = "amber edge light · warm flare",
            centerX = -0.08f, centerY = 0.42f, radiusX = 0.68f, radiusY = 1.12f,
            red = 1f, green = 0.35f, blue = 0.08f,
            light = 0.82f, grain = 0.009f, seed = 901,
        ),
        SolsticeParams(
            id = "solstice_tidal", displayName = "Tidal",
            description = "Cool cyan light washing in from the upper-right edge",
            summary = "cyan wash · cool corner light",
            centerX = 1.04f, centerY = 0.12f, radiusX = 0.92f, radiusY = 0.78f,
            red = 0.10f, green = 0.72f, blue = 1f,
            light = 0.76f, grain = 0.007f, seed = 902,
        ),
        SolsticeParams(
            id = "solstice_rosette", displayName = "Rosette",
            description = "Rose and magenta light blooming upwards from the lower edge",
            summary = "rose bloom · soft lower flare",
            centerX = 0.62f, centerY = 1.06f, radiusX = 1.04f, radiusY = 0.76f,
            red = 1f, green = 0.16f, blue = 0.48f,
            light = 0.78f, grain = 0.008f, seed = 903,
        ),
    )

    val effects: List<PhotoEffect> = presets.map(SolsticeEffectFactory::create)
}
