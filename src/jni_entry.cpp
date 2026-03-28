#ifdef AR_ENABLE_JNI

#include <jni.h>

#include "config.h"
#include "entity_data.h"

extern "C" {

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    return JNI_VERSION_10;
}

JNIEXPORT jlong JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_createCtx(JNIEnv *env, jclass clazz) {
    return reinterpret_cast<jlong>(createCtx());
}

JNIEXPORT void JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_destroyCtx(JNIEnv *env, jclass clazz, jlong ctxPtr) {
    if (ctxPtr != 0) {
        destroyCtx(reinterpret_cast<void*>(ctxPtr));
    }
}

JNIEXPORT jlong JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_createCfg(JNIEnv *env, jclass clazz,
                                                       jint maxCollision, jint gridSize,
                                                       jint densityWindow, jint maxThreads) {
    return reinterpret_cast<jlong>(createCfg(maxCollision, gridSize, densityWindow, maxThreads));
}

JNIEXPORT void JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_updateCfg(JNIEnv *env, jclass clazz, jlong cfgPtr,
                                                       jint maxCollision, jint gridSize,
                                                       jint densityWindow, jint maxThreads) {
    if (cfgPtr != 0) {
        updateCfg(reinterpret_cast<void*>(cfgPtr), maxCollision, gridSize, densityWindow, maxThreads);
    }
}

JNIEXPORT void JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_destroyCfg(JNIEnv *env, jclass clazz, jlong cfgPtr) {
    if (cfgPtr != 0) {
        destroyCfg(reinterpret_cast<void*>(cfgPtr));
    }
}

JNIEXPORT jint JNICALL
Java_com_wiyuka_acceleratedrecoiling_natives_JNIBackend_push(JNIEnv *env, jclass clazz,
                                                  jdoubleArray aabbs_arr,
                                                  jintArray outputA_arr,
                                                  jintArray outputB_arr,
                                                  jint entityCount,
                                                  jfloatArray densityBuf_arr,
                                                  jlong ctxPtr,
                                                  jlong cfgPtr) {
    void* ctx = reinterpret_cast<void*>(ctxPtr);
    void* cfg = reinterpret_cast<void*>(cfgPtr);

    if (!ctx || !cfg || !aabbs_arr || !outputA_arr || !outputB_arr) {
        return 0;
    }

    jboolean isCopy;
    double* aabbs = (double*) env->GetPrimitiveArrayCritical(aabbs_arr, &isCopy);
    int* outputA  = (int*) env->GetPrimitiveArrayCritical(outputA_arr, &isCopy);
    int* outputB  = (int*) env->GetPrimitiveArrayCritical(outputB_arr, &isCopy);
    float* densityBuf = nullptr;
    if (densityBuf_arr != nullptr) {
        densityBuf = (float*) env->GetPrimitiveArrayCritical(densityBuf_arr, &isCopy);
    }

    int collisionCount = push(aabbs, outputA, outputB, entityCount, densityBuf, ctx, cfg);

    if (densityBuf != nullptr) {
        env->ReleasePrimitiveArrayCritical(densityBuf_arr, densityBuf, 0); // 0: 同步修改到 Java
    }
    env->ReleasePrimitiveArrayCritical(outputB_arr, outputB, 0);
    env->ReleasePrimitiveArrayCritical(outputA_arr, outputA, 0);

    env->ReleasePrimitiveArrayCritical(aabbs_arr, aabbs, JNI_ABORT);

    return collisionCount;
}

}

#endif // AR_ENABLE_JNI