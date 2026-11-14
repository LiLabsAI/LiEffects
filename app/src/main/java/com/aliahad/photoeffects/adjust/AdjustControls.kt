/*
 * LiEffects — Android photo editor
 * Copyright (C) 2026 Ali Ahad
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.aliahad.photoeffects.adjust

/** The eight Adjust categories shown in the editor. */
enum class AdjustCategory(val label: String) {
    Light("Light"),
    Color("Color"),
    Hsl("HSL"),
    Details("Details"),
    Stylize("Stylize"),
    Refocus("Refocus"),
    Optical("Optical"),
    Blur("Blur"),
}

/** Which render pass owns a control. */
enum class AdjustPass { Color, Detail, Blur, Finish }

enum class HslBand(
    val label: String,
    val centerDegrees: Float,
    val halfWidthDegrees: Float,
    val colorRgb: Long,
) {
    Red("Red", 0f, 35f, 0xE84545),
    Orange("Orange", 30f, 32f, 0xF9964E),
    Yellow("Yellow", 60f, 35f, 0xFFDE33),
    Green("Green", 120f, 48f, 0x59DF42),
    Blue("Blue", 240f, 55f, 0x6372FF),
    Purple("Purple", 300f, 42f, 0xE023FF),
}

enum class HslComponent(val label: String) { Hue("Hue"), Saturation("Saturation"), Lightness("Lightness") }

/**
 * The complete Adjust inventory (59 controls). Labels, ranges and neutral values are shared
 * by the UI and rendering plan. Implementations use analytic operations rather than LUTs.
 */
