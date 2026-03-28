#include "config.h"

#include "parallel_executor.h"

void* createCfg(int maxCollision, int gridSize, int densityWindow, int maxThreads) {
    return new Config {
        maxCollision, gridSize, densityWindow, maxThreads
    };
}

void updateCfg(void* configPtr, int maxCollision, int gridSize, int densityWindow, int maxThreads) {
    if (configPtr) {
        Config* cfg = static_cast<Config*>(configPtr);
        cfg->maxCollision = maxCollision;
        cfg->gridSize = gridSize;
        cfg->densityWindow = densityWindow;
        cfg->maxThreads = maxThreads;
    }
}

void destroyCfg(void* configPtr) {
    if (configPtr) {
        delete static_cast<Config*>(configPtr);
    }
}