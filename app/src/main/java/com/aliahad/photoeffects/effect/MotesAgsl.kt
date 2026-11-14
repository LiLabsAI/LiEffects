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

package com.aliahad.photoeffects.effect

/**
 * GPU texture pipeline shared by all Motes presets. Uniforms mirror MotesParams;
 * the CPU counterpart is CpuMotesRenderer. Grading, bloom, chromatic fringing,
 * seeded grain and dust are blended at the selected intensity.
 */
internal const val MOTES_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uGrade0;
uniform float uGrade1;
uniform float uGrade2;
uniform float uGrade3;
uniform float uGrade4;
uniform float uGrade5;
uniform float uGrade6;
uniform float uGrade7;
uniform float uGrade8;
uniform float uGrade9;
uniform float uGrade10;
uniform float uGrade11;
uniform float uAberration;
uniform float uBlur;
uniform float uLumen;
uniform float uGrain;
uniform float uDust;
uniform float uDustWhite;
uniform float uDustSize;
uniform float uDustOpacity;
uniform float uScratches;
uniform float uTexture;
uniform float uVignette;

// RuntimeShader child colors are premultiplied; effect math uses straight RGB like the CPU.
float3 sourceStraight(half4 h) {
    return h.a > 0.0 ? float3(float(h.r), float(h.g), float(h.b)) / float(h.a)
        : float3(0.0);
}

float fract1(float x) { return x - floor(x); }

// ---------------------------------------------------------------- lattice noise
// Shared with PixelMath.hashF / vnoise / fbm2.
// NOTE: AGSL RuntimeShader on Android 16 rejects both `uint` and shift operators, so we use the
// classic float sin-hash. Lattice coordinates are scaled down to a moderate magnitude so the
// sin() argument keeps float32 precision (hashing raw device-pixel coords with fract(large*const)
// loses fractional bits and produces the lattice/moire banding this replaced).
float hashF(float2 cell, float seed) {
    float2 c = cell * 0.1 + float2(seed * 0.137, seed * 0.291);
    return fract1(sin(dot(c, float2(12.9898, 78.233))) * 43758.5453);
}

