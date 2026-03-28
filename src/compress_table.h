#pragma once

#include <array>

template <size_t VEC_BITS>
struct CompressTable {
    constexpr static size_t INT_NUM = VEC_BITS / 32;
    constexpr static size_t MASK_MAX = static_cast<size_t>(1) << INT_NUM;

    alignas(64) std::array<std::array<int, INT_NUM>, MASK_MAX> table;

    CompressTable() {
        for (size_t i = 0; i < MASK_MAX; ++i) {
            size_t count = 0;
            for (int bit = 0; bit < INT_NUM; ++bit) {
                if ((i >> bit) & 1) {
                    table[i][count++] = bit;
                }
            }

            for (; count < INT_NUM; ++count) {
                table[i][count] = 0;
            }
        }
    }

    static inline CompressTable instance;
};
