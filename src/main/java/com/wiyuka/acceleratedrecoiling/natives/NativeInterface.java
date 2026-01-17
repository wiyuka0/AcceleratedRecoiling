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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.concurrent.*;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class NativeInterface {
    private static java.lang.foreign.Linker linker;
    private static java.lang.foreign.Arena nativeArena;
    private static java.lang.invoke.MethodHandle pushMethodHandle = null;
    private static long maxSizeTouched = -1;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AcceleratedRecoiling-NativeWorker");
        t.setDaemon(true);
        return t;
    });

    public static void destroy() {
        if (!ParallelAABB.isInitialized) {
            return;
        }
        ParallelAABB.isInitialized = false;

        WORKER.shutdown();
        try {
            if (!WORKER.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                WORKER.shutdownNow();
            }
        } catch (InterruptedException e) {
            WORKER.shutdownNow();
        }

        if (nativeArena != null) {
            nativeArena.close();
        }
        nativeArena = null;
        linker = null;
        pushMethodHandle = null;

        if (collisionPairsArena != null) {
            collisionPairsArena.close();
        }
        collisionPairsArena = null;
        collisionPairsBuf = null;

        currentSize = -1;
    }

    private static java.lang.foreign.SymbolLookup findFoldLib(java.lang.foreign.Arena arena, String dllPath) {
        return java.lang.foreign.SymbolLookup.libraryLookup(dllPath, arena);
    }

    private static Arena collisionPairsArena = null;
    private static MemorySegment collisionPairsBuf;
    private static int currentSize = -1;

    private static MemorySegment reallocOutputBuf(int newSize) {
        if (collisionPairsArena == null) collisionPairsArena = Arena.ofShared();

        long newSizeTotal = Math.max(1024, (long)((newSize * 2) * 1.2) * JAVA_INT.byteSize());

        if (newSizeTotal > currentSize) {
            if (collisionPairsArena.scope().isAlive()) {
                collisionPairsArena.close();
            }
            collisionPairsArena = Arena.ofShared();
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
        Future<MemorySegment> future = WORKER.submit(() -> {
            try (java.lang.foreign.Arena tempArena = java.lang.foreign.Arena.ofConfined()) {
                int count = locations.length / 3;
                int resultSize = locations.length * FoldConfig.maxCollision;
                if (count > maxSizeTouched) maxSizeTouched = count;

                java.lang.foreign.MemorySegment locationsMem = FFM.allocateArray(tempArena, locations);
                java.lang.foreign.MemorySegment aabbMem = FFM.allocateArray(tempArena, aabb);

                java.lang.foreign.MemorySegment collisionPairs = reallocOutputBuf(resultSize);

                int collisionSize = -1;
                try {
                    collisionSize = (int) pushMethodHandle.invoke(locationsMem, aabbMem, collisionPairs, count, FoldConfig.maxCollision, 0);
                } catch (Throwable e) {
                    throw new RuntimeException("Native invoke failed", e);
                }

                resultSizeOut[0] = collisionSize;

                if (collisionSize == -1) return null;

                return collisionPairs;
            }
        });

        try {

            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for native calculation", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Error during native calculation", cause);
        }
    }

    private static final Random rnd = new Random();

    public static void initialize() {
        Logger logger = AcceleratedRecoiling.LOGGER;
        String dllPath = "";
        String dllName = "acceleratedRecoilingLib";
        String fullDllName = System.mapLibraryName(dllName);

        try (InputStream dllStream = AcceleratedRecoiling.class.getResourceAsStream("/" + fullDllName)) {
            if (dllStream == null) {
                throw new java.io.FileNotFoundException("Cannot find " + fullDllName);
            }

            String extension = ".dll"; // 默认
            int i = fullDllName.lastIndexOf('.');
            if (i > 0) {
                extension = fullDllName.substring(i);
            }

            Path tempPath = Files.createTempFile(dllName + rnd.nextLong(), extension);
            File targetDll = tempPath.toFile();

            targetDll.deleteOnExit();

            dllPath = targetDll.getAbsolutePath();

            Files.copy(dllStream, tempPath, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Extracted native lib to temp: " + targetDll.getAbsolutePath());
//            File targetDll = new File(fullDllName);
//            dllPath = targetDll.getAbsolutePath();
//            try (java.io.OutputStream out = new java.io.FileOutputStream(targetDll)) {
//                dllStream.transferTo(out);
//                logger.info("fullDllName: " + targetDll.getAbsolutePath());
//            }
        } catch (IOException e) {
            throw new RuntimeException("Load failed: " + e.getMessage(), e);
        }

        logger.info("DLL: {}", dllPath);

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
            logger.warn("Failed to read acceleratedRecoiling config, reason: " + e.getMessage());
            logger.warn("Using default config");
            foldConfig.deleteOnExit();
            configFile = defaultConfig;
        }

        JsonObject configJson = JsonParser.parseString(configFile).getAsJsonObject();

        try {
            initConfig(configJson);
        } catch (Exception e) {
            logger.info("Config file is broken, reason: " + e.getMessage());
            logger.info("Using default config");
            foldConfig.deleteOnExit();
            createConfigFile(foldConfig, defaultConfig);
            initConfig(JsonParser.parseString(defaultConfig).getAsJsonObject());
        }

        logger.info("acceleratedRecoiling initialized");
        logger.info("Use max collisions: {}", FoldConfig.maxCollision);

        linker = java.lang.foreign.Linker.nativeLinker();

        Arena arena = java.lang.foreign.Arena.ofConfined();
        java.lang.foreign.SymbolLookup lib = findFoldLib(arena, dllPath);

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
                foldConfig.createNewFile();
                Files.writeString(foldConfig.toPath(), config);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}