#include "statistics.h"

#include <chrono>
#include <string_view>
#include <string>

void log(std::string_view str) {
    // std::cout << str << "\n";
    // fflush(stdout);
}

static auto startTime = std::chrono::high_resolution_clock::now();
static auto endTime = std::chrono::high_resolution_clock::now();

void start() {
    startTime = std::chrono::high_resolution_clock::now();
}
void stop() {
    endTime = std::chrono::high_resolution_clock::now();
}

double duration() {
    double duration_microsecond = std::chrono::duration<double, std::milli>(endTime - startTime).count();
    return duration_microsecond;
}

void logTime(const std::string &str) {
    // std::cout << str << ": " << duration() << "ms\n";
    log(str + ": " + std::to_string(duration()) + "ms\n");
}