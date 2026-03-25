package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;

public class NativeInterface {
    public static boolean isVectorApiAvailable() {
        try {
            Class.forName("jdk.incubator.vector.Vector");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public enum BackendType {
        AUTO,   // 自动选择后端
        FFM,    // 强制尝试 FFM (Java 21+)
        JNI,    // 强制尝试 JNI
        JAVA_SIMD,
        JAVA,    // 强制使用 Java
        JAVA_VANILLA
    }

    private static INativeBackend backend;
    private static boolean isInitialized = false;

    public static void initialize() {

        initialize(BackendType.JAVA_VANILLA);
        try {
            if (AVX2.hasAVX2()) initialize(BackendType.AUTO);
            else {
                if(isVectorApiAvailable()) initialize(BackendType.JAVA_SIMD);
                else initialize(BackendType.JAVA);
            }
        } catch (Throwable e) {
                                initialize(BackendType.JAVA);
        }

//        initialize(BackendType.JAVA_SIMD);
    }

    public static void initialize(BackendType preferredType) {
        if (isInitialized) return;

        AcceleratedRecoiling.LOGGER.info("Initializing NativeInterface with preferred backend: {}", preferredType);

        backend = getBackend(preferredType);

        if (backend != null) {
            AcceleratedRecoiling.LOGGER.info("Successfully selected and initialized backend: " + backend.getName());
            isInitialized = true;
        } else {
            throw new IllegalStateException("Failed to initialize ANY backend!");
        }
    }

    private static INativeBackend getBackend(BackendType preferredType) {
        INativeBackend instance = null;
        if (preferredType == BackendType.FFM) {
            instance = tryLoadFFM();
            if (instance != null) return instance;
            AcceleratedRecoiling.LOGGER.warn("Preferred FFM backend failed. Falling back to AUTO...");
        } else if (preferredType == BackendType.JNI) {
            instance = tryLoadJNI();
            if (instance != null) return instance;
            AcceleratedRecoiling.LOGGER.warn("Preferred JNI backend failed. Falling back to AUTO...");
        } else if (preferredType == BackendType.JAVA_SIMD) {
            instance = tryLoadJavaSIMD();
            if (instance != null) return instance;
            AcceleratedRecoiling.LOGGER.warn("Preferred Java SIMD backend failed. Falling back to AUTO...");
        } else if (preferredType == BackendType.JAVA_VANILLA) {
            instance = tryLoadJavaVanilla();
            if (instance != null) return instance;
            AcceleratedRecoiling.LOGGER.warn("Preferred Java Vanilla backend failed. Falling back to AUTO...");

        }
        else if (preferredType == BackendType.JAVA) {
            return tryLoadJava();
        }

        int javaVersion = Runtime.version().feature();
        AcceleratedRecoiling.LOGGER.info("Detected Java Version: {}", javaVersion);

        // 尝试 FFM
        if (javaVersion >= 21) {
            instance = tryLoadFFM();
            if (instance != null) return instance;
        }

        // 尝试 JNI
        instance = tryLoadJNI();
        if (instance != null) return instance;

        // 最终回退到纯 Java
        AcceleratedRecoiling.LOGGER.info("Falling back to Pure Java backend...");
        return tryLoadJava();
    }

    private static INativeBackend tryLoadFFM() {
        try {
            AcceleratedRecoiling.LOGGER.info("Attempting to load FFM backend...");
            Class<?> ffmClass = Class.forName("com.wiyuka.acceleratedrecoiling.natives.FFMBackend");
            INativeBackend ffmInstance = (INativeBackend) ffmClass.getDeclaredConstructor().newInstance();
            ffmInstance.initialize();
            return ffmInstance;
        } catch (Throwable t) {
            AcceleratedRecoiling.LOGGER.warn("FFM backend failed to load. Reason: {}", t.getMessage());
            return null;
        }
    }

    private static INativeBackend tryLoadJNI() {
        try {
            AcceleratedRecoiling.LOGGER.info("Attempting to load JNI backend...");
            INativeBackend jniInstance = new JNIBackend();
            jniInstance.initialize();
            return jniInstance;
        } catch (Throwable t) {
            AcceleratedRecoiling.LOGGER.warn("JNI backend failed to load. Reason: {}", t.getMessage());
            return null;
        }
    }

    private static INativeBackend tryLoadJava() {
        try {
            AcceleratedRecoiling.LOGGER.info("Attempting to load Java backend...");
            INativeBackend javaInstance = new JavaBackend();
            javaInstance.initialize();
            return javaInstance;
        } catch (Throwable t) {
            AcceleratedRecoiling.LOGGER.error("Java backend failed to load. Reason: {}", t.getMessage());
            return null;
        }
    }

    private static INativeBackend tryLoadJavaSIMD() {
        try {
            AcceleratedRecoiling.LOGGER.info("Attempting to load Java SIMD backend...");
            INativeBackend javaInstance = new JavaSIMDBackend();
            javaInstance.initialize();
            return javaInstance;
        } catch (Throwable t) {
            AcceleratedRecoiling.LOGGER.error("Java SIMD backend failed to load. Reason: {}", t.getMessage());
            return null;
        }
    }
    private static INativeBackend tryLoadJavaVanilla() {
        try {
            AcceleratedRecoiling.LOGGER.info("Attempting to load Java Vanilla backend...");
            INativeBackend javaInstance = new JavaVanillaBackend();
            javaInstance.initialize();
            return javaInstance;
        } catch (Throwable t) {
            AcceleratedRecoiling.LOGGER.error("Java Vanilla backend failed to load. Reason: {}", t.getMessage());
            return null;
        }
    }

    public static void applyConfig() {
        if (backend != null) {
            backend.applyConfig();
        }
    }

    public static void destroy() {
        if (backend != null) {
            backend.destroy();
            backend = null;
        }
        isInitialized = false;
    }

    public static PushResult push(double[] locations, double[] aabb, int[] resultSizeOut) {
        if (backend == null) {
            resultSizeOut[0] = 0;
            return null;
        }
        return backend.push(locations, aabb, resultSizeOut);
    }
}