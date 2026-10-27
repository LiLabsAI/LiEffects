package com.aliahad.photoeffects.effect

/**
 * The single AGSL program shared by all HALATION presets.
 *
 * Recovered PREQUEL HALATION recipe (docs/prequel-research/static-halation/… §6):
 *   1. soft-knee **max-channel** bright-pass (highlights above threshold, smooth knee)
 *   2. 3-scale halation field (separable-gaussian radii ≈ 8 / 28 / 64 × multiplier)
 *   3. **additive** tinted halo — inner white-yellow, outer hue-tinted (red-orange … blue-violet);
 *      additive (not screen) keeps the halo saturated
 *   4. optional warm haze screen (`#D7AA80` at low amount)
 *   5. base colour grade → film grain → vignette
 *   6. intensity blend with the original photo (slider 0..100, defaults 65 / 60 / 65)
 *
 * Drawn exactly like the dusty program: a Paint shader over a `BitmapShader` child (see
 * `PhotoPreviewCard` / `GpuEffectRenderer`).
 *
 * AGSL constraints on this project (Android 16 RuntimeShader): no `uint`, no shift operators,
 * no large-coordinate `fract` hashing (float32 precision => lattice/moire banding). Grain uses
 * the same scaled-lattice float sin-hash as `PixelMath.hashF`; loop bounds are constants/unrolled.
 *
 * Coordinates: `fragCoord` = device px, `uv` = normalized, `sc = min(W,H)/1080` converts
 * "1080-scale px" (PREQUEL multiplier) to device px.
 */
internal const val HALATION_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uHue;
uniform float uBrightThreshold;
uniform float uSoftKnee;
uniform float uOpacity;
uniform float uHaloGain;
uniform float uBlur;
uniform float uHaze;
uniform float uGrain;
uniform float uWarmth;
uniform float uTint;
uniform float uContrast;
uniform float uFade;
uniform float uSaturation;
uniform float uVignette;

float fract1(float x) { return x - floor(x); }

// Scaled-lattice float sin-hash (mirrors PixelMath.hashF): small lattice coordinates keep
// float32 sin() precision; no large-value fract hashing.
float hashF(float2 cell, float seed) {
    float2 c = cell * 0.1 + float2(seed * 0.137, seed * 0.291);
    return fract1(sin(dot(c, float2(12.9898, 78.233))) * 43758.5453);
}

float luma3(float3 c) { return dot(c, float3(0.2126, 0.7152, 0.0722)); }

float3 rgbAt(float2 p) {
    half4 h = content.eval(p);
    return float3(h.r, h.g, h.b);
}

float lumaAt(float2 p) { return luma3(rgbAt(p)); }

// 5-tap tent blur of luma; radius chosen to match the CPU box-blur edge detector.
float lumaBlur5(float2 p, float r) {
    float acc = lumaAt(p) * 0.4;
    acc += lumaAt(p + float2(r, 0.0)) * 0.15;
    acc += lumaAt(p - float2(r, 0.0)) * 0.15;
    acc += lumaAt(p + float2(0.0, r)) * 0.15;
    acc += lumaAt(p - float2(0.0, r)) * 0.15;
    return acc;
}

float softLightFilm(float base, float b) {
    float d = base <= 0.25 ? ((16.0 * base - 12.0) * base + 4.0) * base : sqrt(max(base, 0.0));
    if (b <= 0.5) {
        return base - (1.0 - 2.0 * b) * base * (1.0 - base);
    }
    return base + (2.0 * b - 1.0) * (d - base);
}

