package com.wiyuka.acceleratedrecoiling.natives;


import com.wiyuka.acceleratedrecoiling.AcceleratedRecoiling;

public class NativeInterface {

    private static INativeBackend backend;
    private static boolean isInitialized = false;

    public static void initialize() {
        if (isInitialized) return;

        int javaVersion = Runtime.version().feature();
        AcceleratedRecoiling.LOGGER.info("Detected Java Version: {}", javaVersion);

        if (javaVersion >= 21) {
            try {
                AcceleratedRecoiling.LOGGER.info("Loading FFM backend...");

                Class<?> ffmClass = Class.forName("com.wiyuka.acceleratedrecoiling.natives.NativeInterfaceFFM");
                backend = (INativeBackend) ffmClass.getDeclaredConstructor().newInstance();

                backend.initialize();
                AcceleratedRecoiling.LOGGER.info("Current backend: FFM");
                isInitialized = true;
                return;

            } catch (Throwable t) {

                AcceleratedRecoiling.LOGGER.warn("Falling back to JNI. Reason: {}", t.getMessage());
            }
        }

        AcceleratedRecoiling.LOGGER.info("Loading JNI backend...");
        backend = new NativeInterfaceJNI();
        backend.initialize();
        AcceleratedRecoiling.LOGGER.info("Current backend: JNI");

        isInitialized = true;
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
            return null;
        }
        return backend.push(locations, aabb, resultSizeOut);
    }
}