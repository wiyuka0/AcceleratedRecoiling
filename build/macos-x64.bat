@echo off

if not defined AR_TOOLCHAIN (
echo AR toolchain not found.
echo.
pause
exit
)

set "BUILD_TARGET=macos-x64"
set "BUILD_SCRIPT=x86_64-apple-macos-cmake.bat"

set "BUILD_PWD=%~dp0%"
set "BUILD_ROOT=%BUILD_PWD%\.."
set "BUILD_OUT=%BUILD_PWD%\..\out\%BUILD_TARGET%"
mkdir "%BUILD_OUT%"

call "%AR_TOOLCHAIN%\bin\%BUILD_SCRIPT%" -DCMAKE_BUILD_TYPE=Release "%BUILD_ROOT%" -B "%BUILD_OUT%"
call "%AR_TOOLCHAIN%\bin\cmake-build.bat" "%BUILD_OUT%"

echo.
pause