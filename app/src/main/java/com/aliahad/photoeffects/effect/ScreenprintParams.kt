package com.aliahad.photoeffects.effect

import kotlin.math.cos
import kotlin.math.sin

/** A one- or two-ink halftone screen. Pitch is measured at a 1600-pixel long edge. */
data class ScreenprintParams(
    val id: String,
    val displayName: String,
    val description: String,
    val summary: String,
    val pitch: Float,
    val angle: Float,
    val paperR: Float, val paperG: Float, val paperB: Float,
    val inkR: Float, val inkG: Float, val inkB: Float,
    val secondR: Float = inkR, val secondG: Float = inkG, val secondB: Float = inkB,
    val dual: Boolean = false,
    val grain: Float = 0.006f,
    val seed: Int,
    val defaultIntensity: Int = 85,
) {
    init {
        require(pitch.isFinite() && pitch in 8f..64f && angle.isFinite())
        require(listOf(paperR, paperG, paperB, inkR, inkG, inkB, secondR, secondG, secondB, grain)
            .all { it.isFinite() && it in 0f..1f })
        require(defaultIntensity in 0..100)
    }

    // Trigonometry runs once per preset, never in the pixel loops or shader.
    internal val cosine = cos(Math.toRadians(angle.toDouble())).toFloat()
    internal val sine = sin(Math.toRadians(angle.toDouble())).toFloat()

    fun toUniforms(): Map<String, Float> = mapOf(
        "uPitch" to pitch, "uCosine" to cosine, "uSine" to sine,
        "uPaperR" to paperR, "uPaperG" to paperG, "uPaperB" to paperB,
        "uInkR" to inkR, "uInkG" to inkG, "uInkB" to inkB,
        "uSecondR" to secondR, "uSecondG" to secondG, "uSecondB" to secondB,
        "uDual" to if (dual) 1f else 0f, "uGrain" to grain, "uSeed" to seed.toFloat(),
    )
}