enum class AdjustControl(
    val category: AdjustCategory,
    val label: String,
    val range: IntRange,
    val neutral: Int,
    val pass: AdjustPass,
) {
    // Light
    Exposure(AdjustCategory.Light, "Exposure", -100..100, 0, AdjustPass.Color),
    Brightness(AdjustCategory.Light, "Brightness", -100..100, 0, AdjustPass.Color),
    Contrast(AdjustCategory.Light, "Contrast", -100..100, 0, AdjustPass.Color),
    Highlights(AdjustCategory.Light, "Highlights", -100..100, 0, AdjustPass.Color),
    Whites(AdjustCategory.Light, "Whites", -100..100, 0, AdjustPass.Color),
    Shadows(AdjustCategory.Light, "Shadows", -100..100, 0, AdjustPass.Color),
    BlackPoint(AdjustCategory.Light, "Black point", -100..100, 0, AdjustPass.Color),
    Fade(AdjustCategory.Light, "Fade", 0..100, 0, AdjustPass.Color),
    Brilliance(AdjustCategory.Light, "Brilliance", -100..100, 0, AdjustPass.Color),
    PushPull(AdjustCategory.Light, "Push-pull", -100..100, 0, AdjustPass.Color),
    Cleanup(AdjustCategory.Light, "Cleanup", 0..100, 0, AdjustPass.Detail),

    // Color
    Saturation(AdjustCategory.Color, "Saturation", -100..100, 0, AdjustPass.Color),
    Vibrance(AdjustCategory.Color, "Vibrance", -100..100, 0, AdjustPass.Color),
    ColorDensity(AdjustCategory.Color, "Color density", 0..100, 0, AdjustPass.Color),
    Temperature(AdjustCategory.Color, "Temperature", -100..100, 0, AdjustPass.Color),
    Tint(AdjustCategory.Color, "Tint", -100..100, 0, AdjustPass.Color),
    Skintone(AdjustCategory.Color, "Skintone", -100..100, 0, AdjustPass.Color),
    Hdr(AdjustCategory.Color, "HDR", 0..100, 0, AdjustPass.Detail),

    // HSL (six bands x three components)
    HslRedHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslRedSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslRedLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),
    HslOrangeHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslOrangeSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslOrangeLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),
    HslYellowHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslYellowSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslYellowLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),
    HslGreenHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslGreenSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslGreenLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),
    HslBlueHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslBlueSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslBlueLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),
    HslPurpleHue(AdjustCategory.Hsl, "Hue", -100..100, 0, AdjustPass.Color),
    HslPurpleSaturation(AdjustCategory.Hsl, "Saturation", -100..100, 0, AdjustPass.Color),
    HslPurpleLightness(AdjustCategory.Hsl, "Lightness", -100..100, 0, AdjustPass.Color),

    // Details
    Sharpen(AdjustCategory.Details, "Sharpen", 0..100, 0, AdjustPass.Detail),
    Ultrasharp(AdjustCategory.Details, "Ultrasharp", 0..100, 0, AdjustPass.Detail),
    Clarity(AdjustCategory.Details, "Clarity", 0..100, 0, AdjustPass.Detail),
    Dehaze(AdjustCategory.Details, "Dehaze", -100..100, 0, AdjustPass.Detail),
    MidtoneDetails(AdjustCategory.Details, "Midtone details", -100..100, 0, AdjustPass.Detail),
    ChromaDenoise(AdjustCategory.Details, "Color denoise", 0..100, 0, AdjustPass.Detail),

    // Stylize
    Grain(AdjustCategory.Stylize, "Grain", 0..100, 0, AdjustPass.Finish),
    Dust(AdjustCategory.Stylize, "Dust", -100..100, 0, AdjustPass.Finish),
    Vignette(AdjustCategory.Stylize, "Vignette", -100..100, 0, AdjustPass.Finish),
    Vintage(AdjustCategory.Stylize, "Vintage", 0..100, 0, AdjustPass.Finish),

    // Refocus
    RefocusAmount(AdjustCategory.Refocus, "Blur amount", 0..100, 0, AdjustPass.Blur),
    FocusPoint(AdjustCategory.Refocus, "Focus point", 0..100, 50, AdjustPass.Blur),
    FocusRange(AdjustCategory.Refocus, "Focus range", 0..100, 50, AdjustPass.Blur),

    // Optical
    Fisheye(AdjustCategory.Optical, "Fisheye", -100..100, 0, AdjustPass.Finish),
    RgbSplit(AdjustCategory.Optical, "RGB split", 0..100, 0, AdjustPass.Finish),
    Aberration(AdjustCategory.Optical, "Aberration", 0..100, 0, AdjustPass.Finish),

    // Blur
    Mist(AdjustCategory.Blur, "Mist", 0..100, 0, AdjustPass.Blur),
    Glow(AdjustCategory.Blur, "Glow", 0..100, 0, AdjustPass.Blur),
    GaussianBlur(AdjustCategory.Blur, "Gaussian blur", 0..100, 0, AdjustPass.Blur),
    VignetteBlur(AdjustCategory.Blur, "Vignette blur", 0..100, 0, AdjustPass.Blur),
    ZoomBlur(AdjustCategory.Blur, "Zoom blur", 0..100, 0, AdjustPass.Blur),
    RadialBlur(AdjustCategory.Blur, "Radial blur", 0..100, 0, AdjustPass.Blur),
    DirectionalBlur(AdjustCategory.Blur, "Directional blur", 0..100, 0, AdjustPass.Blur);

    val id: String
        get() = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    val signed: Boolean
        get() = range.first < 0

    companion object {
        private val hslMap: Map<AdjustControl, Pair<HslBand, HslComponent>> = buildMap {
            for (band in HslBand.entries) {
                for (component in HslComponent.entries) {
                    put(hslControl(band, component), band to component)
                }
            }
        }

        fun byCategory(category: AdjustCategory): List<AdjustControl> =
            entries.filter { it.category == category }

        fun forHsl(band: HslBand, component: HslComponent): AdjustControl =
            when (band) {
                HslBand.Red -> when (component) {
                    HslComponent.Hue -> HslRedHue
                    HslComponent.Saturation -> HslRedSaturation
                    HslComponent.Lightness -> HslRedLightness
                }
                HslBand.Orange -> when (component) {
                    HslComponent.Hue -> HslOrangeHue
                    HslComponent.Saturation -> HslOrangeSaturation
                    HslComponent.Lightness -> HslOrangeLightness
                }
                HslBand.Yellow -> when (component) {
                    HslComponent.Hue -> HslYellowHue
                    HslComponent.Saturation -> HslYellowSaturation
                    HslComponent.Lightness -> HslYellowLightness
                }
                HslBand.Green -> when (component) {
                    HslComponent.Hue -> HslGreenHue
                    HslComponent.Saturation -> HslGreenSaturation
                    HslComponent.Lightness -> HslGreenLightness
                }
                HslBand.Blue -> when (component) {
                    HslComponent.Hue -> HslBlueHue
                    HslComponent.Saturation -> HslBlueSaturation
                    HslComponent.Lightness -> HslBlueLightness
                }
                HslBand.Purple -> when (component) {
                    HslComponent.Hue -> HslPurpleHue
                    HslComponent.Saturation -> HslPurpleSaturation
                    HslComponent.Lightness -> HslPurpleLightness
                }
            }

        fun hslBandOf(control: AdjustControl): HslBand? = hslMap[control]?.first
        fun hslComponentOf(control: AdjustControl): HslComponent? = hslMap[control]?.second

        private fun hslControl(band: HslBand, component: HslComponent): AdjustControl =
            when (band) {
                HslBand.Red -> when (component) {
                    HslComponent.Hue -> HslRedHue
                    HslComponent.Saturation -> HslRedSaturation
                    HslComponent.Lightness -> HslRedLightness
                }
                HslBand.Orange -> when (component) {
                    HslComponent.Hue -> HslOrangeHue
                    HslComponent.Saturation -> HslOrangeSaturation
                    HslComponent.Lightness -> HslOrangeLightness
                }
                HslBand.Yellow -> when (component) {
                    HslComponent.Hue -> HslYellowHue
                    HslComponent.Saturation -> HslYellowSaturation
                    HslComponent.Lightness -> HslYellowLightness
                }
                HslBand.Green -> when (component) {
                    HslComponent.Hue -> HslGreenHue
                    HslComponent.Saturation -> HslGreenSaturation
                    HslComponent.Lightness -> HslGreenLightness
                }
                HslBand.Blue -> when (component) {
                    HslComponent.Hue -> HslBlueHue
                    HslComponent.Saturation -> HslBlueSaturation
                    HslComponent.Lightness -> HslBlueLightness
                }
                HslBand.Purple -> when (component) {
                    HslComponent.Hue -> HslPurpleHue
                    HslComponent.Saturation -> HslPurpleSaturation
                    HslComponent.Lightness -> HslPurpleLightness
                }
            }
    }
}