// ------------------------------------------------ 1. soft-knee max-channel bright-pass
// Exact recovered PREQUEL formula (static report §6):
//   f = 1 - t;  f2 = s*f;  br = max(r,g,b)
//   soft = clamp(br - (f - f2), 0, 2*f2);  soft = soft^2 * 0.25 / (f2 + 1e-5)
//   contrib = max(soft, br - f) / max(br, 1e-5)
// Returns the scalar highlight contribution (the halo mask).
float brightAt(float2 p) {
    float3 c = rgbAt(p);
    float br = max(max(c.r, c.g), c.b);
    // The parameter table's brightThreshold is a highlight floor (0..1); the recovered formula's
    // "f = 1 - t" refers to PREQUEL's internal darkness-space threshold, hence floor = t here.
    float f = clamp(uBrightThreshold, 0.0, 1.0);
    float f2 = max(clamp(uSoftKnee, 0.0, 1.0) * f, 1e-4);
    float soft = clamp(br - (f - f2), 0.0, 2.0 * f2);
    soft = soft * soft * 0.25 / (f2 + 1e-5);
    // Normalise by the available headroom above the floor => usable 0..1 halo mask.
    float norm = max(1.0 - f, 0.05);
    return clamp(max(soft, br - f) / norm, 0.0, 1.0);
}

// 5-tap tent average of the bright field (tight scales).
float brightBlur5(float2 p, float r) {
    float2 ax = float2(r, 0.0);
    float2 ay = float2(0.0, r);
    float acc = brightAt(p) * 0.4;
    acc += brightAt(p + ax) * 0.15;
    acc += brightAt(p - ax) * 0.15;
    acc += brightAt(p + ay) * 0.15;
    acc += brightAt(p - ay) * 0.15;
    return acc;
}

