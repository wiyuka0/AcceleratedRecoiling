package com.wiyuka.acceleratedrecoiling.natives;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.ffm.FFM;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.*;
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

        collisionPairsArena.close();
        collisionPairsArena = null;
        collisionPairsBuf = null;

        currentSize = -1;
    }

    /**
     * @return Null if the server cannot find the library
     */
    private static SymbolLookup findFoldLib(Arena arena, String dllPath) {
        return SymbolLookup.libraryLookup(dllPath, arena);
    }

    static boolean useCPU = false;

    private static Arena collisionPairsArena = null;
    private static MemorySegment collisionPairsBuf;
    private static int currentSize = -1;

    private static MemorySegment reallocOutputBuf(int newSize) {
        if (collisionPairsArena == null) collisionPairsArena = Arena.ofConfined();
        long newSizeTotal = Math.max(1024, (long)((newSize * 2) * 1.2) * JAVA_INT.byteSize());

        if (newSizeTotal > currentSize) {
            collisionPairsArena.close();
            collisionPairsArena = Arena.ofConfined();
            collisionPairsBuf = collisionPairsArena.allocate(newSizeTotal);
            currentSize = (int) newSizeTotal;
        }
        return collisionPairsBuf;
    }

    public static MemorySegment push(
            double[] locations,
            double[] aabb,
            int[] resultSizeOut
    ) {

//        int[] output = new int[locations.length * FoldConfig.maxCollision];
//        resultSizeOut[0] = SAP.push(locations, aabb, output, locations.length / 3, FoldConfig.maxCollision, 0);
//        return output;
        try (java.lang.foreign.Arena tempArena = java.lang.foreign.Arena.ofConfined()) {
            int count = locations.length / 3;
            int resultSize = locations.length * FoldConfig.maxCollision;
            if (count > maxSizeTouched) maxSizeTouched = count;

//            java.lang.foreign.MemorySegment locationsMem = tempArena.allocateFrom(JAVA_DOUBLE, locations);
//            java.lang.foreign.MemorySegment aabbMem = tempArena.allocateFrom(JAVA_DOUBLE, aabb);
            java.lang.foreign.MemorySegment locationsMem = FFM.allocateArray(tempArena, locations);
            java.lang.foreign.MemorySegment aabbMem = FFM.allocateArray(tempArena, aabb);
            java.lang.foreign.MemorySegment collisionPairs = reallocOutputBuf(resultSize);
//            java.lang.foreign.MemorySegment collisionPairs = tempArena.allocate(JAVA_INT.byteSize() * resultSize * 2);

            int collisionSize = -1;
            try {
                collisionSize = (int) pushMethodHandle.invoke(locationsMem, aabbMem, collisionPairs, count, FoldConfig.maxCollision, 0);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }

            resultSizeOut[0] = collisionSize;
            if (collisionSize == -1) return null;

//            return collisionPairs.toArray(JAVA_INT);
            return collisionPairs;
        }
    }

    public static void initialize() {
//        final Logger logger = Logger.getLogger("Fold");

        Logger logger = AcceleratedRecoiling.LOGGER;

        String dllPath = "";

        String dllName = "acceleratedRecoilingLib";

        String fullDllName = System.mapLibraryName(dllName);

        try (InputStream dllStream = AcceleratedRecoiling.class.getResourceAsStream("/" + fullDllName)) {
            if (dllStream == null) {
                throw new java.io.FileNotFoundException("Cannot find " + fullDllName);
            }

            // 目标路径：JAR 同级目录 ./acceleratedRecoilingLib.dll
            File targetDll = new File(fullDllName);

            dllPath = targetDll.getAbsolutePath();

            try (java.io.OutputStream out = new java.io.FileOutputStream(targetDll)) {
                dllStream.transferTo(out);
                logger.info("fullDllName: " + targetDll.getAbsolutePath());
            }

        } catch (IOException e) {
            throw new RuntimeException("Load failed: " + e.getMessage(), e);
        }

        logger.info("DLL: {}", dllPath);


//        String defaultConfig = """
//                {
//                    "enableEntityCollision": true,
//                    "enableEntityGetterOptimization": true,
//                    "gridSize": 8,
//                    "maxCollision": 32,
//                    "gpuIndex": 0,
//                    "useCPU": false
//                }
//                """;
        String defaultConfig = """
                {
                    "enableEntityCollision": true,
                    "enableEntityGetterOptimization": true,
                    "maxCollision": 32
                }
                """;
        File foldConfig = new File("acceleratedRecoiling.json");
        createConfigFile(foldConfig, defaultConfig);

        String configFile = "";
        try {
            configFile = (Files.readString(foldConfig.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // e.printStackTrace();
            logger.info("Failed to read acceleratedRecoiling config, reason: " + e.getMessage());
            logger.info("Using default config");
            foldConfig.deleteOnExit();
            configFile = defaultConfig;
        }

        JsonObject configJson = JsonParser.parseString(configFile).getAsJsonObject();


        try {
            initConfig(configJson);
        }catch (Exception e) {
            logger.info("Config file is broken, reason: " + e.getMessage());
            logger.info("Using default config");
            foldConfig.deleteOnExit();
            createConfigFile(foldConfig, defaultConfig);
            initConfig(JsonParser.parseString(defaultConfig).getAsJsonObject());
        }
//        useCPU = configJson.get("useCPU").getAsBoolean();


        logger.info("acceleratedRecoiling initialized");
//        logger.info("Use grid size: {}", FoldConfig.gridSize);
        logger.info("Use max collisions: {}", FoldConfig.maxCollision);
//        logger.info("Use gpu index: {}", FoldConfig.gpuIndex);
//        logger.info("Use CPU: {}", useCPU);

        linker = java.lang.foreign.Linker.nativeLinker();
        Arena arena = java.lang.foreign.Arena.ofConfined();
        java.lang.foreign.SymbolLookup lib = findFoldLib(arena, dllPath);

        pushMethodHandle = linker.downcallHandle(
                lib.find("push").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,   // collisionTimes
                        ValueLayout.ADDRESS,    // const double* entityLoc
                        ValueLayout.ADDRESS,    // const double* aabbs
                        ValueLayout.ADDRESS,    // int* output
                        ValueLayout.JAVA_INT,   // int count
                        ValueLayout.JAVA_INT,   // int K
                        ValueLayout.JAVA_INT    // int gridSize
                )
        );

//        java.lang.invoke.MethodHandle initializeMethodHandle = linker.downcallHandle(
//                lib.find("initialize").orElseThrow(),
//                java.lang.foreign.FunctionDescriptor.ofVoid(JAVA_INT, JAVA_BOOLEAN)
//        );

        try {
//            initializeMethodHandle.invoke(FoldConfig.gpuIndex, useCPU);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        nativeArena = arena;
    }

    private static void initConfig(JsonObject configJson) {
        FoldConfig.enableEntityCollision = configJson.get("enableEntityCollision").getAsBoolean();
        FoldConfig.enableEntityGetterOptimization = configJson.get("enableEntityGetterOptimization").getAsBoolean();
        FoldConfig.maxCollision = configJson.get("maxCollision").getAsInt();
    }

    private static void createConfigFile(File foldConfig, String config) {
        if (!foldConfig.exists()) {
            // foldConfig.mkdirs();
            try {
                foldConfig.createNewFile();

                Files.writeString(foldConfig.toPath(), config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
