package com.aliahad.photoeffects.effect

/**
 * Piecewise hue mapping for the HALATION halo colours, fitted from PREQUEL same-photo exports
 * (`docs/prequel-research/app/verification/variant-fidelity-report.md` §2).
 *
 * Measured fringes (98.5th-percentile additive collar pixels, PREQUEL export minus no-effect
 * baseline): #1 hue 0.00 = red-orange (1.00,0.20,0.01), #2 hue 0.50 = cyan (0.01,0.79,1.00),
 * #3 hue 0.70 = blue-violet (0.37,0.27,1.00). A linear red→blue mix would make Hue 0.50 purple,
 * so the mapping is piecewise:
 *
 *   0.0 : redOrange   (1.00,0.20,0.01)
 *   0.5 : cyan        (0.01,0.79,1.00)
 *   0.7 : blueViolet  (0.37,0.27,1.00)
 *   1.0 : violet      (0.60,0.25,1.00)
 *
 *   inner halo: warmWhite -> cyanWhite -> blueWhite -> violetWhite
 *
 * The AGSL shader (`HalationAgsl.kt`) contains the identical piecewise math; this object is the
 * CPU mirror used by `CpuHalationRenderer` and unit tests.
 */
object HalationHue {

    val redOrange = floatArrayOf(1.00f, 0.20f, 0.01f)
    val cyan = floatArrayOf(0.01f, 0.79f, 1.00f)
    val blueViolet = floatArrayOf(0.37f, 0.27f, 1.00f)
    val violet = floatArrayOf(0.60f, 0.25f, 1.00f)
    val warmWhite = floatArrayOf(1.00f, 0.96f, 0.86f)
    val cyanWhite = floatArrayOf(0.86f, 0.95f, 1.00f)
    val blueWhite = floatArrayOf(0.90f, 0.94f, 1.00f)
    val violetWhite = floatArrayOf(0.94f, 0.90f, 1.00f)

    /** Outer halo tint: hue 0 = red/orange, 0.5 = cool cyan, 0.7 = blue/violet, 1 = violet. */
    fun outerColor(hue: Float): FloatArray {
        val h = hue.coerceIn(0f, 1f)
        return when {
            h <= 0.5f -> mix(redOrange, cyan, 2f * h)
            h <= 0.7f -> mix(cyan, blueViolet, (h - 0.5f) / 0.2f)
            else -> mix(blueViolet, violet, (h - 0.7f) / 0.3f)
        }
    }

    /** Inner halo tint: warm white -> cyan white -> blue white -> violet white. */
    fun innerColor(hue: Float): FloatArray {
        val h = hue.coerceIn(0f, 1f)
        return when {
            h <= 0.5f -> mix(warmWhite, cyanWhite, 2f * h)
            h <= 0.7f -> mix(cyanWhite, blueWhite, (h - 0.5f) / 0.2f)
            else -> mix(blueWhite, violetWhite, (h - 0.7f) / 0.3f)
        }
    }

    private fun mix(a: FloatArray, b: FloatArray, t: Float): FloatArray = floatArrayOf(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
    )
}
