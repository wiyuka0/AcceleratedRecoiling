#include <iostream>
#include <chrono>
#include <algorithm>
#include <vector>

#include <omp.h>

#include <cstddef>
#include <cstring>
#include <string>
#include <immintrin.h>

#include <execution>

#include <new>      // for std::bad_alloc
#include <malloc.h> // for _mm_malloc on MSVC
#include <limits>

#ifdef _WIN32
#define EXPORT __declspec(dllexport)
#include <intrin.h> // for _BitScanForward, __popcnt
#else
#define EXPORT __attribute__((visibility("default")))
#endif

// template <typename T, std::size_t Alignment = 64>
// struct AlignedAllocator {
//     using value_type = T;
//     T* allocate(std::size_t n) {
//         if (n == 0) return nullptr;
//         void* ptr = _mm_malloc(n * sizeof(T), Alignment);
//         if (!ptr) throw std::bad_alloc();
//         return static_cast<T*>(ptr);
//     }
//     void deallocate(T* p, std::size_t) {
//         _mm_free(p);
//     }
// };

#if defined(_MSC_VER)
#include <intrin.h>
#endif

inline int TrailingZeroCount(int mask) {
#if defined(_MSC_VER)
    unsigned long index;
    _BitScanForward(&index, (unsigned long)mask);
    return (int)index;
#else
    // GCC / Clang
    return __builtin_ctz(mask);
#endif
}

template <typename T, std::size_t Alignment = 64>
class AlignedAllocator {
public:
    using value_type = T;
    using pointer = T*;
    using const_pointer = const T*;
    using reference = T&;
    using const_reference = const T&;
    using size_type = std::size_t;
    using difference_type = std::ptrdiff_t;
    template <typename U>
    struct rebind {
        using other = AlignedAllocator<U, Alignment>;
    };

    AlignedAllocator() noexcept {}
    
    template <typename U>
    AlignedAllocator(const AlignedAllocator<U, Alignment>&) noexcept {}

    T* allocate(std::size_t n) {
        if (n > std::numeric_limits<std::size_t>::max() / sizeof(T)) {
            throw std::bad_alloc();
        }
        if (auto p = static_cast<T*>(_mm_malloc(n * sizeof(T), Alignment))) {
            return p;
        }
        throw std::bad_alloc();
    }

    void deallocate(T* p, std::size_t) noexcept {
        _mm_free(p);
    }

    bool operator==(const AlignedAllocator&) const noexcept { return true; }
    bool operator!=(const AlignedAllocator&) const noexcept { return false; }
};

template<typename T>
using AlignedVector = std::vector<T, AlignedAllocator<T>>;

struct SAPNode
{
    int id;
    uint64_t sortKey;
};

std::vector<int> lastFrameIDs;

void ensureSize(int n) {
    if (lastFrameIDs.size() < n){
        lastFrameIDs.resize(n);
        for (int i = 0; i < n; ++i)
            lastFrameIDs[i] = i;
    }
}

inline void log(std::string str){
    // std::cout << str << "\n";
    // fflush(stdout);
}
static auto startTime = std::chrono::high_resolution_clock::now();
static auto endTime = std::chrono::high_resolution_clock::now();

void start(){startTime = std::chrono::high_resolution_clock::now();}
void stop(){endTime = std::chrono::high_resolution_clock::now();}
double duration(){
    double duration_microsecond = std::chrono::duration<double, std::milli>(endTime - startTime).count();
    return duration_microsecond;
}
void logTime(std::string str){
    // std::cout << str << ": " << duration() << "ms\n";
    log(str + ": " + std::to_string(duration()) + "ms\n");
}

#include <cstdint> 
#include <cmath>


const double WORLD_OFFSET = 50000000.0; 

const int BITS_X = 36;             
const int BITS_GRID = 64 - BITS_X; 

const uint64_t MASK_X = (1ULL << BITS_X) - 1;

struct CompressTable {
    alignas(64) int table[256][8];

    CompressTable() {
        for (int i = 0; i < 256; ++i) {
            int count = 0;
            for (int bit = 0; bit < 8; ++bit) {
                if ((i >> bit) & 1) {
                    table[i][count++] = bit;
                }
            }
            
            for (; count < 8; ++count) {
                table[i][count] = 0;
            }
        }
    }
};

static const CompressTable compressLUT;

