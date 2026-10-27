package com.aliahad.photoeffects.effect

/**
 * Shared seed derivation so the GPU preview, CPU thumbnails and CPU export all use exactly the
 * same deterministic noise seed for a given photo + variant.
 *
 * The 16-bit photo seed keeps the value small and exactly representable as an AGSL float
 * uniform (integers above 2^24 lose precision in float32).
 */
object EffectSeed {
    fun of(photoSeed: Long, variantSeed: Int): Int =
        ((photoSeed and 0xFFFF).toInt()) + variantSeed
}
