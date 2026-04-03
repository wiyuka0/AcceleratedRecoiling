#include "radix_sort.hpp"

#include <barrier>
#include <cstring>
#include <array>

#include "parallel_executor.h"

void radixSort64(std::span<SAPNode> src, SortBuffer& sortBuffer, ParallelExecutor& executor) {
    if (src.size() <= 1) return;
    if (src.size() <= 256) {
        if (src.size() > 1) std::sort(src.begin(), src.end(), [](auto& a, auto& b){ return a.sortKey < b.sortKey; });
        return;
    }
    auto& buffer = sortBuffer.buffer;
    auto& histograms = sortBuffer.histograms;

    if (buffer.size() < src.size()) buffer.resize(src.size());
    std::span dst = buffer;

    const auto num_threads = executor.getThreadCount();
    if (histograms.size() < num_threads * 256) histograms.resize(num_threads * 256);

    std::barrier barrier(static_cast<ptrdiff_t>(num_threads));
    std::atomic_int flag0(0);
    std::atomic_int flag1(0);

    executor.executeInBlock(0, src.size(), [&](auto start_idx, auto end_idx, auto tid) {
        for (int pass = 0; pass < 8; ++pass)
        {
            int shift = pass * 8;
            std::memset(histograms.data() + tid * 256, 0, 256 * sizeof(size_t));
            barrier.arrive_and_wait();

            for (int i = start_idx; i < end_idx; ++i)
            {
                uint8_t byte = (src[i].sortKey >> shift) & 0xFF;
                ++histograms[tid * 256 + byte];
            }

            barrier.arrive_and_wait();
            if (auto v = pass; flag0.compare_exchange_strong(v, v + 1,
                std::memory_order_relaxed, std::memory_order_relaxed))
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
            barrier.arrive_and_wait();

            std::array<size_t, 256> local_offsets;
            for (int b = 0; b < 256; ++b) local_offsets[b] = histograms[tid * 256 + b];

            for (int i = start_idx; i < end_idx; ++i)
            {
                uint8_t byte = (src[i].sortKey >> shift) & 0xFF;
                size_t dest_idx = local_offsets[byte]++;
                dst[dest_idx] = src[i];
            }

            barrier.arrive_and_wait();
            if (auto v = pass; flag1.compare_exchange_strong(v, v + 1,
                std::memory_order_relaxed, std::memory_order_relaxed))
            {
                std::swap(src, dst);
            }
            // barrier.arrive_and_wait();
        }
    });
}
