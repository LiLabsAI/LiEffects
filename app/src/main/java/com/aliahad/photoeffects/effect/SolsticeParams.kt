package com.aliahad.photoeffects.effect

/** Normalized elliptical light leak, screened over the photograph before intensity blending. */
data class SolsticeParams(
    val id: String,
    val displayName: String,
    val description: String,
    val summary: String,
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
    val red: Float,
    val green: Float,
    val blue: Float,
    val light: Float,
    val grain: Float,
    val seed: Int,
    val defaultIntensity: Int = 80,
) {
    init {
        require(radiusX.isFinite() && radiusX > 0f && radiusY.isFinite() && radiusY > 0f)
        require(centerX.isFinite() && centerY.isFinite())
        require(listOf(red, green, blue, light, grain).all { it.isFinite() && it in 0f..1f })
        require(defaultIntensity in 0..100)
    }

    fun toUniforms(): Map<String, Float> = mapOf(
        "uCenterX" to centerX, "uCenterY" to centerY,
        "uRadiusX" to radiusX, "uRadiusY" to radiusY,
        "uLightR" to red, "uLightG" to green, "uLightB" to blue,
        "uLight" to light, "uGrain" to grain, "uSeed" to seed.toFloat(),
    )
}