// 9-tap average of the bright field (wide scales, avoids cross-shaped ghosting).
float brightBlur9(float2 p, float r) {
    float2 ax = float2(r, 0.0);
    float2 ay = float2(0.0, r);
    float2 dg = float2(r * 0.7, r * 0.7);
    float acc = brightAt(p) * 0.2;
    acc += brightAt(p + ax) * 0.1;
    acc += brightAt(p - ax) * 0.1;
    acc += brightAt(p + ay) * 0.1;
    acc += brightAt(p - ay) * 0.1;
    acc += brightAt(p + dg) * 0.1;
    acc += brightAt(p + float2(-dg.x, dg.y)) * 0.1;
    acc += brightAt(p + float2(dg.x, -dg.y)) * 0.1;
    acc += brightAt(p - dg) * 0.1;
    return acc;
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
    float3 src = float3(srcH.r, srcH.g, srcH.b);

    // ------------------------------------------------------------------ 2. halation field
    // Tight fringe pyramid (tuned against the PREQUEL references): 8/28/64 · m.
    float blurScale = 0.5 + 1.0 * clamp(uBlur, 0.0, 1.0);
    float f1 = brightBlur5(fragCoord, max(8.0 * sc * blurScale, 2.0));
    float f2 = brightBlur9(fragCoord, max(28.0 * sc * blurScale, 4.0));
    float f3 = brightBlur9(fragCoord, max(64.0 * sc * blurScale, 8.0));

    // Edge gate: |luma - blur(luma)| isolates highlight boundaries. The ungated field washes
    // broad tinted rings across skin (seen on-device); PREQUEL's fringe hugs the highlight edge.
    float edgeR = max(16.0 * sc, 2.0);
    float edge = abs(lumaAt(fragCoord) - lumaBlur5(fragCoord, edgeR));
    float gate = smoothstep(0.09, 0.24, edge);

    // ------------------------------------------------------------------ 3. additive tinted halo
    // Piecewise hue mapping fitted from PREQUEL same-photo exports: 0 = red-orange,
    // 0.5 = cyan (#2), 0.7 = blue-violet (#3). Keeps Hue 0.50 cyan instead of purple.
    float h = clamp(uHue, 0.0, 1.0);
    float3 redOrange = float3(1.00, 0.20, 0.01);
    float3 cyan = float3(0.01, 0.79, 1.00);
    float3 blueViolet = float3(0.37, 0.27, 1.00);
    float3 violet = float3(0.60, 0.25, 1.00);
    float3 warmWhite = float3(1.00, 0.96, 0.86);
    float3 cyanWhite = float3(0.86, 0.95, 1.00);
    float3 blueWhite = float3(0.90, 0.94, 1.00);
    float3 violetWhite = float3(0.94, 0.90, 1.00);
    float3 outerCol;
    float3 innerCol;
    if (h <= 0.5) {
        outerCol = mix(redOrange, cyan, 2.0 * h);
        innerCol = mix(warmWhite, cyanWhite, 2.0 * h);
    } else if (h <= 0.7) {
        outerCol = mix(cyan, blueViolet, (h - 0.5) / 0.2);
        innerCol = mix(cyanWhite, blueWhite, (h - 0.5) / 0.2);
    } else {
        outerCol = mix(blueViolet, violet, (h - 0.7) / 0.3);
        innerCol = mix(blueWhite, violetWhite, (h - 0.7) / 0.3);
    }
    float field = f1 * 0.40 + f2 * 0.34 + f3 * 0.26;
    float core = smoothstep(0.90, 1.0, field) * 0.35;
    float3 haloCol = mix(outerCol, innerCol, core);
    float3 halo = haloCol * (field * gate);
    // Additive composite (never screen: screen desaturates the coloured fringe).
    // uHaloGain is the calibrated mapping from PREQUEL's Opacity control to our additive gain
    // (fitted per variant against the export fringe-energy percentiles); kept in sync with
    // CpuHalationRenderer and architecture-halation.md.
    // Protect only near-clipped whites (0.75..0.98 luma): the edge gate already keeps the halo
    // off skin, and PREQUEL's fringe is visible on the bright collar itself.
    float srcLuma = dot(clamp(src, 0.0, 1.0), float3(0.2126, 0.7152, 0.0722));
    float atten = 1.0 - smoothstep(0.75, 0.98, srcLuma);
    float3 c = src + halo * (uOpacity * uHaloGain) * atten;

    // ------------------------------------------------------------------ 4. warm haze screen
    if (uHaze > 0.001) {
        float3 haze = float3(0.843, 0.667, 0.502) * (uHaze * 0.6);
        c = 1.0 - (1.0 - c) * (1.0 - clamp(haze, 0.0, 1.0));
    }

    // ------------------------------------------------------------------ 5. base colour grade
    c += float3(
        uWarmth * 0.12 + uTint * -0.04,
        uWarmth * 0.04 + uTint * 0.06,
        uWarmth * -0.10 + uTint * -0.04
    );
    float lum = luma3(c);
    c = mix(float3(lum, lum, lum), c, 1.0 + uSaturation);
    c = (c - 0.5) * (1.0 + uContrast * 0.8) + 0.5;
    c = mix(c, 0.10 + c * 0.90, clamp(uFade, 0.0, 1.0));
    c = clamp(c, 0.0, 1.0);

    // ------------------------------------------------------------------ 6. film grain
    if (uGrain > 0.001) {
        float cell = max(1.2 * sc, 1.0);
        float gv = hashF(floor(fragCoord / cell), uSeed + 11.0);
        float gb = 0.5 + (gv - 0.5) * (uGrain * 0.06);
        c = float3(softLightFilm(c.r, gb), softLightFilm(c.g, gb), softLightFilm(c.b, gb));
        c = clamp(c, 0.0, 1.0);
        c = float3(softLightFilm(c.r, gb), softLightFilm(c.g, gb), softLightFilm(c.b, gb));
        c = clamp(c, 0.0, 1.0);
    }

    // ------------------------------------------------------------------ 7. vignette
    if (uVignette > 0.001) {
        float2 vd = (uv - 0.5) * float2(res.x / res.y, 1.0);
        c = c * (1.0 - uVignette * smoothstep(0.35, 0.95, length(vd) * 0.9));
    }

    // ------------------------------------------------------------------ 8. intensity blend
    float3 outc = mix(src, clamp(c, 0.0, 1.0), t);
    return half4(half(outc.x), half(outc.y), half(outc.z), srcH.a);
}
"""
