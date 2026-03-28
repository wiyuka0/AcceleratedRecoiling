#pragma once

#include "export.h"

extern "C" {

AR_EXPORT void* createCtx();

AR_EXPORT void destroyCtx(void* context_ptr);

AR_EXPORT int push(const double *aabbs, int *outputA, int *outputB, int entityCount, float* densityBuf, void* memDataPtrOri, void* configPtr);

}