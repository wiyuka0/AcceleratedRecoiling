# 加速碰撞 (Accelerated Recoiling)

加速碰撞是一个专注于优化服务端实体碰撞逻辑的模组。它利用 Java 21+ 的 FFM (Foreign Function & Memory) API 接管实体 AABB 碰撞检测，将高密集计算压力转移至 C++ 原生库，从而显著提升服务器性能。

**本模组目前为实验性质，实体挤压表现与原版不完全一致。请务必在做好存档备份的前提下谨慎使用。**

官方交流群：1023713677

## 环境要求与前置

*   **Java 21 或以上**：必须使用 Java 21+ 启动游戏/服务端（FFM API 为 Java 21 预览功能）。
*   **64位操作系统**：本机库 (`.dll` / `.so`) 仅支持 64 位环境。
*   **Windows 平台**：需安装 [Microsoft Visual C++ 运行库](https://aka.ms/vs/17/release/vc_redist.x64.exe)（如启动失败请优先安装）。
*   **Leaves 端**：启动参数中必须包含 `-Dleavesclip.enable.mixin=true`。

## 故障排查 (游戏崩溃)

*   **NeoForge 1.21.1/1.21.8**：请使用 Java 22+ 启动客户端。
*   **Leaves 环境**：检查是否遗漏了上述的 `enable.mixin` 启动参数。
*   **其他问题**：尝试更新模组，并删除根目录或 `.minecraft` 目录下的 `acceleratedRecoilingLib.dll`，然后重启游戏。

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
*   安装 JDK 21。
*   安装 Visual Studio 2022，勾选“使用 C++ 的桌面开发”。
*   在 WSL 中安装编译工具：`sudo apt update && sudo apt install build-essential libgomp1 -y`

**2. 修改脚本路径**
打开 `build.gradle.kts`，找到 `compileNativeLib` 任务，将 `vcvarsScript` 变量的值替换为你本机实际的 `vcvars64.bat` 路径。

**3. 构建**
在项目根目录运行以下命令：
```bash
gradlew shadowJar
```
编译产物 (包含双端动态库的 Jar) 将生成在 `build/libs/` 目录下。

## 鸣谢与开源协议

本项目基于 **MIT 协议** 开源。
特别感谢以下开发者对本项目的核心思路与代码移植提供的巨大帮助：
*   **[Argon4W](https://github.com/Argon4W)**: 原始构思与核心思路。
*   **[fireboy637](https://github.com/fireboy637)**: Architectury API 移植方案的核心代码。
*   **[hydropuse](https://github.com/hydropuse)**: JDK 22 兼容方案的核心代码。
