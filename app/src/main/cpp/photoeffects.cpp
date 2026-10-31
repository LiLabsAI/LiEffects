#include <jni.h>

#include <cstdint>
#include <limits>
#include <memory>
#include <new>

namespace {

constexpr int clampIndex(int value, int maxValue) {
    return value < 0 ? 0 : (value > maxValue ? maxValue : value);
}

// Return native allocation failures to the Kotlin reference, including with -fno-exceptions.
// Source/destination JNI elements are acquired in separate phases: at most one JNI frame copy
// is live alongside the single full-frame temporary, rather than two native output planes.
template <typename T>
std::unique_ptr<T[]> allocate(size_t count) {
    return std::unique_ptr<T[]>(new (std::nothrow) T[count]);
}

bool validSize(JNIEnv* env, jarray src, jarray dst, int width, int height, int radius) {
    if (!src || !dst || width <= 0 || height <= 0 || radius < 1) return false;
    const int64_t count = static_cast<int64_t>(width) * height;
    const int64_t extension = static_cast<int64_t>(radius) * 2;
    const int64_t maximum = std::numeric_limits<jsize>::max();
    return count <= maximum && width + extension < maximum && height + extension < maximum &&
           env->GetArrayLength(src) >= count && env->GetArrayLength(dst) >= count;
}

class Cancellation {
public:
    Cancellation(JNIEnv* env, jobject predicate) : env_(env), predicate_(predicate) {
        if (predicate_) {
            jclass type = env_->GetObjectClass(predicate_);
            if (type) {
                method_ = env_->GetMethodID(type, "getAsBoolean", "()Z");
                env_->DeleteLocalRef(type);
            }
        }
    }

    bool stopped() const {
        if (env_->ExceptionCheck()) return true;
        if (!predicate_) return false;
        const bool cancelled = env_->CallBooleanMethod(predicate_, method_) == JNI_TRUE;
        if (env_->ExceptionCheck()) return true;
        if (cancelled) {
            jclass type = env_->FindClass("java/util/concurrent/CancellationException");
            if (type) {
                env_->ThrowNew(type, "CPU render cancelled");
                env_->DeleteLocalRef(type);
            }
        }
        return cancelled;
    }

private:
    JNIEnv* env_;
    jobject predicate_;
    jmethodID method_ = nullptr;
};

class FloatElements {
private:
    JNIEnv* env_;
    jfloatArray array_;
    jint mode_;
public:
    FloatElements(JNIEnv* env, jfloatArray array, jint mode)
        : env_(env), array_(array), mode_(mode), data(env->GetFloatArrayElements(array, nullptr)) {}
    ~FloatElements() { if (data) env_->ReleaseFloatArrayElements(array_, data, mode_); }
    jfloat* data;
};

class IntElements {
private:
    JNIEnv* env_;
    jintArray array_;
    jint mode_;
public:
    IntElements(JNIEnv* env, jintArray array, jint mode)
        : env_(env), array_(array), mode_(mode), data(env->GetIntArrayElements(array, nullptr)) {}
    ~IntElements() { if (data) env_->ReleaseIntArrayElements(array_, data, mode_); }
    jint* data;
};

/** Prefix-sum order, float division and clamped edge replication match the Kotlin reference. */
bool blurFloatFast(JNIEnv* env, jfloatArray srcArray, jfloatArray dstArray,
                   jint width, jint height, jint radius, jobject predicate) {
    if (!validSize(env, srcArray, dstArray, width, height, radius)) return false;
    Cancellation cancel(env, predicate);
    if (cancel.stopped()) return false;
    const size_t count = static_cast<size_t>(width) * height;
    const int window = 2 * radius + 1;
    const float inv = 1.0f / static_cast<float>(window);
    const int extW = width + 2 * radius;
    const int extH = height + 2 * radius;
    auto tmp = allocate<float>(count);
    auto prefix = allocate<float>(static_cast<size_t>(extW > extH ? extW : extH) + 1);
    if (!tmp || !prefix) return false;

    {
        FloatElements src(env, srcArray, JNI_ABORT);
        if (!src.data) return false;
        for (int y = 0; y < height; ++y) {
            if ((y & 15) == 0 && cancel.stopped()) return false;
            const int row = y * width;
            prefix[0] = 0.0f;
            for (int x = 0; x < extW; ++x) {
                prefix[x + 1] = prefix[x] + src.data[row + clampIndex(x - radius, width - 1)];
            }
            for (int x = 0; x < width; ++x) {
                tmp[row + x] = (prefix[x + window] - prefix[x]) * inv;
            }
        }
    }
    {
        FloatElements dst(env, dstArray, 0);
        if (!dst.data) return false;
        for (int x = 0; x < width; ++x) {
            if ((x & 15) == 0 && cancel.stopped()) return false;
            prefix[0] = 0.0f;
            for (int y = 0; y < extH; ++y) {
                prefix[y + 1] = prefix[y] + tmp[clampIndex(y - radius, height - 1) * width + x];
            }
            for (int y = 0; y < height; ++y) {
                dst.data[y * width + x] = (prefix[y + window] - prefix[y]) * inv;
            }
        }
    }
    return !cancel.stopped();
}

constexpr uint32_t visiblePixel(uint32_t pixel) {
    return (pixel >> 24) == 0 ? 0 : pixel;
}

struct ChannelSum {
    int64_t r = 0, g = 0, b = 0, a = 0;

