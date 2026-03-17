package com.wiyuka.acceleratedrecoiling.natives;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import com.wiyuka.acceleratedrecoiling.ffm.FFM;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.*;

public class NativeInterface {
    private static Linker linker;
    private static Arena nativeArena;
    private static MethodHandle pushMethodHandle = null;
    private static MethodHandle createCtxMethodHandle = null;
    private static MethodHandle destroyCtxMethodHandle = null; // todo
    private static long maxSizeTouched = -1;

    static boolean useCPU = false;

    private static Arena collisionPairsArena = null;
    private static MemorySegment collisionPairsBufA;
    private static MemorySegment collisionPairsBufB;
    private static MemorySegment context;
    private static int currentSize = -1;

    public record MemPair(MemorySegment A, MemorySegment B) {}

    public static void destroy() {
        if (!ParallelAABB.isInitialized) {
            return;
        }

        ParallelAABB.isInitialized = false;

        if (nativeArena != null) {
            nativeArena.close();
        }

        if (collisionPairsArena != null) {
            collisionPairsArena.close();
        }

        nativeArena = null;
        linker = null;
        pushMethodHandle = null;
        createCtxMethodHandle = null;
        destroyCtxMethodHandle = null;

        collisionPairsArena = null;
        collisionPairsBufA = null;
        collisionPairsBufB = null;
        context = null;
        currentSize = -1;
        maxSizeTouched = -1;
    }

    private static SymbolLookup findFoldLib(Arena arena, String dllPath) {
        return SymbolLookup.libraryLookup(dllPath, arena);
    }

    private static MemPair reallocOutputBuf(int newSize) {
        if (collisionPairsArena == null) {
            collisionPairsArena = Arena.ofConfined();
        }

        long newSizeTotal = Math.max(1024, (long) (newSize * 1.2) * JAVA_INT.byteSize());

        if (newSizeTotal > currentSize) {
            collisionPairsArena.close();
            collisionPairsArena = Arena.ofConfined();
            collisionPairsBufA = collisionPairsArena.allocate(newSizeTotal);
            collisionPairsBufB = collisionPairsArena.allocate(newSizeTotal);
            currentSize = (int) newSizeTotal;
        }
        return new MemPair(collisionPairsBufA, collisionPairsBufB);
    }

    public static MemPair push(
            double[] locations,
            double[] aabb,
            int[] resultSizeOut
    ) {
        try (Arena tempArena = Arena.ofConfined()) {
            int count = locations.length / 3;
            int resultSize = locations.length * FoldConfig.maxCollision;
            if (count > maxSizeTouched) maxSizeTouched = count;

            MemorySegment aabbMem = FFM.allocateArray(tempArena, aabb);
            MemPair collisionPairs = reallocOutputBuf(resultSize);

            int collisionSize;
            try {
                collisionSize = (int) pushMethodHandle.invokeExact(
                        aabbMem,
                        collisionPairs.A(),
                        collisionPairs.B(),
                        count,
                        FoldConfig.maxCollision,
                        0,
                        context
                );
            } catch (Throwable e) {
                throw new RuntimeException("Failed to invoke native push method", e);
            }

            resultSizeOut[0] = collisionSize;
            if (collisionSize == -1) return null;

            return collisionPairs;
        }
    }

    public static void initialize() {
        Logger logger = AcceleratedRecoiling.LOGGER;
        String dllPath = "";
        String dllName = "acceleratedRecoilingLib";
        String fullDllName = System.mapLibraryName(dllName);

        try (InputStream dllStream = AcceleratedRecoiling.class.getResourceAsStream("/" + fullDllName)) {
            if (dllStream == null) {
                throw new FileNotFoundException("Cannot find " + fullDllName + " in resources.");
            }

            File tempDll = File.createTempFile(UUID.randomUUID() + "_acceleratedRecoiling_", "_" + fullDllName);
            tempDll.deleteOnExit();

            dllPath = tempDll.getAbsolutePath();

            try (OutputStream out = new FileOutputStream(tempDll)) {
                dllStream.transferTo(out);
                logger.info("Extracted native library to temp: {}", dllPath);
            }

        } catch (IOException e) {
            throw new RuntimeException("Native library load failed: " + e.getMessage(), e);
        }

        String defaultConfig = """
                {
                    "enableEntityCollision": true,
                    "enableEntityGetterOptimization": true,
                    "maxCollision": 32
                }
                """;
        File foldConfig = new File("acceleratedRecoiling.json");
        createConfigFile(foldConfig, defaultConfig);

        String configFile;
        try {
            configFile = Files.readString(foldConfig.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to read acceleratedRecoiling.json, reason: {}. Using default config.", e.getMessage());
            foldConfig.deleteOnExit();
            configFile = defaultConfig;
        }

        try {
            JsonObject configJson = JsonParser.parseString(configFile).getAsJsonObject();
            initConfig(configJson);
        } catch (Exception e) {
            logger.warn("Config file is broken, reason: {}. Overwriting with default config.", e.getMessage());
            foldConfig.deleteOnExit();
            createConfigFile(foldConfig, defaultConfig);
            initConfig(JsonParser.parseString(defaultConfig).getAsJsonObject());
        }

        logger.info("acceleratedRecoiling initialized.");
        logger.info("Use max collisions: {}", FoldConfig.maxCollision);

        // 3. 绑定 FFM
        linker = Linker.nativeLinker();
        Arena arena = Arena.ofShared();
        SymbolLookup lib = findFoldLib(arena, dllPath);

        pushMethodHandle = linker.downcallHandle(
                lib.find("push").orElseThrow(() -> new RuntimeException("Cannot find symbol 'push'")),
                FunctionDescriptor.of(
                        JAVA_INT,   // return: collisionTimes
                        ADDRESS,    // const double* aabbs
                        ADDRESS,    // int* outputA
                        ADDRESS,    // int* outputB
                        JAVA_INT,   // int count
                        JAVA_INT,   // int K
                        JAVA_INT,   // int gridSize
                        ADDRESS     // Context
                )
        );

        createCtxMethodHandle = linker.downcallHandle(
                lib.find("createCtx").orElseThrow(() -> new RuntimeException("Cannot find symbol 'createCtx'")),
                FunctionDescriptor.of(ADDRESS)
        );

        try {
            context = (MemorySegment) createCtxMethodHandle.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create native context", e);
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
            try {
                if (foldConfig.createNewFile()) {
                    Files.writeString(foldConfig.toPath(), config);
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create config file", e);
            }
        }
    }
}