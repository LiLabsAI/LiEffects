package com.aliahad.photoeffects.effect

/**
 * Five soft portrait presets with tone curves, masked blur and fine grain.
 * The blur tool value drives the shared masked-blur radius.
 */
object VelourFamily {

    const val ID = "Velour"

    val presets: List<VelourParams> = listOf(
        VelourParams(
            id = "velour_1",
            displayName = "Velour #1",
            description = "Dreamy heavy soft-focus with a warm lifted grade and fine grain (pack default)",
            seed = 401,
            filter = 80, blur = 60, texture = 100, monochrome = 0,
            sigma1080 = 2.16f,
            toneR = CubicTone(-1.72827f, 2.35061f, 0.25792f, 0.02981f),
            toneG = CubicTone(-1.61948f, 2.68282f, -0.13652f, 0.05348f),
            toneB = CubicTone(0.01295f, 0.52265f, 0.54160f, 0.00525f),
            satMix = 0.67f,
            gainR = GainOffset(1.06336f, -0.02092f),
            gainG = GainOffset(1.00328f, -0.00219f),
            gainB = GainOffset(1.01971f, -0.00845f),
            grainAmp = 0.0172f,
            defaultIntensity = 100,
        ),
        VelourParams(
            id = "velour_2",
            displayName = "Velour #2",
            description = "Bright hazy soft-focus with coarser grain",
            seed = 402,
            filter = 100, blur = 20, texture = 100, monochrome = 0, random = 0,
            sigma1080 = 2.16f,
            toneR = CubicTone(-1.84111f, 2.32361f, 0.33665f, 0.06649f),
            toneG = CubicTone(-2.69372f, 3.66666f, -0.21352f, 0.08524f),
            toneB = CubicTone(-0.24680f, 0.59415f, 0.72718f, 0.02281f),
            satMix = 0.464f,
            gainR = GainOffset(1.02400f, -0.01727f),
            gainG = GainOffset(1.00876f, -0.00239f),
            gainB = GainOffset(1.01295f, -0.00487f),
            grainAmp = 0.0378f,
            defaultIntensity = 100,
        ),
        VelourParams(
            id = "velour_3",
            displayName = "Velour #3",
            description = "Soft warm dream with strong masked blur and medium grain",
            seed = 403,
            filter = 100, blur = 50, texture = 100, monochrome = 0, scale = 40,
            sigma1080 = 2.16f,
            toneR = CubicTone(-1.90768f, 2.94396f, -0.19772f, 0.10019f),
            toneG = CubicTone(-2.32137f, 3.70907f, -0.52591f, 0.13104f),
            toneB = CubicTone(0.03223f, 0.54285f, 0.57163f, 0.04568f),
            satMix = 0.332f,
            gainR = GainOffset(1.01055f, -0.01345f),
            gainG = GainOffset(1.00644f, -0.00033f),
            gainB = GainOffset(1.02918f, -0.00803f),
            grainAmp = 0.0270f,
            defaultIntensity = 100,
        ),
        VelourParams(
            id = "velour_4",
            displayName = "Velour #4",
            description = "Deep dreamy soft-focus with a cool heavy grade and grain",
            seed = 404,
            filter = 100, blur = 20, texture = 100, monochrome = 0, scale = 30,
            sigma1080 = 1.62f,
            toneR = CubicTone(-2.47106f, 3.81200f, -0.68170f, 0.15628f),
            toneG = CubicTone(-2.54615f, 4.00193f, -0.81892f, 0.18586f),
            toneB = CubicTone(-2.24726f, 3.54888f, -0.68990f, 0.19226f),
            satMix = 0.339f,
            gainR = GainOffset(1.02392f, -0.02015f),
            gainG = GainOffset(1.00778f, -0.00076f),
            gainB = GainOffset(1.04114f, -0.00647f),
            grainAmp = 0.0250f,
            defaultIntensity = 100,
        ),
        VelourParams(
            id = "velour_5",
            displayName = "Velour #5",
            description = "Lightest dreamy wash with a gentle lift and grain",
            seed = 405,
            filter = 60, blur = 30, texture = 100, monochrome = 0, random = 0,
            sigma1080 = 0.00f,
            toneR = CubicTone(-0.01926f, 0.31556f, 0.49516f, 0.09124f),
            toneG = CubicTone(0.33649f, 0.12485f, 0.43530f, 0.09629f),
            toneB = CubicTone(0.52856f, -0.18999f, 0.52131f, 0.09318f),
            satMix = 0.175f,
            gainR = GainOffset(1.03167f, -0.01182f),
            gainG = GainOffset(0.99888f, 0.00038f),
            gainB = GainOffset(1.01432f, -0.00283f),
            grainAmp = 0.0222f,
            defaultIntensity = 100,
        ),
    )

    /** photo-editor cross-listing (the pack was found on the TRENDS shelf). */
    private val extraCategories: Map<String, Set<EffectCategory>> = mapOf(
        "velour_1" to setOf(EffectCategory.Trends),
        "velour_2" to setOf(EffectCategory.Trends, EffectCategory.New),
        "velour_3" to setOf(EffectCategory.Films),
        "velour_4" to setOf(EffectCategory.ForYou),
        "velour_5" to setOf(EffectCategory.New, EffectCategory.Films),
    )

    val effects: List<PhotoEffect> = presets.map { params ->
        VelourEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
