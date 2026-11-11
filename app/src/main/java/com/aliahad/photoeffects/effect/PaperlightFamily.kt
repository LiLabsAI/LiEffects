package com.aliahad.photoeffects.effect

/**
 * Five print-inspired film grades with paper overlays and seeded grain.
 * Frame assets and tone-curve data retain their calibrated rendering values.
 */
object PaperlightFamily {

    const val ID = "Paperlight"

    val presets: List<PaperlightParams> = listOf(
        PaperlightParams(
            id = "paperlight_1",
            displayName = "Paperlight #1",
            description = "Warm, clean print with a rough-edged paper frame and heavy grain",
            seed = 501,
            filter = 100, grain = 100, frame = true,
            toneR = CubicTone(-0.70836f, 0.92234f, 0.61433f, 0.07635f),
            toneG = CubicTone(-0.66491f, 0.91228f, 0.57965f, 0.07186f),
            toneB = CubicTone(-0.54996f, 0.80465f, 0.56573f, 0.05211f),
            satMix = 0.114f,
            gainR = GainOffset(1.01113f, -0.00626f),
            gainG = GainOffset(1.00095f, 0.00013f),
            gainB = GainOffset(1.01183f, -0.00560f),
            grainAmp = 0.030f,
            frameAsset = "paperlight_frame_1.png",
        ),
        PaperlightParams(
            id = "paperlight_2",
            displayName = "Paperlight #2",
            description = "Cool blue print with a wide, torn paper frame and grain",
            seed = 502,
            filter = 100, grain = 100, frame = true,
            toneR = CubicTone(-0.91857f, 1.59128f, 0.18643f, 0.09844f),
            toneG = CubicTone(-0.67817f, 1.31116f, 0.25135f, 0.09627f),
            toneB = CubicTone(-0.88693f, 1.43514f, 0.29382f, 0.10368f),
            satMix = 0.059f,
            gainR = GainOffset(1.00701f, -0.00419f),
            gainG = GainOffset(1.00042f, -0.00005f),
            gainB = GainOffset(1.01033f, -0.00140f),
            grainAmp = 0.028f,
            frameAsset = "paperlight_frame_2.png",
        ),
        PaperlightParams(
            id = "paperlight_3",
            displayName = "Paperlight #3",
            description = "Bright neutral print with a slim white frame and fine grain",
            seed = 503,
            filter = 100, grain = 100, frame = true,
            toneR = CubicTone(-1.67822f, 2.38486f, 0.20392f, 0.00970f),
            toneG = CubicTone(-1.75843f, 2.47497f, 0.15728f, 0.01035f),
            toneB = CubicTone(-1.50193f, 2.18042f, 0.20737f, 0.00809f),
            satMix = 0.037f,
            gainR = GainOffset(1.00306f, -0.00225f),
            gainG = GainOffset(1.00056f, 0.00002f),
            gainB = GainOffset(1.00425f, -0.00088f),
            grainAmp = 0.032f,
            frameAsset = "paperlight_frame_3.png",
        ),
        PaperlightParams(
            id = "paperlight_4",
            displayName = "Paperlight #4",
            description = "Sun-warmed print with a black-lined paper frame and coarse grain",
            seed = 504,
            filter = 100, grain = 100, frame = true,
            toneR = CubicTone(-0.77575f, 0.99169f, 0.65916f, 0.07490f),
            toneG = CubicTone(-0.52999f, 0.70049f, 0.64476f, 0.09970f),
            toneB = CubicTone(-0.51575f, 0.81576f, 0.46838f, 0.09487f),
            satMix = 0.0f,
            gainR = GainOffset(0.99642f, 0.00196f),
            gainG = GainOffset(1.00014f, -0.00023f),
            gainB = GainOffset(0.99888f, 0.00099f),
            grainAmp = 0.036f,
            frameAsset = "paperlight_frame_4.png",
        ),
        PaperlightParams(
            id = "paperlight_5",
            displayName = "Paperlight #5",
            description = "Deep warm print with a rounded black-lined frame and heavy grain",
            seed = 505,
            filter = 100, grain = 80, frame = true,
            toneR = CubicTone(-0.82528f, 1.01272f, 0.69019f, 0.06369f),
            toneG = CubicTone(-0.54684f, 0.74560f, 0.64453f, 0.06899f),
            toneB = CubicTone(-0.49048f, 0.73719f, 0.54537f, 0.07132f),
            satMix = 0.0f,
            gainR = GainOffset(0.99824f, 0.00077f),
            gainG = GainOffset(1.00006f, -0.00004f),
            gainB = GainOffset(0.99943f, 0.00036f),
            grainAmp = 0.030f,
            frameAsset = "paperlight_frame_5.png",
        ),
    )

    /** Cross-list the print presets on the Trends shelf. */
    private val extraCategories: Map<String, Set<EffectCategory>> = presets.associate { params ->
        params.id to setOf(EffectCategory.Trends)
    }

    val effects: List<PhotoEffect> = presets.map { params ->
        PaperlightEffectFactory.create(params, extraCategories[params.id].orEmpty())
    }
}
