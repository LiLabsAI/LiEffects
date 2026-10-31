package com.aliahad.photoeffects.adjust

/** Which optional Adjust GPU/CPU passes exist for a parameter set. */
data class AdjustPlan(
    val detail: Boolean,
    val blur: Boolean,
) {
    val passCount: Int get() = 2 + (if (detail) 1 else 0) + (if (blur) 1 else 0)
}

fun planFor(params: AdjustParams): AdjustPlan =
    AdjustPlan(detail = params.layers.detail, blur = params.layers.blur)

/**
 * Rough peak heap cost of one CPU Adjust render. The color/finish path keeps source, working and
 * output ARGB buffers; detail adds support planes and blur adds an ARGB blur chain.
 */
fun adjustBytesPerPixel(plan: AdjustPlan): Int =
    24 + (if (plan.detail) 12 else 0) + (if (plan.blur) 32 else 0)
