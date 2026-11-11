package com.aliahad.photoeffects.effect

/**
 * GPU Daymark pipeline: soft focus, channel curves, saturation and gain, vignette,
 * grain and mottling, then intensity blending. The dot-matrix date is drawn
 * separately after the shader so it uses the shared DotMatrixDate geometry.
 */
internal const val DAYMARK_AGSL: String = """
uniform shader content;
uniform float2 uResolution;
uniform float uIntensity;
uniform float uSeed;
uniform float uBlurSigma;
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
uniform float uVigC0;
uniform float uVigC1;
uniform float uVigC2;
uniform float uGrainAmp;
uniform float uGrainCell;
uniform float uMottleAmp;
uniform float uMottleFreq;

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

    // ------------------------------------------------- 1. gaussian pre-blur
    float bsigma = uBlurSigma * sc;
    float3 b = src;
    if (bsigma > 0.05) {
        b = blur3x3(fragCoord, max(bsigma, 0.5));
    }

    // ------------------------------------------------- 2. per-channel cubic tone
    float3 c = float3(
        ((uToneR3 * b.r + uToneR2) * b.r + uToneR1) * b.r + uToneR0,
        ((uToneG3 * b.g + uToneG2) * b.g + uToneG1) * b.g + uToneG0,
        ((uToneB3 * b.b + uToneB2) * b.b + uToneB1) * b.b + uToneB0
    );

    // ------------------------------------------------- 3. saturation mix
    float gray = dot(c, float3(0.2126, 0.7152, 0.0722));
    c = mix(float3(gray, gray, gray), c, 1.0 - clamp(uSatMix, 0.0, 1.0));

    // ------------------------------------------------- 4. per-channel gain/offset
    c = float3(
        uGainR * c.r + uOffR,
        uGainG * c.g + uOffG,
        uGainB * c.b + uOffB
    );

    // ------------------------------------------------- 5. vignette
    float2 vd = (uv - 0.5) * 2.0;
    float r = length(vd);
    float vig = clamp(uVigC0 + uVigC1 * r + uVigC2 * r * r, 0.0, 2.0);
    c = c * vig;

    // ------------------------------------------------- 6. grain + low-frequency mottle
    float cell = max(uGrainCell * sc, 1.0);
    float gv = hashF(floor(fragCoord / cell), uSeed + 11.0);
    float grain = (gv - 0.5) * (uGrainAmp / 0.2887);
    float mottle = (fbm2(uv * uMottleFreq * 2.0, uSeed + 29.0) - 0.5) * (uMottleAmp / 0.2);
    c = clamp(c + (grain + mottle), 0.0, 1.0);

    // ------------------------------------------------- 7. intensity blend
    float3 outc = mix(src, c, t);
    float alpha = float(srcH.a);
    return half4(half(outc.x * alpha), half(outc.y * alpha), half(outc.z * alpha), srcH.a);
}
"""
