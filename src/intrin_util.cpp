#include "intrin_util.h"

#include <optional>

#ifdef AR_X64
#ifdef AR_WINDOWS
#include <intrin.h>
static void get_cpuid(int leaf, int subleaf, int out[4]) noexcept {
    __cpuidex(out, leaf, subleaf);
}
#else /* AR_WINDOWS */
#include <cpuid.h>
static void get_cpuid(int leaf, int subleaf, int out[4]) noexcept {
    __cpuid_count(leaf, subleaf, out[0], out[1], out[2], out[3]);
}
#endif /* AR_WINDOWS */
// #elif (defined AR_AARCH64)
// #include <sys/auxv.h>
// #include <asm/hwcap.h>
#endif

SimdType getSimdType() noexcept {
#ifdef AR_X64
    static std::optional<SimdType> supported;
    if (!supported) [[unlikely]] {
        int info[4];

        get_cpuid(1, 0, info);
        auto FMA3 = (info[2] & (1 << 12)) != 0;

        get_cpuid(7, 0, info);
        auto AVX2 = (info[1] & (1 << 5)) != 0;
        auto AVX512F = (info[1] & (1 << 16)) != 0;
        auto AVX512BW = (info[1] & (1 << 30)) != 0;
        auto AVX512VL = (info[1] & (1 << 31)) != 0;

        // if (AVX512F && AVX512BW && AVX512VL) {
        //     supported = SimdType::AVX512;
        // } else
        if (AVX2 && FMA3) {
            supported = SimdType::AVX2;
        } else {
            supported = SimdType::SSE41;
        }
    }
    return supported.value();
#else
    return SimdType::NEON;
#endif
}
