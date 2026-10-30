#include <jni.h>

#include <cstdint>
#include <vector>

namespace {

constexpr int clampIndex(int value, int maxValue) {
    return value < 0 ? 0 : (value > maxValue ? maxValue : value);
}

/**
 * O(n) box blur over one float plane, matching BoxBlur.blurFloatFast:
 * horizontal prefix sums over a clamped-extended row, then the same vertically.
 */
bool blurFloatFast(
    JNIEnv* env,
    jfloatArray srcArray,
    jfloatArray dstArray,
    jint width,
    jint height,
    jint radius
) {
    if (width <= 0 || height <= 0 || radius < 1) return false;
    const jsize count = static_cast<jsize>(width) * height;
    if (env->GetArrayLength(srcArray) < count || env->GetArrayLength(dstArray) < count) {
        return false;
    }

    jfloat* src = env->GetFloatArrayElements(srcArray, nullptr);
    if (src == nullptr) return false;
    std::vector<float> tmp(static_cast<size_t>(count));
    std::vector<float> out(static_cast<size_t>(count));

    const int window = 2 * radius + 1;
    const float inv = 1.0f / static_cast<float>(window);
    const int extW = width + 2 * radius;
    std::vector<float> prefix(static_cast<size_t>(extW) + 1);

    for (int y = 0; y < height; ++y) {
        const int row = y * width;
        prefix[0] = 0.0f;
        for (int x = 0; x < extW; ++x) {
            const int sx = clampIndex(x - radius, width - 1);
            prefix[x + 1] = prefix[x] + src[row + sx];
        }
        for (int x = 0; x < width; ++x) {
            tmp[row + x] = (prefix[x + window] - prefix[x]) * inv;
        }
    }

    const int extH = height + 2 * radius;
    std::vector<float> prefixC(static_cast<size_t>(extH) + 1);
    for (int x = 0; x < width; ++x) {
        prefixC[0] = 0.0f;
        for (int y = 0; y < extH; ++y) {
            const int sy = clampIndex(y - radius, height - 1);
            prefixC[y + 1] = prefixC[y] + tmp[sy * width + x];
        }
        for (int y = 0; y < height; ++y) {
            out[y * width + x] = (prefixC[y + window] - prefixC[y]) * inv;
        }
    }

    env->ReleaseFloatArrayElements(srcArray, src, JNI_ABORT);
    env->SetFloatArrayRegion(dstArray, 0, count, out.data());
    return true;
}

/** Integer box blur matching BoxBlur.blurArgb's channel order and integer division. */
bool blurArgb(
    JNIEnv* env,
    jintArray srcArray,
    jintArray dstArray,
    jint width,
    jint height,
    jint radius
) {
    if (width <= 0 || height <= 0 || radius < 1) return false;
    const jsize count = static_cast<jsize>(width) * height;
    if (env->GetArrayLength(srcArray) < count || env->GetArrayLength(dstArray) < count) {
        return false;
    }

    jint* src = env->GetIntArrayElements(srcArray, nullptr);
    if (src == nullptr) return false;
    std::vector<int32_t> tmp(static_cast<size_t>(count));
    std::vector<int32_t> out(static_cast<size_t>(count));

    const int window = 2 * radius + 1;
    for (int y = 0; y < height; ++y) {
        const int row = y * width;
        for (int x = 0; x < width; ++x) {
            int r = 0;
            int g = 0;
            int b = 0;
            int a = 0;
            for (int k = -radius; k <= radius; ++k) {
                const int sx = clampIndex(x + k, width - 1);
                const uint32_t p = static_cast<uint32_t>(src[row + sx]);
                r += static_cast<int>((p >> 16) & 0xFFu);
                g += static_cast<int>((p >> 8) & 0xFFu);
                b += static_cast<int>(p & 0xFFu);
                a += static_cast<int>((p >> 24) & 0xFFu);
            }
            tmp[row + x] = static_cast<int32_t>(
                (static_cast<uint32_t>(a / window) << 24) |
                (static_cast<uint32_t>(r / window) << 16) |
                (static_cast<uint32_t>(g / window) << 8) |
                static_cast<uint32_t>(b / window));
        }
    }

    for (int x = 0; x < width; ++x) {
        for (int y = 0; y < height; ++y) {
            int r = 0;
            int g = 0;
            int b = 0;
            int a = 0;
            for (int k = -radius; k <= radius; ++k) {
                const int sy = clampIndex(y + k, height - 1);
                const uint32_t p = static_cast<uint32_t>(tmp[sy * width + x]);
                r += static_cast<int>((p >> 16) & 0xFFu);
                g += static_cast<int>((p >> 8) & 0xFFu);
                b += static_cast<int>(p & 0xFFu);
                a += static_cast<int>((p >> 24) & 0xFFu);
            }
            out[y * width + x] = static_cast<int32_t>(
                (static_cast<uint32_t>(a / window) << 24) |
                (static_cast<uint32_t>(r / window) << 16) |
                (static_cast<uint32_t>(g / window) << 8) |
                static_cast<uint32_t>(b / window));
        }
    }

    env->ReleaseIntArrayElements(srcArray, src, JNI_ABORT);
    env->SetIntArrayRegion(dstArray, 0, count, out.data());
    return true;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aliahad_photoeffects_effect_cpu_NativeBlur_blurFloatFast(
    JNIEnv* env,
    jclass,
    jfloatArray src,
    jfloatArray dst,
    jint width,
    jint height,
    jint radius
) {
    return blurFloatFast(env, src, dst, width, height, radius) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aliahad_photoeffects_effect_cpu_NativeBlur_blurArgb(
    JNIEnv* env,
    jclass,
    jintArray src,
    jintArray dst,
    jint width,
    jint height,
    jint radius
) {
    return blurArgb(env, src, dst, width, height, radius) ? JNI_TRUE : JNI_FALSE;
}
