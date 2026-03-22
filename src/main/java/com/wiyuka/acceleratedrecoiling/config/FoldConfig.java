package com.wiyuka.acceleratedrecoiling.config;

public class FoldConfig {
    public static boolean enableEntityCollision = true;
    public static boolean enableEntityGetterOptimization = true;
    public static int maxCollision = 32;

    public static int gridSize = 2;
    public static int densityWindow = 4;

    public final static boolean debugDensity = false;
    public static int densityThreshold = 16;
    public static int maxThreads = Runtime.getRuntime().availableProcessors() /2;
}
