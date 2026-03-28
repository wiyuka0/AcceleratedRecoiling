#pragma once

#include <cstddef>

#include <functional>
#include <memory>
#include <thread>

#include "noncopyable.h"
#include "intrin_util.h"

// NOT thread-safe
class ParallelExecutor : NonCopyable
{
public:
    using InitTask = std::function<void(size_t, size_t)>;

    ParallelExecutor() : ParallelExecutor(0) {}

    explicit ParallelExecutor(const InitTask& initTask) : ParallelExecutor(0, initTask) {}

    explicit ParallelExecutor(const size_t size, const InitTask& initTask = nullptr)
    {
        createThreads(determineThreadCount(size), initTask);
    }

    ~ParallelExecutor()
    {
        destroyThreads();
    }

    void resize(const size_t newSize, const InitTask& initTask = nullptr)
    {
        destroyThreads();
        createThreads(determineThreadCount(newSize), initTask);
    }

    size_t getThreadCount() const noexcept
    {
        return threads.size() + 1;
    }

    std::unique_ptr<ParallelExecutor, void(*)(ParallelExecutor*)> beginActiveScope() {
        activeScope.store(true, std::memory_order_release);
        activeScope.notify_all();

        return {this, [](auto self) {
            self->activeScope.store(false, std::memory_order_release);
        }};
    }

    void execute(const auto& taskFunc)
    {
        auto threadCount = getThreadCount();

        // 派发任务
        Task task{
            [](auto idx, auto workers, auto ctx) { invoke(*static_cast<decltype(&taskFunc)>(ctx), idx, workers); },
            &taskFunc
        };
        currentTask = &task;
        runningWorkers.fetch_add(threadCount, std::memory_order_relaxed);
        taskSeq.fetch_add(1, std::memory_order_release);              // sfence: 保护 currentTask 和 runningWorkers 的写入

        // 当前线程作为 worker#0
        invoke(taskFunc, 0, threadCount);

        // 等待其他 worker
        while (runningWorkers.load(std::memory_order_acquire) != 1)
        {
            AR_PAUSE;
        }

        // 清理任务
        currentTask = nullptr;
        runningWorkers.store(0, std::memory_order_release);
    }

    void executeInBlock(const size_t begin, const size_t end, const auto& taskFunc)
    {
        execute([&](auto idx, auto total) {
            auto [taskBegin, taskEnd] = determineTaskRange(idx, total, begin, end);
            if (taskEnd > taskBegin) [[likely]]
            {
                invoke(taskFunc, taskBegin, taskEnd, idx, total);
            }
        });
    }

private:
    // AI 写的剥离尾参，效果不错
    template <typename F, typename Tuple, std::size_t... Is>
    static void invokeLess(const F& func, Tuple&& tup, std::index_sequence<Is...>)
    {
        if constexpr (std::is_invocable_v<F, std::tuple_element_t<Is, std::remove_reference_t<Tuple>>...>)
        {
            func(std::get<Is>(std::forward<Tuple>(tup))...);
        }
        else if constexpr (sizeof...(Is) > 0)
        {
            invokeLess(func, std::forward<Tuple>(tup), std::make_index_sequence<sizeof...(Is) - 1>{});
        }
        else
        {
            static_assert(sizeof(F) == -1, "unsatisfied function");
        }
    }

    template <typename F, typename... Args>
    static void invoke(const F& func, Args&&... args)
    {
        invokeLess(func, std::forward_as_tuple(std::forward<Args>(args)...), std::make_index_sequence<sizeof...(Args)>{});
    }

    static std::pair<size_t, size_t> determineTaskRange(size_t idx, size_t workers, size_t taskBegin, size_t taskEnd) noexcept
    {
        auto taskSize = taskEnd - taskBegin;
        return {
            taskBegin + (idx == 0 ? 0 : taskSize * idx / workers),
            taskBegin + taskSize * (idx + 1) / workers
        };
    }

    static size_t determineThreadCount(const size_t size) noexcept
    {
        if (size < 1)
        {
            // hardware_concurrency 无法识别 cgroup 环境，不能作为默认值
            return 1;
        }
        return size;
    }

    void createThreads(const size_t size, const InitTask& initTask)
    {
        workersAvailable = true;
        if (auto actualSize = size - 1; actualSize > 0)
        {
            threads.reserve(actualSize);
            for (size_t i = 0; i < actualSize; ++i)
            {
                threads.emplace_back(&ParallelExecutor::worker, this, i + 1, size, initTask);
            }
        }
    }

    void destroyThreads()
    {
        if (!threads.empty())
        {
            workersAvailable = false;
            activeScope.store(true, std::memory_order_release);
            activeScope.notify_all();
            for (auto &thread : threads)
            {
                // macos 14.5 没有 jthread，先凑合一下
                thread.join();
            }
            threads.clear();
        }
    }

    void worker(const size_t idx, const size_t workers, InitTask initTask)
    {
        if (initTask)
        {
            invoke(initTask, idx, workers);
            initTask = nullptr;
        }

        uint64_t localSeq = 0;
        while (workersAvailable)
        {
            // sfence: 保护 currentTask 读取
            if (auto id = taskSeq.load(std::memory_order_acquire); id > localSeq) [[likely]]
            {
                localSeq = id;
                if (auto task = currentTask; task) [[likely]]
                {
                    task->func(idx, workers, task->ctx);
                }
                runningWorkers.fetch_sub(1, std::memory_order_release);
            }
            else
            {
                AR_PAUSE;
                // lfence: 保护下一轮 workersAvailable 的读取
                activeScope.wait(false, std::memory_order_acquire);
            }
        }
    }

    struct Task {
        void(*func)(size_t, size_t, const void*);
        const void* ctx;
    };

    std::vector<std::thread> threads;
    bool workersAvailable = false;
    std::atomic_bool activeScope = false;

    std::atomic_uint64_t taskSeq = 0;
    Task* currentTask;

    alignas(64) std::atomic_int32_t runningWorkers = 0;
};
