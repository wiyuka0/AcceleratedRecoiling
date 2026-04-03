package com.wiyuka.acceleratedrecoiling.natives;

import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class NativeInterface {

    public static boolean isVectorApiAvailable() {
        try {
            Class.forName("jdk.incubator.vector.Vector");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getPlatformNativePath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String os;
        if (osName.contains("win")) {
            os = "windows";
        } else if (osName.contains("mac")) {
            os = "macos";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            os = "linux";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }
        String arch;
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            arch = "x64";
        }
        else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = "arm64";
        } else {
            throw new UnsupportedOperationException("Unsupported architecture: " + osArch);
        }
        return "/natives/" + os + "-" + arch + "/";
    }

    public enum BackendType {
        FFM("FFM", () -> loadReflectively("com.wiyuka.acceleratedrecoiling.natives.FFMBackend")),
        JNI("JNI", JNIBackend::new), // 假设 JNI 类兼容所有 JDK
        JAVA_SIMD("Java SIMD", () -> {
            if (!isVectorApiAvailable()) throw new UnsupportedOperationException("Vector API not available");
            return loadReflectively("com.wiyuka.acceleratedrecoiling.natives.JavaSIMDBackend");
        }),
        JAVA_VANILLA("Java Vanilla", JavaVanillaBackend::new),
        JAVA("Pure Java", JavaBackend::new),
        GPU("GPU", () -> loadReflectively("com.wiyuka.acceleratedrecoiling.natives.GPUBackend")),
        AUTO("Auto", null);

        private final String displayName;
        private final Supplier<INativeBackend> loader;

        BackendType(String displayName, Supplier<INativeBackend> loader) {
            this.displayName = displayName;
            this.loader = loader;
        }

        public String getDisplayName() { return displayName; }

        public INativeBackend tryLoad() {
            if (this == AUTO) return null;
            try {
                AcceleratedRecoiling.LOGGER.info("Attempting to load {} backend...", this.displayName);
                INativeBackend instance = loader.get();
                instance.initialize();
                return instance;
            } catch (Throwable t) {
                AcceleratedRecoiling.LOGGER.warn("{} backend failed to load. Reason: {}", this.displayName, t.getMessage());
                return null;
            }
        }
    }

    private static INativeBackend backend;
    private static boolean isInitialized = false;

    private static final List<BackendType> AUTO_FALLBACK_CHAIN = Arrays.asList(
            BackendType.GPU,
            BackendType.FFM,
            BackendType.JNI,
            BackendType.JAVA_SIMD,
            BackendType.JAVA
    );

    public static void initialize() {
        // 1. 读取 JVM 启动参数，例如: -Dacceleratedrecoiling.backend=FFM
        String backendProp = System.getProperty("acceleratedrecoiling.backend");
        BackendType selectedBackend = BackendType.AUTO;
        // 2. 解析玩家指定的参数
        if (backendProp != null && !backendProp.trim().isEmpty()) {
            try {
                // 将字符串转为大写以匹配 Enum (例如 "ffm" -> "FFM")
                selectedBackend = BackendType.valueOf(backendProp.trim().toUpperCase());
                AcceleratedRecoiling.LOGGER.info("User requested backend via JVM argument: {}", selectedBackend.getDisplayName());
            } catch (IllegalArgumentException e) {
                AcceleratedRecoiling.LOGGER.warn("Unknown backend '{}' specified in -Dacceleratedrecoiling.backend. Falling back to AUTO.", backendProp);
            }
        }
        // 3. 如果是 AUTO (用户未指定、指定为 AUTO 或指定错误)，执行原有的自动探测逻辑
        if (selectedBackend == BackendType.AUTO) {
            if (AVX2.hasAVX2()) {
                // 这里你可以指定最高性能的后端，比如 FFM 或 JNI
                selectedBackend = BackendType.FFM;
            } else if (isVectorApiAvailable()) {
                selectedBackend = BackendType.JAVA_SIMD;
            } else {
                selectedBackend = BackendType.JAVA;
            }
            AcceleratedRecoiling.LOGGER.info("Auto-selected backend: {}", selectedBackend.getDisplayName());
        }
        // 4. 执行初始化 (假设你有一个接受 BackendType 的 initialize 方法)
        initialize(selectedBackend);
    }

    public static void initialize(BackendType preferredType) {
        if (isInitialized) return;

        AcceleratedRecoiling.LOGGER.info("Initializing NativeInterface with preferred backend: {}", preferredType);

        backend = getBackend(preferredType);

        if (backend != null) {
            AcceleratedRecoiling.LOGGER.info("Successfully selected and initialized backend: {}", backend.getName());
            isInitialized = true;
        } else {
            throw new IllegalStateException("Failed to initialize ANY backend!");
        }
    }

    private static INativeBackend getBackend(BackendType preferredType) {
        INativeBackend instance = null;

        if (preferredType != BackendType.AUTO) {
            instance = preferredType.tryLoad();
            if (instance != null) return instance;

            AcceleratedRecoiling.LOGGER.warn("Preferred {} backend failed. Falling back to AUTO chain...", preferredType.getDisplayName());
        }

        AcceleratedRecoiling.LOGGER.info("Detected Java Version: {}", Runtime.version().feature());

        for (BackendType type : AUTO_FALLBACK_CHAIN) {
            if (type == preferredType) continue;

            if (type == BackendType.FFM && Runtime.version().feature() < 21) continue;

            instance = type.tryLoad();
            if (instance != null) return instance;
        }

        return null;
    }

    private static INativeBackend loadReflectively(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (INativeBackend) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Reflection load failed for " + className, e);
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