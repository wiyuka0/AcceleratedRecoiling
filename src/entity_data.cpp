#include "entity_data.h"

#include <iostream>
#include <algorithm>
#include <vector>
#include <cstdint>
#include <execution>
#include <cmath>

#include <magic_enum/magic_enum_switch.hpp>

#include "statistics.h"
#include "aligned.h"
#include "config.h"
#include "compress_table.h"
#include "radix_sort.hpp"
#include "parallel_executor.h"
#include "intrin_util.h"
#include "noncopyable.h"

constexpr double WORLD_OFFSET = 50000000.0;

constexpr int BITS_X = 36;
constexpr int BITS_GRID = 64 - BITS_X;

constexpr uint64_t MASK_X = (1ULL << BITS_X) - 1;

constexpr int WINDOW = 4;
constexpr float EPSILON_DISTANCE = 0.1f;
constexpr float SCALE = 64;


struct EntityData : NonCopyable
{
    AlignedVector<SAPNode> sortedList;
    AlignedVector<int> sortedMinX, sortedMaxX;
    AlignedVector<int> sortedMinY, sortedMaxY;
    AlignedVector<int> sortedMinZ, sortedMaxZ;
    AlignedVector<int> sortedOriginalIDs;

    AlignedVector<int> quantized;
    AlignedVector<int> runIndexPerItem;
    std::vector<int> runStarts;

    SortBuffer sortBuffer;

    // 【新增】用于记录每个 entity 实际写入了多少个碰撞结果
    AlignedVector<int> collisionCounts;

    int currentSize = -1;

    ParallelExecutor executor;

    void ensureSize(int n)
    {
        if (currentSize < n)
        {
            if (currentSize == -1)
            {
                sortedList = AlignedVector<SAPNode>(n);
                sortedMinX = AlignedVector<int>(n);
                sortedMinY = AlignedVector<int>(n);
                sortedMinZ = AlignedVector<int>(n);
                sortedMaxX = AlignedVector<int>(n);
                sortedMaxY = AlignedVector<int>(n);
                sortedMaxZ = AlignedVector<int>(n);
                sortedOriginalIDs = AlignedVector<int>(n);
                quantized = AlignedVector<int>(n * 6);
                runIndexPerItem = AlignedVector<int>(n);
                collisionCounts = AlignedVector<int>(n); // 【新增】
            }
            currentSize = n;
            sortedList.resize(n);
            sortedMinX.resize(n);
            sortedMaxX.resize(n);
            sortedMinY.resize(n);
            sortedMaxY.resize(n);
            sortedMinZ.resize(n);
            sortedMaxZ.resize(n);
            sortedOriginalIDs.resize(n);
            quantized.resize(n * 6);
            runIndexPerItem.resize(n);
            collisionCounts.resize(n); // 【新增】
        }
    }
};

static std::vector<int> lastFrameIDs;

static void ensureLastFrameSize(int n) {
    if (lastFrameIDs.size() < n){
        lastFrameIDs.resize(n);
        for (int i = 0; i < n; ++i)
            lastFrameIDs[i] = i;
    }
}

// #error This library is not allow any bounding box's size bigger than 2 * gridSize, if you know what you are doing, remove this #error

void* createCtx() {
    return new EntityData();
}

void destroyCtx(void* context_ptr) {
    if(context_ptr) {
        delete static_cast<EntityData*>(context_ptr);
    }
}

#ifdef AR_X64

#define AR_SIMD_TYPE AVX512
#define AR_SIMD_TARGET AR_TARGET_AVX512
#include "entity_data_push_avx512.inc"
#include "entity_data_push.inc"
#undef AR_SIMD_TARGET
#undef AR_SIMD_TYPE

#define AR_SIMD_TYPE AVX2
#define AR_SIMD_TARGET AR_TARGET_AVX2
#include "entity_data_push_avx2.inc"
#include "entity_data_push.inc"
#undef AR_SIMD_TARGET
#undef AR_SIMD_TYPE

#define AR_SIMD_TYPE SSE41
#define AR_SIMD_TARGET
#include "entity_data_push_sse41.inc"
#include "entity_data_push.inc"
#undef AR_SIMD_TARGET
#undef AR_SIMD_TYPE

#elif AR_AARCH64

#define AR_SIMD_TYPE NEON
#define AR_SIMD_TARGET
#include "entity_data_push_neon.inc"
#include "entity_data_push.inc"
#undef AR_SIMD_TARGET
#undef AR_SIMD_TYPE

#endif /* AR_X64 */

int push(const double *aabbs, int *outputA, int *outputB, int entityCount, float* densityBuf, void* memDataPtrOri, void* configPtr) {
    return magic_enum::enum_switch([&](auto type) {
        return pushImpl(SimdTag<type>{}, aabbs, outputA, outputB, entityCount, densityBuf, memDataPtrOri, configPtr);
    }, getSimdType());
}
