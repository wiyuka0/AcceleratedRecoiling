package com.wiyuka.acceleratedrecoiling.api;

public interface ICustomBB {

    int getNativeId();
    void setNativeId(int id);

    void extractionBoundingBox(double[] doubleArray, int offset, double inflate);
    void extractionPosition(double[] doubleArray, int offset);
}