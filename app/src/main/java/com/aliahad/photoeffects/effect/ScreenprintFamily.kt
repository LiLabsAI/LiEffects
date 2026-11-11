package com.aliahad.photoeffects.effect

/** Tone-dependent ink dots and subtractive overprinting on lightly textured paper. */
object ScreenprintFamily {
    const val ID = "Screenprint"

    val presets: List<ScreenprintParams> = listOf(
        ScreenprintParams(
            id = "screenprint_carbon", displayName = "Carbon",
            description = "Fine charcoal halftone dots on warm off-white paper",
            summary = "charcoal ink · fine angled screen",
            pitch = 18f, angle = 45f,
            paperR = 0.98f, paperG = 0.97f, paperB = 0.93f,
            inkR = 0.06f, inkG = 0.08f, inkB = 0.10f,
            seed = 1001,
        ),
        ScreenprintParams(
            id = "screenprint_duet", displayName = "Duet",
            description = "Coral and navy inks with an offset second screen and visible overprint",
            summary = "coral + navy · two-ink overprint",
            pitch = 24f, angle = 30f,
            paperR = 0.99f, paperG = 0.96f, paperB = 0.89f,
            inkR = 0.88f, inkG = 0.18f, inkB = 0.25f,
            secondR = 0.06f, secondG = 0.29f, secondB = 0.48f,
            dual = true, seed = 1002,
        ),
        ScreenprintParams(
            id = "screenprint_terracotta", displayName = "Terracotta",
            description = "Coarse clay-brown ink dots on a pale peach paper stock",
            summary = "clay ink · coarse straight screen",
            pitch = 30f, angle = 0f,
            paperR = 1f, paperG = 0.92f, paperB = 0.82f,
            inkR = 0.34f, inkG = 0.12f, inkB = 0.07f,
            grain = 0.009f, seed = 1003,
        ),
    )

    val effects: List<PhotoEffect> = presets.map(ScreenprintEffectFactory::create)
}
