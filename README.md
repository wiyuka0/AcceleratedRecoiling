# 加速碰撞（Accelerated Recoiling） 
加速碰撞是一个作用于服务器的实体碰撞逻辑优化模组，旨在显著降低实体密集区域的碰撞计算开销
通过 Java 21+ 的 Foreign Function & Memory (FFM) API，调用GPU来接管实体AABB碰撞检测，将计算压力从主线程转移。

*本模组目前为实验性质，仍处于测试阶段，可能会出现非预期的表现或错误。请在使用前做好存档备份，并谨慎使用。*

---

## 前置要求

* **Java 21 或更高版本**：FFM API 是 Java 21 的预览功能。**你必须使用 Java 21+ 来启动你的服务器/客户端。**
* **64位操作系统**：本机库（`.dll` / `.so`）需要64位环境。
* **Windows 平台** [**Microsoft Visual C++ 运行库**](https://aka.ms/vs/17/release/vc_redist.x64.exe)：如果启动失败，请首先安装此运行库。
* **Linux 平台**：如果启动失败，请首先安装 **OpenCL** 运行库。
    * 对于 Debian/Ubuntu 系统，通常可以通过 `sudo apt install ocl-icd-libopencl1` 来安装。
    * 对于其他发行版，请使用您的包管理器安装对应的 OpenCL 运行时。
* **OpenCL** 你需要一个兼容的显卡或能正常运行OpenCL的驱动程序。

## 游戏崩溃了！
如果游戏无法正常启动……

* 你的客户端是 Forge 1.20.1，尝试在 JVM 启动参数中添加 `--enable-preview`。
* 你的客户端是 NeoForge 1.21.1/1.21.8，请使用 Java 22+ 启动游戏。
* 如果这些方法都不起作用，请更新此模组，并删除文件 `.minecraft/acceleratedRecoilingLib.dll`，然后重启客户端。（文件位于 `.minecraft/` 或你的客户端根目录下）

## 安装
模组在首次启动时，会自动在服务器/客户端**根目录**下解压 `acceleratedRecoilingLib.dll`（或 `.so`）文件，并创建一个 `acceleratedRecoiling.json` 配置文件。

你可以在服务器/客户端**根目录**（与 `mods文件夹` 同级）找到 `acceleratedRecoiling.json` 文件。

**默认配置：**
```json
{
    "enableEntityCollision": true,
    "enableEntityGetterOptimization": true,
    "gridSize": 8,
    "maxCollision": 32,
    "gpuIndex": 0,
    "useCPU": false
}
```
* enableEntityCollision: 是否启用实体挤压优化
* enableEntityGetterOptimization: 是否启用EntityGetter接口优化
* gridSize 世界分格大小
* maxCollision 每个实体最多与其周围几个实体相互碰撞（受限于OpenCL输出缓冲区）
* gpuIndex 使用的GPU索引
* useCPU 是否使用CPU
  
## 基准测试
#### 测试环境
* **CPU**: Intel Core i5-12600KF
* **内存**: 32GB (分配堆内存 24GB)
* **显卡**: NVIDIA RTX 3060 Ti
* **服务端**: `Leaves 1.21.8-138-master@9331167 (2025-10-18T16:10:30Z)`
* **Java**: Eclipse Adoptium JDK 21
* **启动参数**: `-Dleavesclip.enable.mixin=true -Xmx24G -Xms24G`
* **模组版本** `AcceleratedRecoilingLeaves-0.7-alpha-leaves-all.jar`

#### 测试方法
在同一个区块内的 2x2 空间内生成指定数量的猪，记录服务器 TPS 变化

#### 测试结果

| 实体数量 | LeavesMC + 加速碰撞 (TPS) | LeavesMC (TPS) | 提升倍率 |
| :--- | :--- | :--- | :--- |
| **2,048** | **20.0** | 3.0 | 6.6x  |
| **4,096** | **19.0** | 0.5 | 38x |
| **8,192** | **10.0** | - | - |
| **16,384** | **5.3** | - | - |
| **32,768** | **2.8** | - | - |
| **65,536** | **1.3** | - | - |
| **131,072** | **~0.4** | - | - |
| **294,912** | **~0.2** | - | - |

## 贡献与致谢

非常感谢以下开发者和所有为本项目提交issues与pr的人的帮助与支持！！
*   **[**Argon4W**](https://github.com/Argon4W)**: 提供了该项目的原始构思与核心原理。
*   **[**fireboy637**](https://github.com/fireboy637)**: 提供了该项目的ArchitecturyAPI移植方案与核心代码。
*   **[**hydropuse**](https://github.com/hydropuse)**: 提供了该项目的JDK 22兼容方案与核心代码。

