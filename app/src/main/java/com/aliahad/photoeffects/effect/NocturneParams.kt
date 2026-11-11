package com.aliahad.photoeffects.effect

data class NocturneParams(
    val id: String,
    val displayName: String,
    val description: String,
    val summary: String,
    val gilded: Boolean,
    val seed: Int,
    val exposure: Float,
    val saturation: Float,
    val edgeShade: Float,
    val light: Float,
    val grain: Float,
    val defaultIntensity: Int,
) {
    fun toUniforms(): Map<String, Float> = mapOf(
        "uStyle" to if (gilded) 0f else 1f,
        "uSeed" to seed.toFloat(),
        "uExposure" to exposure,
        "uSaturation" to saturation,
        "uEdgeShade" to edgeShade,
        "uLight" to light,
        "uGrain" to grain,
    )
}
