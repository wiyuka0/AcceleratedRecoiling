#pragma once

#include <cstdint>
#include <span>
#include <vector>

#include "parallel_executor.h"

struct SAPNode
{
    int id;
    uint64_t sortKey;
};

struct SortBuffer {
    std::vector<SAPNode> buffer;
    std::vector<size_t> histograms;
};

void radixSort64(std::span<SAPNode> src, SortBuffer& sortBuffer, ParallelExecutor& executor);