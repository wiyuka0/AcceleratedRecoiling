
#ifndef AR_SIMD_TYPE
#error "AR_SIMD_TYPE not set"
#endif // AR_SIMD_TYPE

AR_SIMD_TARGET
static int pushImpl(const SimdTag<SimdType::AR_SIMD_TYPE> simdTag, const double *aabbs, int *outputA, int *outputB, int entityCount, float* densityBuf, void* memDataPtrOri, void* configPtr) {
    if (entityCount < 2 || aabbs == nullptr || outputA == nullptr || outputB == nullptr || configPtr == nullptr || memDataPtrOri == nullptr)
    {
        return 0;
    }

    auto configStructPtr = static_cast<Config *>(configPtr);
    auto memDataPtr = static_cast<EntityData *>(memDataPtrOri);

    if (configStructPtr->maxThreads != memDataPtr->executor.getThreadCount())
    {
        memDataPtr->executor.resize(configStructPtr->maxThreads);
    }
    auto activeScope = memDataPtr->executor.beginActiveScope();

    int K = configStructPtr->maxCollision, gridSize = configStructPtr->gridSize;

    ensureLastFrameSize(entityCount);

    double invGridSize = 1.0 / static_cast<double>(gridSize);
    // static thread_local EntityData memData;
    memDataPtr->ensureSize(entityCount);

    SAPNode* __restrict sortedList = memDataPtr->sortedList.data();
    int* __restrict sortedMinX = memDataPtr->sortedMinX.data();
    int* __restrict sortedMaxX = memDataPtr->sortedMaxX.data();
    int* __restrict sortedMinY = memDataPtr->sortedMinY.data();
    int* __restrict sortedMaxY = memDataPtr->sortedMaxY.data();
    int* __restrict sortedMinZ = memDataPtr->sortedMinZ.data();
    int* __restrict sortedMaxZ = memDataPtr->sortedMaxZ.data();
    int* __restrict sortedOriginalIDs = memDataPtr->sortedOriginalIDs.data();
    int* __restrict quantizedData = memDataPtr->quantized.data();

    start();

    memDataPtr->executor.executeInBlock(0, entityCount, [&](auto begin, auto end) AR_SIMD_TARGET {
        for (int i = begin; i < end; ++i) {
            sortedList[i].id = i;
            sortedOriginalIDs[i] = i;

            double dMinX = aabbs[i * 6 + 0];
            double dMinY = aabbs[i * 6 + 1];
            double dMinZ = aabbs[i * 6 + 2];
            double dMaxX = aabbs[i * 6 + 3];
            double dMaxY = aabbs[i * 6 + 4];
            double dMaxZ = aabbs[i * 6 + 5];

            auto qX = static_cast<int64_t>((dMinX + WORLD_OFFSET) * SCALE);
            auto gridZ = static_cast<int64_t>((dMinZ + WORLD_OFFSET) * invGridSize);
            uint64_t key = 0;

            key |= static_cast<uint64_t>(gridZ) << BITS_X;
            key |= static_cast<uint64_t>(qX) & MASK_X;
            sortedList[i].sortKey = key;

            quantizedData[i * 6 + 0] = static_cast<int>(dMinX * SCALE);
            quantizedData[i * 6 + 1] = static_cast<int>(dMinY * SCALE);
            quantizedData[i * 6 + 2] = static_cast<int>(dMinZ * SCALE);
            quantizedData[i * 6 + 3] = static_cast<int>(dMaxX * SCALE);
            quantizedData[i * 6 + 4] = static_cast<int>(dMaxY * SCALE);
            quantizedData[i * 6 + 5] = static_cast<int>(dMaxZ * SCALE);
        }
    });

    stop();
    logTime("Prepare Data");

    start();
    radixSort64({sortedList, static_cast<size_t>(entityCount)}, memDataPtr->sortBuffer, memDataPtr->executor);
    stop();

    logTime("Sort");

    start();
    int* __restrict runIndexPerItem = memDataPtr->runIndexPerItem.data();
    auto& runStarts = memDataPtr->runStarts;
    runStarts.clear();
    runStarts.reserve(1024);
    runStarts.push_back(0);
    int runIndex = 0;
    uint64_t currentGrid = sortedList[0].sortKey >> BITS_X;
    for (int i = 0; i < entityCount; ++i)
    {
        uint64_t g = sortedList[i].sortKey >> BITS_X;
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

    memDataPtr->executor.executeInBlock(0, entityCount, [&](auto begin, auto end) AR_SIMD_TARGET {
        for (int i = begin; i < end; ++i)
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
    });

    stop();
    logTime("Copy Data to Linear Array");

    start();
    const int *__restrict pMinX = sortedMinX;
    const int *__restrict pIDs = sortedOriginalIDs;

    int* __restrict counts = memDataPtr->collisionCounts.data();

    if(densityBuf) {
        start();
        float* pDensity = densityBuf;
        memDataPtr->executor.executeInBlock(0, runStarts.size() - 1, [&](auto begin, auto end) AR_SIMD_TARGET {
            for (int grid = begin; grid < end; grid++)
            {
                int startIdx = runStarts[grid];
                int endIdx = runStarts[grid + 1];
                for (int i = startIdx; i < endIdx; ++i)
                {
                    int left = std::max(startIdx, i - WINDOW);
                    int right = std::min(endIdx - 1, i + WINDOW);

                    int count = right - left + 1;

                    if (count <= 1) {
                        pDensity[pIDs[i]] = 0.0f;
                        continue;
                    }

                    int dx_quantized = pMinX[right] - pMinX[left];

                    float dx_real = static_cast<float>(dx_quantized) / static_cast<float>(SCALE);

                    float localDensity = static_cast<float>(count) / (dx_real + EPSILON_DISTANCE);

                    pDensity[pIDs[i]] = localDensity;
                }
            }
        });
        stop();
        logTime("Density Estimation");
    }

    std::atomic_int collisionCount = 0;
    auto batchCount = (entityCount + 64 - 1) / 64;
    memDataPtr->executor.executeInBlock(0, batchCount, [&](auto batchBegin, auto batchEnd) AR_SIMD_TARGET {
        auto batchResult = push_compute(simdTag, batchBegin, batchEnd, entityCount, *memDataPtr, outputA, outputB, K);
        collisionCount.fetch_add(batchResult, std::memory_order_release);
    });

    stop();
    logTime("SAP");

    activeScope.reset();

    start();
    int currentOffset = 0;
    for (int i = 0; i < entityCount; ++i)
    {
        int count = counts[i];
        if (count > 0)
        {
            int srcOffset = i * K;
            int dstOffset = currentOffset;

            if (srcOffset != dstOffset)
            {
                for (int c = 0; c < count; ++c)
                {
                    outputA[dstOffset + c] = outputA[srcOffset + c];
                    outputB[dstOffset + c] = outputB[srcOffset + c];
                }
            }
            currentOffset += count;
        }
    }
    stop();
    logTime("Compaction");
    return collisionCount.load(std::memory_order_acquire);
}