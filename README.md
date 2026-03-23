# 加速碰撞 (Accelerated Recoiling)

加速碰撞是一个专注于优化服务端实体碰撞逻辑的模组。它利用 FFM (Foreign Function & Memory) API/JNI 接管实体 AABB 碰撞检测，将高密集计算压力转移至 C++ 原生库，从而显著提升服务器性能。

**本模组目前为实验性质，实体挤压表现与原版不完全一致。请务必在做好存档备份的前提下谨慎使用。**

官方交流群：1023713677


## 特性介绍

*   **实体碰撞性能提升**：通过将底层的碰撞逻辑交由 C++ 处理，打破 Java 在处理海量实体碰撞时的性能瓶颈。在测试条件下（同一区块数千实体），TPS 提升可达数十倍。
*   **算法优化**：引入效率更高的碰撞算法，避免了原版 <math>O(N^2)</math> 复杂度的实体遍历，降低 CPU 负担。
*   **阈值触发**：只有当局部实体密度达到设定阈值时才会使用 C++ 加速算法，保证常规游戏场景下的稳定与原版体验。
*   **双端原生支持**：内建 Windows (`.dll`) 与 Linux (`.so`) 的动态链接库，面板服、VPS 还是 Docker 容器，都能一键部署。
*   **动态后端选择机制**：
*   * 同时包含多个后端并自动选择当前可以应用且效率最高的后端，如
    * * FFM  （使用FFM API与C++层进行通信）
      * JNI  （使用JNI与C++层进行通信）
      * Java （使用加速碰撞的Java原生算法 **注：MacOS系统将被fallback到此后端**）
      * GPU  （使用GPU算法加速碰撞 *Work In Progress*）

## 环境要求与前置

