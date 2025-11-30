package com.wiyuka.acceleratedrecoiling.api;

public interface ICustomBB {


    void extractionBoundingBox(double[] doubleArray, int offset, double inflate);
    void extractionPosition(double[] doubleArray, int offset);
}