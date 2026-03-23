package com.wiyuka.acceleratedrecoiling.natives;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.foreign.ValueLayout.*;

public class FFMBackend implements INativeBackend {
    private static Linker linker;
    private static Arena nativeArena;
    private static MethodHandle pushMethodHandle = null;
    private static MethodHandle createCtxMethodHandle = null;
    private static MethodHandle destroyCtxMethodHandle = null;
    private static MethodHandle createCfgMethodHandle = null;

    private static MethodHandle updateCfgMethodHandle = null;
    private static MethodHandle destroyCfgMethodHandle = null;

    private static final AtomicLong maxSizeTouched = new AtomicLong(-1);

    @Override
    public String getName() {
        return "FFM";
    }

    static class PushResultFFM implements com.wiyuka.acceleratedrecoiling.natives.PushResult {
        private MemorySegment segmentA;
        private MemorySegment segmentB;
        private MemorySegment segmentDensity;

        private PushResultFFM() {}
        void update(MemorySegment a, MemorySegment b, MemorySegment density) {
            this.segmentA = a;
            this.segmentB = b;
            this.segmentDensity = density;
        }
        @Override
        public int getA(int index) {
            return segmentA.get(JAVA_INT, (long) index * Integer.BYTES);
        }
        @Override
        public int getB(int index) {
            return segmentB.get(JAVA_INT, (long) index * Integer.BYTES);
        }
        @Override
        public float getDensity(int index) {
            return segmentDensity.get(JAVA_FLOAT, (long) index * Float.BYTES);
        }
        @Override
        public void copyATo(int[] dest, int length) {
            MemorySegment.copy(segmentA, JAVA_INT, 0, dest, 0, length);
        }
        @Override
        public void copyBTo(int[] dest, int length) {
            MemorySegment.copy(segmentB, JAVA_INT, 0, dest, 0, length);
        }
        @Override
        public void copyDensityTo(float[] dest, int length) {
            MemorySegment.copy(segmentDensity, JAVA_FLOAT, 0, dest, 0, length);
        }
    }
    private static class ThreadState {
        Arena bufferArena = null;
        MemorySegment bufA;
        MemorySegment bufB;
        MemorySegment densityBuf;
        MemorySegment context;
        MemorySegment configPtr;
        int currentSize = -1;

        final PushResultFFM resultWrapper = new PushResultFFM();
        ThreadState() {
            try {
                if (createCtxMethodHandle != null) {
                    context = (MemorySegment) createCtxMethodHandle.invokeExact();
                }
                if (createCfgMethodHandle != null) {
                    configPtr = (MemorySegment) createCfgMethodHandle.invokeExact(
                            FoldConfig.maxCollision,
                            FoldConfig.gridSize,
                            FoldConfig.densityWindow,
                            FoldConfig.maxThreads
                    );
                }
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create native context for thread", e);
            }
        }

        PushResultFFM reallocOutputBuf(int newSize) {
            int newCapacity = (int) (newSize * 1.2);
            long newSizeTotal = Math.max(1024L, (long) newCapacity * JAVA_INT.byteSize());
            long densitySizeTotal = Math.max(1024L, (long) newCapacity * JAVA_FLOAT.byteSize());
            if (newSizeTotal > currentSize) {
                if (bufferArena != null) {
                    bufferArena.close();
                }
                bufferArena = Arena.ofConfined();
                bufA = bufferArena.allocate(newSizeTotal);
                bufB = bufferArena.allocate(newSizeTotal);
                densityBuf = bufferArena.allocate(densitySizeTotal);
                currentSize = (int) newSizeTotal;
            }

            // 更新封装类中的内部指针
            resultWrapper.update(bufA, bufB, densityBuf);
            return resultWrapper;
        }

        void destroy() {
            if (bufferArena != null) {
                try { bufferArena.close(); } catch (Exception ignored) {}
            }
            if (context != null && destroyCtxMethodHandle != null) {
                try {
                    destroyCtxMethodHandle.invokeExact(context);
                } catch (Throwable e) {
                    AcceleratedRecoiling.LOGGER.error("Failed to destroy native context", e);
                }
            }

            if (configPtr != null && destroyCfgMethodHandle != null) {
                try {
                    destroyCfgMethodHandle.invokeExact(configPtr);
                } catch (Throwable e) {
                    AcceleratedRecoiling.LOGGER.error("Failed to destroy native config", e);
                }
            }
        }
    }

    @Override
    public void applyConfig() {
        if (!ParallelAABB.isInitialized || updateCfgMethodHandle == null) {
            return;
        }
        for (ThreadState state : ALL_THREAD_STATES) {
            if (state.configPtr != null) {
                try {
                    updateCfgMethodHandle.invokeExact(
                            state.configPtr,
                            FoldConfig.maxCollision,
                            FoldConfig.gridSize,
                            FoldConfig.densityWindow,
                            FoldConfig.maxThreads
                    );
                } catch (Throwable e) {
                    AcceleratedRecoiling.LOGGER.error("Failed to update native config for thread", e);
                }
            }
        }
    }

    private static final Set<ThreadState> ALL_THREAD_STATES = ConcurrentHashMap.newKeySet();

