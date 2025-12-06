# 加速碰撞（Accelerated Recoiling） 
加速碰撞是一个作用于服务器的实体碰撞逻辑优化模组，旨在显著降低实体的碰撞计算开销
通过 Java 21+ 的 Foreign Function & Memory (FFM) API来接管实体AABB碰撞检测，将计算压力从Java转移。

*本模组目前为实验性质，仍处于测试阶段，可能会出现非预期的表现或错误。请在使用前做好存档备份，并谨慎使用。*

---

## 前置要求

* **Java 21 或更高版本**：FFM API 是 Java 21 的预览功能。**你必须使用 Java 21+ 来启动你的服务器/客户端。**
* **64位操作系统**：本机库（`.dll` / `.so`）需要64位环境。
* **Windows 平台** [**Microsoft Visual C++ 运行库**](https://aka.ms/vs/17/release/vc_redist.x64.exe)：如果启动失败，请首先安装此运行库。
* **LeavesMC** 如果你用的是加速碰撞的LeavesMC移植版，请确保服务器的启动参数中包含 `-Dleavesclip.enable.mixin=true`

## 游戏崩溃了！
如果游戏无法正常启动……

* 你的客户端是 Forge 1.20.1，尝试在 JVM 启动参数中添加 `--enable-preview`。
* 你的客户端是 NeoForge 1.21.1/1.21.8，请使用 Java 22+ 启动游戏。
* 你的环境是 LeavesMC，请确保服务器的启动参数中包含 `-Dleavesclip.enable.mixin=true`。
* 如果这些方法都不起作用，请更新此模组，并删除文件 `.minecraft/acceleratedRecoilingLib.dll`，然后重启客户端。（文件位于 `.minecraft/` 或你的客户端根目录下）

## 安装
模组在首次启动时，会自动在服务器/客户端**根目录**下解压 `acceleratedRecoilingLib.dll`（或 `.so`）文件，并创建一个 `acceleratedRecoiling.json` 配置文件。

你可以在服务器/客户端**根目录**（与 `mods文件夹` 同级）找到 `acceleratedRecoiling.json` 文件。

**默认配置：**
```json
{
    "enableEntityCollision": true,
    "enableEntityGetterOptimization": true,
    "maxCollision": 32
}
```
* enableEntityCollision: 是否启用实体挤压优化
* enableEntityGetterOptimization: 是否启用EntityGetter接口优化 (暂时无效)
* maxCollision: 每个实体最多与其周围几个实体相互碰撞
  
## 基准测试
#### 测试环境
* **CPU**: Intel Core i5-12600KF
* **内存**: 32GB (分配堆内存 24GB)
* **显卡**: NVIDIA RTX 3060 Ti
* **服务端**: `Leaves 1.21.8-138-master@9331167 (2025-10-18T16:10:30Z)`
* **Java**: GraalVM JDK 21
* **启动参数**: `-Dleavesclip.enable.mixin=true -Xmx24G -Xms24G`
* **模组版本**: `AcceleratedRecoilingLeaves-0.7.3-alpha-leaves-all.jar`
* **使用配置**: 默认

#### 测试方法
在同一个区块内的 2x2 空间内生成指定数量的猪，记录服务器 TPS 变化

#### 测试结果

| 实体数量 | LeavesMC + 加速碰撞 (TPS) | LeavesMC (TPS) | 提升倍率 |
| :--- | :--- | :--- | :--- |
| **2,048** | **20.0** (16 MSPT) | 3.0 | 20.8x  |
| **4,096** | **20.0** (27 MSPT) | 0.5 | 74x |
| **8,192** | **19.7** (51 MSPT) | - | - |
| **16,384** | **8.6** (115 MSPT) | - | - |
| **32,768** | **4.3** (230 MSPT) | - | - |

## 贡献与致谢

非常感谢以下开发者和所有为本项目提交issues与pr的人的帮助与支持！！
*   **[**Argon4W**](https://github.com/Argon4W)**: 提供了该项目的原始构思与核心原理。
*   **[**fireboy637**](https://github.com/fireboy637)**: 提供了该项目的ArchitecturyAPI移植方案与核心代码。
*   **[**hydropuse**](https://github.com/hydropuse)**: 提供了该项目的JDK 22兼容方案与核心代码。