*   **Java 17 或以上**：必须使用 Java 17 或 Java 17+ 启动游戏/服务端。
*   **64位操作系统**：本机库 (`.dll` / `.so`) 仅支持 64 位环境。
*   **Windows 平台**：需安装 [Microsoft Visual C++ 运行库](https://aka.ms/vs/17/release/vc_redist.x64.exe)（如启动失败请优先安装）。
*   **Leaves 端**：启动参数中必须包含 `-Dleavesclip.enable.mixin=true`。
*   **Linux/Docker**: 需确保系统中已安装 `libgomp1` 依赖。

## 安装与配置

首次启动时，模组会自动在根目录释放本机库文件，并生成 `acceleratedRecoiling.json` 配置文件。

**默认配置及说明：**
```json
{
   "enableEntityCollision": true,      // 是否启用实体挤压优化
   "enableEntityGetterOptimization": true, // 启用EntityGetter接口优化(暂时无效)
   "maxCollision": 32,                 // 单个实体最大碰撞交互数
   "gridSize": 1,                      // 算法网格大小
   "densityWindow": 4,                 // 密度平滑窗口
   "densityThreshold": 16              // 触发加速碰撞的周围实体密度阈值
}
```
*注：若开启后性能不升反降，请尝试调低 `densityThreshold`。*

## 常见问题 (Q&A)

**Q: 为什么游戏崩溃或无法启动？** <br>
**A:** 请按以下步骤排查：
1. 确认已正确安装 Java 17+
3. 若使用 **Leaves** 服务端，确保启动参数包含 `-Dleavesclip.enable.mixin=true`。
4. 如果更新过模组，尝试删除根目录或 `.minecraft` 下的 `acceleratedRecoilingLib.dll`与`acceleratedRecoiling.json`，然后重启游戏让其重新生成。

**Q: 开启后实体挤压表现和原版一样吗？** <br>
**A:** 不完全一致。本模组目前为实验性质，改变了底层计算逻辑，因此挤压表现会与原版有差异。**请务必在使用前做好存档备份。**

**Q: 会影响生电特性吗？** <br>
**A:** 目前不清楚，可能会影响与实体挤压有关的红石机器，**请务必在使用前做好存档备份。**

**Q: 为什么开启模组后，服务器性能反而下降了？** <br>
**A:** 可能是周围实体密度未达到触发优化的条件，因此同时走了原版和加速碰撞的两条路径。请尝试打开配置文件，适当调低 `densityThreshold` 的数值。或尝试调低`maxThreads`。

**Q: 在 Docker 中运行服务端时，报错提示找不到 `libgomp.so` 怎么办？** <br>
**A:** Docker 中使用的 Ubuntu 镜像不包含 `libgomp.so`，因此只需在构建镜像的 Dockerfile 中添加以下命令并重新构建镜像即可：
```dockerfile
RUN apt-get update && \
    apt-get install -y libgomp1
```

**Q: FFM 是Java 21的预览功能，我是否应该使用Java 21+启动游戏？** <br>
**A:** 如果你的游戏版本是*1.21.1*以上，那么是的，但这是**1.21.1**版本本身需要**Java 21**来运行。 <br>
如果你的游戏是*1.20.1*，那么不需要，加速碰撞*v0.10.0-alpha-1.20.1*以上的版本**同时支持最新版JDK到Java 17**之间的任意JDK版本。 <br> （* 注: 1.20.1 的最低可运行 Java 即为 Java 17 *）

## 性能基准测试

**测试环境:** i5-12600KF | 32GB RAM | RTX 3060 Ti | Leaves 1.21.8 | GraalVM JDK 21

**测试一：TPS 变化 (同一区块 2x2 空间内生成实体)**
| 实体数量 | Leaves + 加速碰撞 | 原版 Leaves | 提升倍率 |
| :--- | :--- | :--- | :--- |
| **2,048** | **20.0 TPS** (16 MSPT) | 3.0 TPS | 20.8x |
| **4,096** | **20.0 TPS** (27 MSPT) | 0.5 TPS | 74x |
| **16,384** | **8.6 TPS** (115 MSPT) | - | - |
| **32,768** | **4.3 TPS** (230 MSPT) | - | - |

**测试二：BroadPhase 耗时 (纯 C++ 端处理性能)**
| 实体数量 | MS / Frame (每帧耗时) | 等效 FPS |
| :--- | :--- | :--- |
| 10,000 | 0.2 ms | 5000 |
| 50,000 | 1.1 ms | 909 |
| 100,000 | 2.5 ms | 400 |
| 400,000 | 21.3 ms | 46 |

## 开发计划 (TODO)
*   兼容 MacOS 
*   Luminol 支持

## 源码编译

项目通过 Gradle 调用 MSVC 和 WSL 进行双端跨平台编译 (生成 `.dll` 和 `.so`)，建议在装有 WSL 的 Windows 10/11 环境下操作。

**1. 环境准备**
*   安装 JDK 21。(必须)
*   安装 Visual Studio 2022，勾选“使用 C++ 的桌面开发”。
*   在 WSL 中安装编译工具：`sudo apt update && sudo apt install build-essential libgomp1 -y`

**2. 修改脚本路径**
打开 `build.gradle.kts`，找到 `compileNativeLib` 任务，将 `vcvarsScript` 变量的值替换为你本机实际的 `vcvars64.bat` 路径。

**3. 构建**
在项目根目录运行以下命令：
```bash
gradlew jar
gradlew build
```
编译产物 (包含双端动态库的 Jar) 将生成在 `build/libs/` 目录下。

## 鸣谢与开源协议

本项目基于 **MIT 协议** 开源。
特别感谢以下开发者对本项目的核心思路与代码移植提供的巨大帮助：
*   **[Argon4W](https://github.com/Argon4W)**: 原始构思与核心思路。
*   **[fireboy637](https://github.com/fireboy637)**: Architectury API 移植方案的核心代码。
*   **[hydropuse](https://github.com/hydropuse)**: JDK 22 兼容方案的核心代码。
*   **[wellcoming](https://github.com/wellcoming)**: Docker Ubuntu镜像解决方案。