    private static final ThreadLocal<ThreadState> THREAD_STATE = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        ALL_THREAD_STATES.add(state);
        return state;
    });

    @Override
    public void destroy() {
        if (!ParallelAABB.isInitialized) {
            return;
        }

        ParallelAABB.isInitialized = false;

        for (ThreadState state : ALL_THREAD_STATES) {
            state.destroy();
        }
        ALL_THREAD_STATES.clear();

        nativeArena = null;
        linker = null;
        pushMethodHandle = null;
        createCtxMethodHandle = null;
        destroyCtxMethodHandle = null;

        maxSizeTouched.set(-1);
    }

    private static SymbolLookup findFoldLib(Arena arena, String dllPath) {
        return SymbolLookup.libraryLookup(dllPath, arena);
    }

    @Override
    public PushResult push(
            double[] locations,
            double[] aabb,
            int[] resultSizeOut
    ) {
        if (!ParallelAABB.isInitialized) {
            return null;
        }

        ThreadState state = THREAD_STATE.get();
        if (state.context == null) {
            return null;
        }

        try (Arena tempArena = Arena.ofConfined()) {
            int count = locations.length / 3;
            int resultSize = locations.length * FoldConfig.maxCollision;
            maxSizeTouched.updateAndGet(current -> Math.max(current, count));

            MemorySegment aabbMem = FFM.allocateArray(tempArena, aabb);
            PushResultFFM collisionPairs = state.reallocOutputBuf(resultSize);

            int collisionSize = 0;
            try {
                collisionSize = (int) pushMethodHandle.invokeExact(
                        aabbMem,                 // const double *aabbs
                        collisionPairs.segmentA, // int *outputA
                        collisionPairs.segmentB, // int *outputB
                        count,                   // int entityCount
                        collisionPairs.segmentDensity, // float* densityBuf
                        state.context,           // void* memDataPtrOri
                        state.configPtr          // void* configPtr
                );
            } catch (Throwable e) {
                throw new RuntimeException("Failed to invoke native push method", e);
            }

            resultSizeOut[0] = collisionSize;
            if (collisionSize == -1) return null;

            return collisionPairs;
        }
    }

    @Override
    public void initialize() {
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

//        String defaultConfig = """
//                {
//                    "enableEntityCollision": true,
//                    "enableEntityGetterOptimization": true,
//                    "maxCollision": 32,
//                    "gridSize": 1,
//                    "densityWindow": 4,
//                    "densityThreshold": 16,
//                    "maxThreads": %%
//                }
//                """;

        JsonObject defaultConfigJson =  new JsonObject();
        defaultConfigJson.addProperty("enableEntityCollision", true);
        defaultConfigJson.addProperty("enableEntityGetterOptimization", true);
        defaultConfigJson.addProperty("maxCollision", 32);
        defaultConfigJson.addProperty("gridSize", 1);
        defaultConfigJson.addProperty("densityWindow", 4);
        defaultConfigJson.addProperty("densityThreshold", 16);
        defaultConfigJson.addProperty("maxThreads", Runtime.getRuntime().availableProcessors() / 2);
        File foldConfig = new File("acceleratedRecoiling.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String defaultConfig = gson.toJson(defaultConfigJson);
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

        linker = Linker.nativeLinker();
        nativeArena = Arena.global();
        SymbolLookup lib = findFoldLib(nativeArena, dllPath);

        pushMethodHandle = linker.downcallHandle(
                lib.find("push").orElseThrow(() -> new RuntimeException("Cannot find symbol 'push'")),
                FunctionDescriptor.of(
                        JAVA_INT,   // return: collisionTimes
                        ADDRESS,    // const double* aabbs
                        ADDRESS,    // int* outputA
                        ADDRESS,    // int* outputB
                        JAVA_INT,   // int count
                        ADDRESS,    // float* densityBuf
                        ADDRESS,    // void* memDataPtrOri (Context)
                        ADDRESS     // void* configPtr
                )
        );

        createCtxMethodHandle = linker.downcallHandle(
                lib.find("createCtx").orElseThrow(() -> new RuntimeException("Cannot find symbol 'createCtx'")),
                FunctionDescriptor.of(ADDRESS)
        );
        createCfgMethodHandle = linker.downcallHandle(
                lib.find("createCfg").orElseThrow(() -> new RuntimeException("Cannot find symbol 'createCfg'")),
                FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)
        );

        try {
            updateCfgMethodHandle = linker.downcallHandle(
                    lib.find("updateCfg").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT)
            );
        } catch (Exception e) {
            logger.warn("Cannot find symbol 'updateCfg'");
        }
        try {
            destroyCfgMethodHandle = linker.downcallHandle(
                    lib.find("destroyCfg").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS)
            );
        } catch (Exception e) {
            logger.warn("Cannot find symbol 'destroyCfg'");
        }
        try {
            destroyCtxMethodHandle = linker.downcallHandle(
                    lib.find("destroyCtx").orElseThrow(),
                    FunctionDescriptor.ofVoid(ADDRESS)
            );
        } catch (Exception e) {
            logger.warn("Cannot find symbol 'destroyCtx'");
        }

    }

    private static void initConfig(JsonObject configJson) {
        FoldConfig.enableEntityCollision = configJson.get("enableEntityCollision").getAsBoolean();
        FoldConfig.enableEntityGetterOptimization = configJson.get("enableEntityGetterOptimization").getAsBoolean();
        FoldConfig.maxCollision = configJson.get("maxCollision").getAsInt();

        FoldConfig.gridSize = configJson.has("gridSize") ? configJson.get("gridSize").getAsInt() : 1;
        FoldConfig.densityWindow = configJson.has("densityWindow") ? configJson.get("densityWindow").getAsInt() : 4;
        FoldConfig.densityThreshold = configJson.has("densityThreshold") ? configJson.get("densityThreshold").getAsInt() : 16;

        int safeThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        FoldConfig.maxThreads = configJson.has("maxThreads") ? configJson.get("maxThreads").getAsInt() : safeThreads;
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