package com.wiyuka.acceleratedrecoiling.natives;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;
import com.wiyuka.acceleratedrecoiling.config.FoldConfig;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class JNIBackend implements INativeBackend {

    private static final AtomicLong maxSizeTouched = new AtomicLong(-1);

    private static native long createCtx();
    private static native void destroyCtx(long ctxPtr);
    private static native long createCfg(int maxCollision, int gridSize, int densityWindow, int maxThreads);
    private static native void updateCfg(long cfgPtr, int maxCollision, int gridSize, int densityWindow, int maxThreads);
    private static native void destroyCfg(long cfgPtr);
    private static native int push(double[] aabbs, int[] outputA, int[] outputB, int count, float[] densityBuf, long ctxPtr, long cfgPtr);

    @Override
    public String getName() {
        return "JNI";
    }

    static class PushResultJNI implements PushResult {
        private int[] arrayA;
        private int[] arrayB;
        private float[] arrayDensity;

        private PushResultJNI() {}

        void update(int[] a, int[] b, float[] density) {
            this.arrayA = a;
            this.arrayB = b;
            this.arrayDensity = density;
        }

        @Override
        public int getA(int index) { return arrayA[index]; }

        @Override
        public int getB(int index) { return arrayB[index]; }

        @Override
        public float getDensity(int index) { return arrayDensity[index]; }

        @Override
        public void copyATo(int[] dest, int length) { System.arraycopy(arrayA, 0, dest, 0, length); }

        @Override
        public void copyBTo(int[] dest, int length) { System.arraycopy(arrayB, 0, dest, 0, length); }

        @Override
        public void copyDensityTo(float[] dest, int length) { System.arraycopy(arrayDensity, 0, dest, 0, length); }
    }

    private static class ThreadState {
        int[] bufA;
        int[] bufB;
        float[] densityBuf;

        long contextPtr = 0;
        long configPtr = 0;
        int currentSize = -1;

        final PushResultJNI resultWrapper = new PushResultJNI();

        ThreadState() {
            try {
                contextPtr = createCtx();
                configPtr = createCfg(
                        FoldConfig.maxCollision,
                        FoldConfig.gridSize,
                        FoldConfig.densityWindow,
                        FoldConfig.maxThreads
                );
            } catch (Throwable e) {
                throw new RuntimeException("Failed to create JNI native context for thread", e);
            }
        }

        PushResultJNI reallocOutputBuf(int newSize) {
            int newCapacity = (int) (newSize * 1.2);
            if (newCapacity > currentSize) {
                int allocSize = Math.max(1024, newCapacity);
                bufA = new int[allocSize];
                bufB = new int[allocSize];
                densityBuf = new float[allocSize];
                currentSize = allocSize;
            }
            resultWrapper.update(bufA, bufB, densityBuf);
            return resultWrapper;
        }

        void destroy() {
            if (contextPtr != 0) {
                try { destroyCtx(contextPtr); } catch (Throwable e) { AcceleratedRecoiling.LOGGER.error("Failed to destroy ctx", e); }
                contextPtr = 0;
            }
            if (configPtr != 0) {
                try { destroyCfg(configPtr); } catch (Throwable e) { AcceleratedRecoiling.LOGGER.error("Failed to destroy cfg", e); }
                configPtr = 0;
            }
            bufA = null;
            bufB = null;
            densityBuf = null;
        }
    }

    private static final Set<ThreadState> ALL_THREAD_STATES = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<ThreadState> THREAD_STATE = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        ALL_THREAD_STATES.add(state);
        return state;
    });

    @Override
    public void applyConfig() {
        if (!ParallelAABB.isInitialized) return;
        for (ThreadState state : ALL_THREAD_STATES) {
            if (state.configPtr != 0) {
                try {
                    updateCfg(state.configPtr, FoldConfig.maxCollision, FoldConfig.gridSize, FoldConfig.densityWindow, FoldConfig.maxThreads);
                } catch (Throwable e) {
                    AcceleratedRecoiling.LOGGER.error("Failed to update JNI native config", e);
                }
            }
        }
    }

    @Override
    public void destroy() {
        if (!ParallelAABB.isInitialized) return;
        ParallelAABB.isInitialized = false;

        for (ThreadState state : ALL_THREAD_STATES) {
            state.destroy();
        }
        ALL_THREAD_STATES.clear();
        maxSizeTouched.set(-1);
    }

    @Override
    public PushResult push(double[] locations, double[] aabb, int[] resultSizeOut) {
        if (!ParallelAABB.isInitialized) return null;

        ThreadState state = THREAD_STATE.get();
        if (state.contextPtr == 0) return null;

        int count = locations.length / 3;
        int resultSize = locations.length * FoldConfig.maxCollision;
        maxSizeTouched.updateAndGet(current -> Math.max(current, count));

        PushResultJNI collisionPairs = state.reallocOutputBuf(resultSize);

        try {
            int collisionSize = push(
                    aabb,
                    collisionPairs.arrayA,
                    collisionPairs.arrayB,
                    count,
                    collisionPairs.arrayDensity,
                    state.contextPtr,
                    state.configPtr
            );

            resultSizeOut[0] = collisionSize;
            if (collisionSize == -1) return null;

            return collisionPairs;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke JNI push method", e);
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
                logger.info("Extracted JNI native library to temp: {}", dllPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("JNI library load failed: " + e.getMessage(), e);
        }

        try {
            System.load(dllPath);
            logger.info("dll: " + dllPath);
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException("Failed to load JNI library", e);
        }

        JsonObject defaultConfigJson = new JsonObject();
        defaultConfigJson.addProperty("enableEntityCollision", true);
        defaultConfigJson.addProperty("enableEntityGetterOptimization", true);
        defaultConfigJson.addProperty("maxCollision", 32);
        defaultConfigJson.addProperty("gridSize", 1);
        defaultConfigJson.addProperty("densityWindow", 4);
        defaultConfigJson.addProperty("densityThreshold", 16);
        defaultConfigJson.addProperty("maxThreads", Runtime.getRuntime().availableProcessors() / 2);

        File foldConfig = new File("acceleratedRecoiling.jso n");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String defaultConfig = gson.toJson(defaultConfigJson);
        createConfigFile(foldConfig, defaultConfig);

        String configFile;
        try {
            configFile = Files.readString(foldConfig.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to read config, reason: {}. Using default.", e.getMessage());
            foldConfig.deleteOnExit();
            configFile = defaultConfig;
        }

        try {
            JsonObject configJson = JsonParser.parseString(configFile).getAsJsonObject();
            initConfig(configJson);
        } catch (Exception e) {
            logger.warn("Config broken: {}. Overwriting.", e.getMessage());
            foldConfig.deleteOnExit();
            createConfigFile(foldConfig, defaultConfig);
            initConfig(JsonParser.parseString(defaultConfig).getAsJsonObject());
        }

        logger.info("JNI acceleratedRecoiling initialized.");
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