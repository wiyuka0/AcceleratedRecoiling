#pragma once

#include "export.h"

struct Config
{
    int maxCollision;
    int gridSize;
    int densityWindow;
    int maxThreads;
};

extern "C" {

AR_EXPORT void* createCfg(int maxCollision, int gridSize, int densityWindow, int maxThreads);

AR_EXPORT void updateCfg(void* configPtr, int maxCollision, int gridSize, int densityWindow, int maxThreads);

AR_EXPORT void destroyCfg(void* configPtr);

}