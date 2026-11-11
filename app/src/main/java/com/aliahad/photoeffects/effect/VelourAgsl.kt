package com.aliahad.photoeffects.effect

/**
 * GPU portrait-softening pipeline shared by all Velour presets. A global soft-focus
 * pass feeds masked variable blur, channel curves, saturation and gain, grain and
 * intensity blending. uMask samples the bundled radial blur mask. Keep the taps
 * and weights aligned with CpuVelourRenderer.
 */
internal const val VELOUR_AGSL: String = """
uniform shader content;
uniform shader uMask;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uSigma;
uniform float uBlurRadius;
uniform float uToneR3;
uniform float uToneR2;
uniform float uToneR1;
uniform float uToneR0;
uniform float uToneG3;
uniform float uToneG2;
uniform float uToneG1;
uniform float uToneG0;
uniform float uToneB3;
uniform float uToneB2;
uniform float uToneB1;
uniform float uToneB0;
uniform float uSatMix;
uniform float uGainR;
uniform float uOffR;
uniform float uGainG;
uniform float uOffG;
uniform float uGainB;
uniform float uOffB;
uniform float uGrainAmp;
uniform float uGrainCell;
uniform float uMottleAmp;
uniform float uMottleFreq;
uniform float uMottleAmp2;
uniform float uMottleFreq2;

// RuntimeShader child colors are premultiplied; effect math uses straight RGB like the CPU.
float3 sourceStraight(half4 h) {
    return h.a > 0.0 ? float3(float(h.r), float(h.g), float(h.b)) / float(h.a)
        : float3(0.0);
}

float fract1(float x) { return x - floor(x); }

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

float3 sampleRgb(float2 p) {
    half4 h = content.eval(p);
    return sourceStraight(h);
}

// 3x3 binomial (approximate gaussian) with the given sample spacing.
float3 blur3x3(float2 p, float s) {
    float3 acc = float3(0.0);
    acc += sampleRgb(p + float2(-s, -s)) * 0.0625;
    acc += sampleRgb(p + float2(0.0, -s)) * 0.125;
    acc += sampleRgb(p + float2(s, -s)) * 0.0625;
    acc += sampleRgb(p + float2(-s, 0.0)) * 0.125;
    acc += sampleRgb(p) * 0.25;
    acc += sampleRgb(p + float2(s, 0.0)) * 0.125;
    acc += sampleRgb(p + float2(-s, s)) * 0.0625;
    acc += sampleRgb(p + float2(0.0, s)) * 0.125;
    acc += sampleRgb(p + float2(s, s)) * 0.0625;
    return acc;
}

half4 main(float2 fragCoord) {
    float t = clamp(uIntensity, 0.0, 1.0);
    half4 srcH = content.eval(fragCoord);
    if (t <= 0.0) {
        return srcH;
    }

    float2 res = max(uResolution, float2(1.0, 1.0));
    float minDim = min(res.x, res.y);
    float sc = minDim / 1080.0;
    float2 uv = fragCoord / res;
    float3 src = sourceStraight(srcH);

    // ------------------------------------------------- 1. small global soft-focus gaussian
    float sigma = uSigma * sc;
    float3 b1 = src;
    if (sigma > 0.05) {
        b1 = blur3x3(fragCoord, max(sigma, 0.5));
    }

    // ------------------------------------------------- 2. masked variable blur (adj-Blur recipe)
    float3 b = b1;
    if (uBlurRadius > 0.05) {
        float rPx = uBlurRadius * sc;
        float total = sqrt(sigma * sigma + rPx * rPx);
        float3 b2 = blur3x3(fragCoord, max(total, 0.5));
        float mask = clamp(uMask.eval(fragCoord).r, 0.0, 1.0);
        b = mix(b1, b2, mask);
    }

    // ------------------------------------------------- 3. per-channel cubic tone curves
    float3 c = float3(
        ((uToneR3 * b.r + uToneR2) * b.r + uToneR1) * b.r + uToneR0,
        ((uToneG3 * b.g + uToneG2) * b.g + uToneG1) * b.g + uToneG0,
        ((uToneB3 * b.b + uToneB2) * b.b + uToneB1) * b.b + uToneB0
    );

    // ------------------------------------------------- 4. saturation mix + gain/offset
    float gray = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(gray, gray, gray), c, 1.0 - clamp(uSatMix, 0.0, 1.0));
    c = float3(
        uGainR * c.r + uOffR,
        uGainG * c.g + uOffG,
        uGainB * c.b + uOffB
    );

    // ------------------------------------------------- 5a. low-frequency Texture mottling
    // Two luma-neutral octaves (mean k = 1), fitted from fine film texture.
    float mottle1 = (fbm2(fragCoord / minDim * uMottleFreq, uSeed + 53.0) - 0.5) * 2.0 * uMottleAmp;
    float mottle2 = (fbm2(fragCoord / minDim * uMottleFreq2, uSeed + 71.0) - 0.5) * 2.0 * uMottleAmp2;
    c = c * clamp(1.0 + mottle1 + mottle2, 0.8, 1.2);

    // ------------------------------------------------- 5. monochrome film grain
    float cell = max(uGrainCell * sc, 1.0);
    float gv = hashF(floor(fragCoord / cell), uSeed + 37.0);
    float grain = (gv - 0.5) * (uGrainAmp / 0.2887);
    c = clamp(c + grain, 0.0, 1.0);

    // ------------------------------------------------- 6. intensity blend
    float3 outc = mix(src, c, t);
    float alpha = float(srcH.a);
    return half4(half(outc.x * alpha), half(outc.y * alpha), half(outc.z * alpha), srcH.a);
}
"""
