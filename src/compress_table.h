#pragma once

#include <array>

struct CompressTable {
    alignas(64) std::array<std::array<int, 8>, 256> table;

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