static inline void radixSort64_OMP(SAPNode* src, std::vector<SAPNode>& buffer, std::vector<size_t>& histograms, int n)
{
    if (n <= 1) return;
    if ((int)buffer.size() < n) buffer.resize(n);
    SAPNode* dst = buffer.data();

    int max_threads = omp_get_max_threads();
    if ((int)histograms.size() < max_threads * 256) histograms.resize(max_threads * 256);

    #pragma omp parallel
    {
        int tid = omp_get_thread_num();
        int num_threads = omp_get_num_threads();

        int items_per_thread = (n + num_threads - 1) / num_threads;
        int start_idx = tid * items_per_thread;
        int end_idx = std::min(start_idx + items_per_thread, n);

        for (int pass = 0; pass < 8; ++pass)
        {
            int shift = pass * 8;
            std::memset(histograms.data() + tid * 256, 0, 256 * sizeof(size_t));

            #pragma omp barrier

            for (int i = start_idx; i < end_idx; ++i)
            {
                uint8_t byte = (src[i].sortKey >> shift) & 0xFF;
                histograms[tid * 256 + byte]++;
            }

            #pragma omp barrier

            #pragma omp single
            {
                size_t total_offset = 0;
                for (int b = 0; b < 256; ++b)
                {
                    for (int t = 0; t < num_threads; ++t)
                    {
                        size_t idx = t * 256 + b;
                        size_t count = histograms[idx];
                        histograms[idx] = total_offset;
                        total_offset += count;
                    }
                }
            }

            size_t local_offsets[256];
            for (int b = 0; b < 256; ++b) local_offsets[b] = histograms[tid * 256 + b];

            for (int i = start_idx; i < end_idx; ++i)
            {
                uint8_t byte = (src[i].sortKey >> shift) & 0xFF;
                size_t dest_idx = local_offsets[byte]++;
                dst[dest_idx] = src[i];
            }

            #pragma omp barrier
            #pragma omp single
            {
                std::swap(src, dst);
            }
        }
    }
}

struct EntityData
{

    AlignedVector<SAPNode> sortedList;
    AlignedVector<int> sortedMinX, sortedMaxX;
    AlignedVector<int> sortedMinY, sortedMaxY;
    AlignedVector<int> sortedMinZ, sortedMaxZ;
    AlignedVector<int> sortedOriginalIDs;
    AlignedVector<int> quantized;
    std::vector<SAPNode> sortBuffer;
    AlignedVector<int> runIndexPerItem;
    std::vector<int> runStarts;
    std::vector<size_t> sortHistograms;
    // std::vector<int> gridStarts(range + 2, entityCount);
    int currentSize = -1;

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
        }
    }
};


#define SCALE 64

// #error This library is not allow any bounding box's size bigger than 2 * gridSize, if you know what you doing, remove this #error