    void add(uint32_t pixel, int sign = 1) {
        r += static_cast<int64_t>((pixel >> 16) & 0xFFu) * sign;
        g += static_cast<int64_t>((pixel >> 8) & 0xFFu) * sign;
        b += static_cast<int64_t>(pixel & 0xFFu) * sign;
        a += static_cast<int64_t>((pixel >> 24) & 0xFFu) * sign;
    }

    int32_t packed(int window) const {
        return static_cast<int32_t>((static_cast<uint32_t>(a / window) << 24) |
            (static_cast<uint32_t>(r / window) << 16) |
            (static_cast<uint32_t>(g / window) << 8) | static_cast<uint32_t>(b / window));
    }
};

/** Running integer sums preserve the reference division exactly without rescanning each window. */
bool blurArgb(JNIEnv* env, jintArray srcArray, jintArray dstArray,
              jint width, jint height, jint radius, jobject predicate) {
    if (!validSize(env, srcArray, dstArray, width, height, radius)) return false;
    Cancellation cancel(env, predicate);
    if (cancel.stopped()) return false;
    const size_t count = static_cast<size_t>(width) * height;
    auto tmp = allocate<int32_t>(count);
    if (!tmp) return false;
    const int window = 2 * radius + 1;

    {
        IntElements src(env, srcArray, JNI_ABORT);
        if (!src.data) return false;
        for (int y = 0; y < height; ++y) {
            if ((y & 15) == 0 && cancel.stopped()) return false;
            const int row = y * width;
            ChannelSum sum;
            for (int k = -radius; k <= radius; ++k) {
                sum.add(visiblePixel(static_cast<uint32_t>(src.data[row + clampIndex(k, width - 1)])));
            }
            for (int x = 0; x < width; ++x) {
                tmp[row + x] = sum.packed(window);
                if (x + 1 < width) {
                    sum.add(visiblePixel(static_cast<uint32_t>(src.data[row + clampIndex(x - radius, width - 1)])), -1);
                    sum.add(visiblePixel(static_cast<uint32_t>(src.data[row + clampIndex(x + radius + 1, width - 1)])));
                }
            }
        }
    }
    {
        IntElements dst(env, dstArray, 0);
        if (!dst.data) return false;
        for (int x = 0; x < width; ++x) {
            if ((x & 15) == 0 && cancel.stopped()) return false;
            ChannelSum sum;
            for (int k = -radius; k <= radius; ++k) {
                sum.add(static_cast<uint32_t>(tmp[clampIndex(k, height - 1) * width + x]));
            }
            for (int y = 0; y < height; ++y) {
                dst.data[y * width + x] = sum.packed(window);
                if (y + 1 < height) {
                    sum.add(static_cast<uint32_t>(tmp[clampIndex(y - radius, height - 1) * width + x]), -1);
                    sum.add(static_cast<uint32_t>(tmp[clampIndex(y + radius + 1, height - 1) * width + x]));
                }
            }
        }
    }
    return !cancel.stopped();
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aliahad_photoeffects_effect_cpu_NativeBlur_blurFloatFastNative(
    JNIEnv* env, jclass, jfloatArray src, jfloatArray dst,
    jint width, jint height, jint radius, jobject cancel) {
    return blurFloatFast(env, src, dst, width, height, radius, cancel) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aliahad_photoeffects_effect_cpu_NativeBlur_blurArgbNative(
    JNIEnv* env, jclass, jintArray src, jintArray dst,
    jint width, jint height, jint radius, jobject cancel) {
    return blurArgb(env, src, dst, width, height, radius, cancel) ? JNI_TRUE : JNI_FALSE;
}
