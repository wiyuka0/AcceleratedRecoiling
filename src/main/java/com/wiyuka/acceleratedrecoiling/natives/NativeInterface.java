package com.wiyuka.acceleratedrecoiling.natives;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static java.lang.foreign.ValueLayout.*;

public class NativeInterface {
    private static java.lang.foreign.SegmentAllocator allocator;
    private static java.lang.foreign.Linker linker;
    private static java.lang.foreign.Arena nativeArena;
    private static java.lang.invoke.MethodHandle pushMethodHandle = null;
    private static long maxSizeTouched = -1;

    public static void destroy() {
        // 1. 使用标志位防止重复调用
        if (!ParallelAABB.isInitialized) {
            return;
        }

        // 2. 立即设置标志位
        ParallelAABB.isInitialized = false;

        // 4. 关闭为 FFM 分配的 Arena (非常重要)
        if (nativeArena != null) {
            // 这将释放为加载库符号而分配的本机内存
            nativeArena.close();
        }

        // 5. 将静态句柄设为 null，帮助 GC 并防止“use-after-close”
        nativeArena = null;
        linker = null;
        pushMethodHandle = null;
    }
    /**
     * @return Null if the server cannot find the library
     */
    private static java.lang.foreign.SymbolLookup findFoldLib(java.lang.foreign.Arena arena, String dllPath) {
        return java.lang.foreign.SymbolLookup.libraryLookup(dllPath, arena);
    }

    static int K = 32;
    static int GRID_SIZE = 8;
    static int gpuIndex = 0;
    static boolean useCPU = false;

    public static int[] push(
        double[] locations,
        double[] aabb,
        int[] resultSizeOut
    ){
        try(java.lang.foreign.Arena tempArena = java.lang.foreign.Arena.ofConfined()) {
            int count = locations.length / 3;
            int resultSize = locations.length * K;
            if (count > maxSizeTouched) maxSizeTouched = count;

//            java.lang.foreign.MemorySegment locationsMem = tempArena.allocateFrom(JAVA_DOUBLE, locations);
//            java.lang.foreign.MemorySegment aabbMem = tempArena.allocateFrom(JAVA_DOUBLE, aabb);
            java.lang.foreign.MemorySegment locationsMem = tempArena.allocateArray(JAVA_DOUBLE, locations);
            java.lang.foreign.MemorySegment aabbMem = tempArena.allocateArray(JAVA_DOUBLE, aabb);
            java.lang.foreign.MemorySegment collisionPairs = tempArena.allocate(JAVA_INT.byteSize() * resultSize * 2);

            int collisionSize = -1;
            try {
                collisionSize = (int) pushMethodHandle.invoke(locationsMem, aabbMem, collisionPairs, count, K, GRID_SIZE);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
            resultSizeOut[0] = collisionSize;
            if (collisionSize == -1) return new int[0];
            return collisionPairs.toArray(JAVA_INT);
        }
    }
    public static void initialize() {
//        final Logger logger = Logger.getLogger("Fold");

        Logger logger = AcceleratedRecoiling.LOGGER;

        String dllPath = "";

        String dllName = "acceleratedRecoilingLib";

        String fullDllName = System.mapLibraryName(dllName);


        try (InputStream dllStream = AcceleratedRecoiling.class.getResourceAsStream(STR."/\{fullDllName}")) {
            if (dllStream == null) {
                throw new java.io.FileNotFoundException(STR."Cannot find /\{fullDllName}");
            }

            // 目标路径：JAR 同级目录 ./acceleratedRecoilingLib.dll
            File targetDll = new File(fullDllName);

            dllPath = targetDll.getAbsolutePath();

            // 如果文件不存在，就提取出来
            if (!targetDll.exists()) {
                try (java.io.OutputStream out = new java.io.FileOutputStream(targetDll)) {
                    dllStream.transferTo(out);
                    logger.info(STR."\{fullDllName}: \{targetDll.getAbsolutePath()}");
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(STR."Load failed: \{e.getMessage()}", e);
        }

        logger.info("DLL: {}", dllPath);


        String config = """
            {
                "useFold": true,
                "gridSize": 8,
                "maxCollision": 32,
                "gpuIndex": 0,
                "useCPU": false
            }
            """;
        File foldConfig = new File("acceleratedRecoiling.json");
        if(!foldConfig.exists()) {
            // foldConfig.mkdirs();
            try {
                foldConfig.createNewFile();

                Files.writeString(foldConfig.toPath(), config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            config = (Files.readString(foldConfig.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // e.printStackTrace();
            logger.info("Failed to read acceleratedRecoiling config, reason: " + e.getMessage());
        }

        JsonObject configJson = JsonParser.parseString(config).getAsJsonObject();


        ParallelAABB.useFold = configJson.get("useFold").getAsBoolean();
        GRID_SIZE = configJson.get("gridSize").getAsInt();
        K = configJson.get("maxCollision").getAsInt();
        gpuIndex = configJson.get("gpuIndex").getAsInt();
        useCPU = configJson.get("useCPU").getAsBoolean();


        logger.info("acceleratedRecoiling initialized");
        logger.info("Use grid size: {}", GRID_SIZE);
        logger.info("Use max collisions: {}", K);
        logger.info("Use gpu index: {}", gpuIndex);
        logger.info("Use CPU: {}", useCPU);

        linker = java.lang.foreign.Linker.nativeLinker();
        nativeArena = java.lang.foreign.Arena.ofConfined();
        java.lang.foreign.SymbolLookup lib = findFoldLib(nativeArena, dllPath);

        pushMethodHandle = linker.downcallHandle(
            lib.find("push").orElseThrow(),
            java.lang.foreign.FunctionDescriptor.of(
                java.lang.foreign.ValueLayout.JAVA_INT,   // collisionTimes
                java.lang.foreign.ValueLayout.ADDRESS,    // const double* entityLoc
                java.lang.foreign.ValueLayout.ADDRESS,    // const double* aabbs
                java.lang.foreign.ValueLayout.ADDRESS,    // int* output
                java.lang.foreign.ValueLayout.JAVA_INT,   // int count
                java.lang.foreign.ValueLayout.JAVA_INT,   // int K
                java.lang.foreign.ValueLayout.JAVA_INT    // int gridSize
            )
        );

        java.lang.invoke.MethodHandle initializeMethodHandle = linker.downcallHandle(
            lib.find("initialize").orElseThrow(),
            java.lang.foreign.FunctionDescriptor.ofVoid(JAVA_INT, JAVA_BOOLEAN)
        );

        try {
            initializeMethodHandle.invoke(gpuIndex, useCPU);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