float vnoise(float2 p, float seed) {
    float2 i = floor(p);
    float2 f = p - i;
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hashF(i, seed);
    float b = hashF(i + float2(1.0, 0.0), seed);
    float c = hashF(i + float2(0.0, 1.0), seed);
    float d = hashF(i + float2(1.0, 1.0), seed);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm2(float2 p, float seed) {
    return (vnoise(p, seed) * 2.0 + vnoise(p * 2.0, seed + 17.0)) / 3.0;
}

// ---------------------------------------------------------------- colour helpers
float softLightFilm(float base, float b) {
    float d = base <= 0.25 ? ((16.0 * base - 12.0) * base + 4.0) * base : sqrt(max(base, 0.0));
    if (b <= 0.5) {
        return base - (1.0 - 2.0 * b) * base * (1.0 - base);
    }
    return base + (2.0 * b - 1.0) * (d - base);
}

float3 sampleRgb(float2 p) {
    half4 h = content.eval(p);
    return sourceStraight(h);
}

float sampleLuma(float2 p) {
    half4 h = content.eval(p);
    return dot(sourceStraight(h), float3(0.2126, 0.7152, 0.0722));
}

half4 main(float2 fragCoord) {
    float t = clamp(uIntensity, 0.0, 1.0);
    half4 srcH = content.eval(fragCoord);
    if (t <= 0.0) {
        return srcH;
    }

    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = fragCoord / res;
    float sc = min(res.x, res.y) / 1080.0;
    float3 src = sourceStraight(srcH);

    // ------------------------------------------------------------------ 2a. aberration
    float3 aber = src;
    if (uAberration > 0.001) {
        float2 d = (uv - 0.5) * uAberration * 0.012 * res;
        float3 pr = sampleRgb(fragCoord + d);
        float3 pb = sampleRgb(fragCoord - d);
        aber = float3(pr.r, src.g, pb.b);
    }

    // ------------------------------------------------------------------ 2b. soft focus
    float3 base = aber;
    if (uBlur > 0.001) {
        float r = max(uBlur * 6.0 * sc, 1.0);
        float3 acc = aber * 0.2;
        acc += sampleRgb(fragCoord + float2(r, 0.0)) * 0.12;
        acc += sampleRgb(fragCoord - float2(r, 0.0)) * 0.12;
        acc += sampleRgb(fragCoord + float2(0.0, r)) * 0.12;
        acc += sampleRgb(fragCoord - float2(0.0, r)) * 0.12;
        acc += sampleRgb(fragCoord + float2(r * 0.7, r * 0.7)) * 0.08;
        acc += sampleRgb(fragCoord + float2(-r * 0.7, r * 0.7)) * 0.08;
        acc += sampleRgb(fragCoord + float2(r * 0.7, -r * 0.7)) * 0.08;
        acc += sampleRgb(fragCoord + float2(-r * 0.7, -r * 0.7)) * 0.08;
        base = mix(aber, acc, clamp(uBlur, 0.0, 1.0));
    }

    // ------------------------------------------------------------------ 1. color grade
    // Per-variant affine grade (row-major 3x4) calibrated from same-photo exports; replaces
    // the old scalar warm/tint/sepia chain so each variant reproduces its measured cast.
    float3 c = float3(
        base.r * uGrade0 + base.g * uGrade1 + base.b * uGrade2 + uGrade3,
        base.r * uGrade4 + base.g * uGrade5 + base.b * uGrade6 + uGrade7,
        base.r * uGrade8 + base.g * uGrade9 + base.b * uGrade10 + uGrade11
    );
    c = clamp(c, 0.0, 1.0);

    // ------------------------------------------------------------------ 3. halation
    if (uLumen > 0.001) {
        float hr = max((2.5 + uLumen * 4.0) * sc, 1.0);
        float hi = 0.2 * max(0.0, sampleLuma(fragCoord) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(hr, 0.0)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord - float2(hr, 0.0)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(0.0, hr)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord - float2(0.0, hr)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(hr * 0.7, hr * 0.7)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(-hr * 0.7, hr * 0.7)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(hr * 0.7, -hr * 0.7)) - 0.65);
        hi += 0.1 * max(0.0, sampleLuma(fragCoord + float2(-hr * 0.7, -hr * 0.7)) - 0.65);
        float3 hal = (hi * uLumen * 2.2) * float3(1.0, 0.6, 0.35);
        c = 1.0 - (1.0 - c) * (1.0 - clamp(hal, 0.0, 1.0));
        c = clamp(c, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ 4. film grain
    // Integer-lattice white noise band-limited to ~1.2px cells (spec amplitude 0.5*grain,
    // double soft-light). No float fract(huge) hashing => no lattice banding.
    if (uGrain > 0.001) {
        float cell = max(1.2 * sc, 1.0);
        float gv = hashF(floor(fragCoord / cell), uSeed + 11.0);
        float gb = 0.5 + (gv - 0.5) * (uGrain * 0.18);
        c = float3(softLightFilm(c.r, gb), softLightFilm(c.g, gb), softLightFilm(c.b, gb));
        c = clamp(c, 0.0, 1.0);
        c = float3(softLightFilm(c.r, gb), softLightFilm(c.g, gb), softLightFilm(c.b, gb));
        c = clamp(c, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ 5. dust specks
    // Scalar mask only (never the noise alpha): sparse fbm2 clumps + crisp 1px flecks.
    // The mask carries 0.7 opacity; the dust amount controls density/size only,
    // so specks stay visible instead of fading with the slider.
    if (uDust > 0.001) {
        // Larger dustSize => slightly larger specks; never sub-pixel (band-limited).
        float cell = max(2.0 * max(uDustSize, 0.25) * sc, 2.0);
        float2 q = fragCoord / cell;
        float f = fbm2(q, uSeed + 3.0);
        if (sc >= 2.0) {
            float2 o = float2(sc, 0.0) / cell;
            float2 oy = float2(0.0, sc) / cell;
            f = (f * 2.0 + fbm2(q + o, uSeed + 3.0) + fbm2(q - o, uSeed + 3.0)
                 + fbm2(q + oy, uSeed + 3.0) + fbm2(q - oy, uSeed + 3.0)) / 6.0;
        }
        float thr = 0.95 - 0.20 * uDust;
        float m = smoothstep(thr, thr + 0.08, f);
        float a = clamp(m * uDustOpacity, 0.0, 1.0);
        // Sparse crisp single-pixel flecks (fine noise collapses to 1-2 px specks).
        float fleck = hashF(floor(fragCoord), uSeed + 5.0);
        float fleckA = step(1.0 - uDust * 0.002, fleck) * uDustOpacity * 0.85;
        a = max(a, fleckA);
        float3 dustCol = mix(
            float3(0.0, 0.0, 0.0),
            float3(1.0, 0.985, 0.955),
            clamp(uDustWhite, 0.0, 1.0)
        );
        c = mix(c, dustCol, a);
    }

    // ------------------------------------------------------------------ 6. scratches
    if (uScratches > 0.001) {
        float s = 0.0;
        float py = fragCoord.y / 1080.0;
        for (int k = 0; k < 8; k++) {
            float fk = float(k);
            float xi = hashF(float2(fk, 3.0), uSeed + 7.0) * 0.98 + 0.01;
            float wob = 0.0015 * sin(py * 6.0 + fk * 2.1 + uSeed);
            float dx = abs(uv.x - (xi + wob));
            float line = 1.0 - smoothstep(0.0, 0.0025, dx);
            float gate = step(0.4, hashF(float2(floor(py * 0.5), fk * 13.0), uSeed + 13.0));
            s += line * gate;
        }
        c = mix(c, float3(0.95, 0.94, 0.90), clamp(s, 0.0, 1.0) * uScratches * 0.35);
    }

    // ------------------------------------------------------------------ 7. texture
    if (uTexture > 0.001) {
        float tx = fbm2(uv * 3.0, uSeed + 29.0);
        c = c * (1.0 - uTexture * 0.12 * (1.0 - tx));
    }

    // ------------------------------------------------------------------ 8. vignette
    if (uVignette > 0.001) {
        float2 vd = (uv - 0.5) * float2(res.x / res.y, 1.0);
        float vig = 1.0 - uVignette * smoothstep(0.35, 0.95, length(vd) * 0.9);
        c = c * vig;
    }

    // ------------------------------------------------------------------ 9. intensity blend
    float3 outc = mix(src, clamp(c, 0.0, 1.0), t);
    float alpha = float(srcH.a);
    return half4(half(outc.x * alpha), half(outc.y * alpha), half(outc.z * alpha), srcH.a);
}
"""
