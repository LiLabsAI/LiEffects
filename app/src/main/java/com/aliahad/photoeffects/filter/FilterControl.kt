package com.aliahad.photoeffects.filter

/** Controls exposed by a recipe, independently of its catalogue family or display name. */
enum class FilterControl(val label: String, val range: IntRange = 0..100) {
    Strength("Filter"), Sharpen("Sharpen"), Intensity("Intensity"), Grain("Grain"),
    Highlights("Highlights", -100..100), Shadows("Shadows", -100..100),
    Aberration("Aberration"),
}
