package com.aliahad.photoeffects.filter

import kotlin.math.floor
import kotlin.math.max

/**
 * Original, static monochrome grain field shared with [FILTER_GRAIN_AGSL].
 *
 * Two smoothly interpolated lattice fields give irregular clumps without reference textures
 * or platform-dependent trigonometry. Coordinates use pixel centres on a 1600-pixel long-edge
 * reference, so resizing an image preserves the apparent grain size. Size is cell spacing in
 * reference pixels. The field is repeatable, not temporally animated or a physical film model.
 *
 * The modulo-4091 cubic permutation keeps every hash product below 4091² (< 2²⁴), well inside
 * signed int32 range on CPU and GPU. No bit shifts, unsigned arithmetic or sin/tan are needed.
 */
internal object FilterGrain {
    private const val MODULUS = 4091

    fun noise(x: Int, y: Int, width: Int, height: Int, seed: Int, size: Float): Float {
        val safeSize = if (size.isFinite()) size.coerceAtLeast(0.25f) else 1f
        val scale = 1600f / max(width, height).coerceAtLeast(1).toFloat() / safeSize
        val px = (x.toFloat() + 0.5f) * scale
        val py = (y.toFloat() + 0.5f) * scale
        val s = wrap(seed)
        return 0.75f * lattice(px, py, s) +
            0.25f * lattice(px * 1.93f + 17.25f, py * 1.93f + 53.5f, wrap(s + 811))
    }

    private fun wrap(value: Int): Int = ((value % MODULUS) + MODULUS) % MODULUS

    private fun hash(x: Int, y: Int, seed: Int): Float {
        var h = (wrap(x) * 131 + wrap(y) * 719 + seed * 283 + 911) % MODULUS
        val squared = h * h % MODULUS
        h = squared * h % MODULUS
        h = (h * 1597 + 2137) % MODULUS
        return h.toFloat() / 2045f - 1f
    }

    private fun lattice(x: Float, y: Float, seed: Int): Float {
        val floorX = floor(x)
        val floorY = floor(y)
        val ix = floorX.toInt()
        val iy = floorY.toInt()
        val fx = x - floorX
        val fy = y - floorY
        val u = fx * fx * (3f - 2f * fx)
        val v = fy * fy * (3f - 2f * fy)
        val a = hash(ix, iy, seed)
        val b = hash(ix + 1, iy, seed)
        val c = hash(ix, iy + 1, seed)
        val d = hash(ix + 1, iy + 1, seed)
        val top = a + (b - a) * u
        val bottom = c + (d - c) * u
        return top + (bottom - top) * v
    }
}

/** Same integer hash, pixel-centre coordinates, interpolation and octave weights as Kotlin. */
internal const val FILTER_GRAIN_AGSL = """
int filterGrainWrap(int v) {
    // AGSL excludes the remainder operator; bounded integer division is exact here.
    int r = v - (v / 4091) * 4091;
    return r < 0 ? r + 4091 : r;
}

float filterGrainHash(int x, int y, int seed) {
    int h = filterGrainWrap(filterGrainWrap(x) * 131 + filterGrainWrap(y) * 719 + seed * 283 + 911);
    int squared = filterGrainWrap(h * h);
    h = filterGrainWrap(squared * h);
    h = filterGrainWrap(h * 1597 + 2137);
    return float(h) / 2045.0 - 1.0;
}

float filterGrainLattice(float2 p, int seed) {
    float2 cell = floor(p);
    int x = int(cell.x);
    int y = int(cell.y);
    float2 f = p - cell;
    float2 t = f * f * (3.0 - 2.0 * f);
    float a = filterGrainHash(x, y, seed);
    float b = filterGrainHash(x + 1, y, seed);
    float c = filterGrainHash(x, y + 1, seed);
    float d = filterGrainHash(x + 1, y + 1, seed);
    float top = a + (b - a) * t.x;
    float bottom = c + (d - c) * t.x;
    return top + (bottom - top) * t.y;
}

float filterGrain(float2 coord, float2 resolution, float seed, float grainSize) {
    float scale = 1600.0 / max(max(resolution.x, resolution.y), 1.0) / max(grainSize, 0.25);
    float2 p = coord * scale;
    int s = filterGrainWrap(int(seed));
    return 0.75 * filterGrainLattice(p, s) +
        0.25 * filterGrainLattice(p * 1.93 + float2(17.25, 53.5), filterGrainWrap(s + 811));
}
"""