extern "C" EXPORT int push(const double *aabbs, int *outputA, int *outputB, int entityCount, int K, int gridSize)
{

    if (entityCount < 2 || aabbs == nullptr || outputA == nullptr || outputB == nullptr)
    {
        return 0;
    }

    
    
    ensureSize(entityCount);

    double invGridSize = 1.0 / ((double) gridSize);
    static thread_local EntityData memData;
    auto memDataPtr = &memData;
    memDataPtr->ensureSize(entityCount);


    SAPNode* __restrict sortedList = memData.sortedList.data();
    int* __restrict sortedMinX = memData.sortedMinX.data();
    int* __restrict sortedMaxX = memData.sortedMaxX.data();
    int* __restrict sortedMinY = memData.sortedMinY.data();
    int* __restrict sortedMaxY = memData.sortedMaxY.data();
    int* __restrict sortedMinZ = memData.sortedMinZ.data();
    int* __restrict sortedMaxZ = memData.sortedMaxZ.data();
    int* __restrict sortedOriginalIDs = memData.sortedOriginalIDs.data();
    int* __restrict quantizedData = memData.quantized.data();

    start();

    #pragma omp parallel for schedule(static)
    for (int i = 0; i < entityCount; ++i)
    {
        sortedList[i].id = i;
        sortedOriginalIDs[i] = i;

        double dMinX = aabbs[i * 6 + 0];
        double dMinY = aabbs[i * 6 + 1];
        double dMinZ = aabbs[i * 6 + 2];
        double dMaxX = aabbs[i * 6 + 3];
        double dMaxY = aabbs[i * 6 + 4];
        double dMaxZ = aabbs[i * 6 + 5];

        int64_t qX = (int64_t)((dMinX + WORLD_OFFSET) * SCALE);
        int64_t gridZ = (int64_t)((dMinZ + WORLD_OFFSET) * invGridSize);
        uint64_t key = 0;

        key |= ((uint64_t)gridZ) << BITS_X;
        key |= ((uint64_t)qX) & MASK_X;
        sortedList[i].sortKey = key;


        quantizedData[i * 6 + 0] = (int)(dMinX * SCALE);
        quantizedData[i * 6 + 1] = (int)(dMinY * SCALE);
        quantizedData[i * 6 + 2] = (int)(dMinZ * SCALE);
        quantizedData[i * 6 + 3] = (int)(dMaxX * SCALE);
        quantizedData[i * 6 + 4] = (int)(dMaxY * SCALE);
        quantizedData[i * 6 + 5] = (int)(dMaxZ * SCALE);
    }

    stop();
    logTime("Prepare Data");

    start();
    std::vector<SAPNode>& sortBuffer = memDataPtr->sortBuffer;
    radixSort64_OMP(sortedList, sortBuffer, memData.sortHistograms, entityCount);
    stop();

    logTime("Sort");

    start();
    int* __restrict runIndexPerItem = memData.runIndexPerItem.data();
    auto& runStarts = memData.runStarts;
    runStarts.clear();
    runStarts.reserve(1024);
    runStarts.push_back(0);
    int runIndex = 0;
    uint64_t currentGrid = (uint64_t)(sortedList[0].sortKey >> BITS_X);
    for (int i = 0; i < entityCount; ++i)
    {
        uint64_t g = (uint64_t)(sortedList[i].sortKey >> BITS_X);
        if (g != currentGrid)
        {
            runStarts.push_back(i);
            currentGrid = g;
            ++runIndex;
        }
        runIndexPerItem[i] = runIndex;
    }
    runStarts.push_back(entityCount);
    stop();
    logTime("Build Grid Runs");

    start();

#pragma omp parallel for schedule(static)
    for (int i = 0; i < entityCount; ++i)
    {
        int originalID = sortedList[i].id;
        sortedOriginalIDs[i] = originalID;

        int baseIdx = originalID * 6;
        sortedMinX[i] = quantizedData[baseIdx + 0];
        sortedMinY[i] = quantizedData[baseIdx + 1];
        sortedMinZ[i] = quantizedData[baseIdx + 2];
        sortedMaxX[i] = quantizedData[baseIdx + 3];
        sortedMaxY[i] = quantizedData[baseIdx + 4];
        sortedMaxZ[i] = quantizedData[baseIdx + 5];
    }

    stop();
    logTime("Copy Data to Linear Array");

    int collisionCount = 0;

    start();

    const int *__restrict pMinY = sortedMinY;
    const int *__restrict pMaxY = sortedMaxY;
    const int *__restrict pMinZ = sortedMinZ;
    const int *__restrict pMaxZ = sortedMaxZ;
    const int *__restrict pMaxX = sortedMaxX;
    const int *__restrict pMinX = sortedMinX;
    const int *__restrict pIDs = sortedOriginalIDs;

    #pragma omp parallel for schedule(guided, 64) reduction(+ : collisionCount)
    for (int i = 0; i < entityCount; ++i)
    {
        int idA = pIDs[i];
        int maxXA = pMaxX[i];
        int minYA = pMinY[i];
        int maxYA = pMaxY[i];
        int minZA = pMinZ[i];
        int maxZA = pMaxZ[i];
        int minXA = pMinX[i];

        __m256i vMaxXA = _mm256_set1_epi32(maxXA);
        __m256i vMinYA = _mm256_set1_epi32(minYA);
        __m256i vMaxYA = _mm256_set1_epi32(maxYA);
        __m256i vMinZA = _mm256_set1_epi32(minZA);
        __m256i vMaxZA = _mm256_set1_epi32(maxZA);

        __m256i vMinXA = _mm256_set1_epi32(minXA);
        int writeOffset = i * K;
        int currentCollisions = 0;

        int* __restrict outA = outputA + writeOffset;
        int* __restrict outB = outputB + writeOffset;

        auto processRange = [&](int start, int end)
        {
            int j = start;

            int alignedStart = (j + 7) & ~7;
            int scalarEnd = std::min(alignedStart, end);
            for (; j < scalarEnd; ++j)
            {
                if (pMinX[j] > maxXA) return;
                if (currentCollisions >= K) return;

                if (!(maxXA <= pMinX[j] || minXA >= pMaxX[j] ||
                      maxYA <= pMinY[j] || minYA >= pMaxY[j] ||
                      maxZA <= pMinZ[j] || minZA >= pMaxZ[j]))
                {
                    outA[currentCollisions] = idA;
                    outB[currentCollisions] = pIDs[j];
                    currentCollisions++;
                }
            }

            const __m256i allOnes = _mm256_set1_epi32(-1);

            for (; j < end - 7; j += 8)
            {
                __m256i vMinXB = _mm256_load_si256((const __m256i*)&pMinX[j]);
                __m256i vIsAllGreater = _mm256_cmpgt_epi32(vMinXB, vMaxXA);
                if (_mm256_movemask_ps(_mm256_castsi256_ps(vIsAllGreater)) == 0xFF) return;

                __m256i vMaxXB = _mm256_load_si256((const __m256i*)&pMaxX[j]);
                __m256i maskX = _mm256_and_si256(_mm256_xor_si256(vIsAllGreater, allOnes), _mm256_cmpgt_epi32(vMaxXB, vMinXA));
                if (_mm256_testz_si256(maskX, maskX)) continue;

                __m256i vMinYB = _mm256_load_si256((const __m256i*)&pMinY[j]);
                __m256i vMaxYB = _mm256_load_si256((const __m256i*)&pMaxY[j]);
                __m256i maskY = _mm256_and_si256(_mm256_cmpgt_epi32(vMaxYA, vMinYB), _mm256_cmpgt_epi32(vMaxYB, vMinYA));
                __m256i maskXY = _mm256_and_si256(maskX, maskY);
                if (_mm256_testz_si256(maskXY, maskXY)) continue;

                __m256i vMinZB = _mm256_load_si256((const __m256i*)&pMinZ[j]);
                __m256i vMaxZB = _mm256_load_si256((const __m256i*)&pMaxZ[j]);
                __m256i maskZ = _mm256_and_si256(_mm256_cmpgt_epi32(vMaxZA, vMinZB), _mm256_cmpgt_epi32(vMaxZB, vMinZA));
                __m256i maskXYZ = _mm256_and_si256(maskXY, maskZ);
                if (_mm256_testz_si256(maskXYZ, maskXYZ)) continue;

                int laneMask = _mm256_movemask_ps(_mm256_castsi256_ps(maskXYZ));
                if (laneMask != 0)
                {
                    uint8_t mask8 = (uint8_t)laneMask;

                    #ifdef _WIN32
                        int cnt = __popcnt((unsigned int) mask8);//__builtin_popcount((unsigned int)mask8);
                    #else 
                        int cnt = __builtin_popcount((unsigned int)mask8);
                    #endif
                    const int* bits = compressLUT.table[mask8];
                    int take = std::min(cnt, K - currentCollisions);
                    for (int t = 0; t < take; ++t)
                    {
                        int k = bits[t];
                        outA[currentCollisions] = idA;
                        outB[currentCollisions] = pIDs[j + k];
                        currentCollisions++;
                    }
                    if (currentCollisions >= K) return;
                }
            }

            for (; j < end; ++j)
            {
                if (pMinX[j] > maxXA) return;
                if (currentCollisions >= K) return;

                if (!(maxXA <= pMinX[j] || minXA >= pMaxX[j] ||
                      maxYA <= pMinY[j] || minYA >= pMaxY[j] ||
                      maxZA <= pMinZ[j] || minZA >= pMaxZ[j]))
                {
                    outA[currentCollisions] = idA;
                    outB[currentCollisions] = pIDs[j];
                    currentCollisions++;
                }
            }
        };

        int myRun = runIndexPerItem[i];
        int endOfMyGrid = runStarts[myRun + 1];
        // log("515");
        if (i + 1 < endOfMyGrid)
            processRange(i + 1, endOfMyGrid);
        if (currentCollisions < K)
        {
            if (myRun + 2 < (int)runStarts.size())
            {
                int startNext = runStarts[myRun + 1];
                int endNext = runStarts[myRun + 2];
                if (startNext < endNext)
                    processRange(startNext, endNext);
            }
        }
        collisionCount += currentCollisions;
    }
    stop();
    logTime("SAP");
    return collisionCount;
}
