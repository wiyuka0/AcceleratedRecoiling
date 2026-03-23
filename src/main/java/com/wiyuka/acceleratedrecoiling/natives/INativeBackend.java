package com.wiyuka.acceleratedrecoiling.natives;

public interface INativeBackend {
    void initialize();
    void applyConfig();
    void destroy();
    String getName();
    PushResult push(double[] locations, double[] aabb, int[] resultSizeOut);
}