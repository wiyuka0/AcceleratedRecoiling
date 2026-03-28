#pragma once

#include <cstdint>

#ifdef AR_X64
#include <immintrin.h>

#define AR_PAUSE _mm_pause()
#define AR_TARGET_AVX512 __attribute__((target("avx512f,avx512vl,avx512bw,avx512dq")))
#define AR_TARGET_AVX2 __attribute__((target("avx2,fma")))

#elif defined(AR_AARCH64)
#include <arm_acle.h>
#include <arm_neon.h>

#define AR_PAUSE __isb(0xF)
#define AR_TARGET_SVE __attribute__((target("sve")))

#else
#error "Unsupported cpu arch"
#endif

enum class SimdType : uint16_t {
#ifdef AR_X64
    SSE41,
    AVX2,
    AVX512,
#elif defined(AR_AARCH64)
    NEON,
    // SVE128,
#endif
};

template <SimdType T>
struct SimdTag {
    constexpr static SimdType TYPE = T;
};

SimdType getSimdType() noexcept;
