#pragma once

#ifdef AR_WINDOWS
#define AR_EXPORT __declspec(dllexport)
#else
#define AR_EXPORT __attribute__((visibility("default")))
#endif