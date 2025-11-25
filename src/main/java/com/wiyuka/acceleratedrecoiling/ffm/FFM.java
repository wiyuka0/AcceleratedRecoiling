package com.wiyuka.acceleratedrecoiling.ffm;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import org.slf4j.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class FFM {
    // 用于标记检测到的版本，方便调试
    private static final int JDK_VERSION;

    // 核心句柄，static final 确保 JIT 可以内联优化
    private static final MethodHandle ALLOCATE_HANDLE;

    static {
        Logger logger = AcceleratedRecoiling.LOGGER;

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        MethodHandle handle = null;
        int version = -1;

        try {
            // -----------------------------------------------------------
            // 1. 尝试 JDK 22+ 的新标准: allocateFrom
            // -----------------------------------------------------------
            // 签名: MemorySegment allocateFrom(ValueLayout.OfDouble layout, double[] array)
            handle = lookup.findVirtual(Arena.class, "allocateFrom",
                    MethodType.methodType(MemorySegment.class, ValueLayout.OfDouble.class, double[].class));
            version = 22; // 代表 JDK 22 或更高

        } catch (NoSuchMethodException | IllegalAccessException e1) {
            try {
                // -----------------------------------------------------------
                // 2. 回退到 JDK 21 (Preview) 的旧标准: allocateArray
                // -----------------------------------------------------------
                // 签名: MemorySegment allocateArray(ValueLayout.OfDouble layout, double[] array)
                handle = lookup.findVirtual(Arena.class, "allocateArray",
                        MethodType.methodType(MemorySegment.class, ValueLayout.OfDouble.class, double[].class));
                version = 21; // 代表 JDK 21

            } catch (NoSuchMethodException | IllegalAccessException e2) {
                // 两个都找不到，说明环境极其离谱（比如 JDK 17 或更低）
                throw new RuntimeException("FFM Initialization Failed: Compatible 'allocate' method not found. " +
                        "Require JDK 21 (allocateArray) or JDK 22+ (allocateFrom).", e2);
            }
        }

        ALLOCATE_HANDLE = handle;
        JDK_VERSION = version;

        logger.info("[FFM] Native Linker initialized. Detected JDK Compatibility Level: {}", version);
    }

    /**
     * 获取当前检测到的 JDK 兼容版本 (21 或 22)
     */
    public static int getJdkVersion() {
        return JDK_VERSION;
    }

    /**
     * 兼容性封装方法
     * 自动根据 JDK 版本调用 allocateArray 或 allocateFrom
     *
     * @param arena 内存域
     * @param array 要写入的 double 数组
     * @return 分配好的内存段
     */
    public static MemorySegment allocateArray(Arena arena, double[] array) {
        try {
            // invokeExact 要求参数类型严格匹配，性能最高
            // ValueLayout.JAVA_DOUBLE 对应 double[] 的布局
            return (MemorySegment) ALLOCATE_HANDLE.invokeExact(arena, ValueLayout.JAVA_DOUBLE, array);
        } catch (Throwable e) {
            // MethodHandle.invokeExact 可能会抛出 Throwable，必须捕获
            throw new RuntimeException("Failed to allocate native array", e);
        }
    }
}