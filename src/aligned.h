#pragma once

#include <cstddef>
#include <vector>
#if AR_WINDOWS
#include <malloc.h>
#else
#include <cstdlib>
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
//     }1
// };

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
#if AR_WINDOWS
        if (auto p = static_cast<T*>(_mm_malloc(n * sizeof(T), Alignment))) {
            return p;
        }
#else
        if (auto p = static_cast<T*>(aligned_alloc(Alignment, n * sizeof(T)))) {
            return p;
        }
#endif
        throw std::bad_alloc();
    }

    void deallocate(T* p, std::size_t) noexcept {
#if AR_WINDOWS
        _mm_free(p);
#else
        free(p);
#endif
    }

    bool operator==(const AlignedAllocator&) const noexcept { return true; }
    bool operator!=(const AlignedAllocator&) const noexcept { return false; }
};

template<typename T>
using AlignedVector = std::vector<T, AlignedAllocator<T>>;
