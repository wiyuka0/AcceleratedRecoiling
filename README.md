# 加速碰撞（Accelerated Recoiling） 
加速碰撞是一个作用于服务器的实体碰撞逻辑优化模组，旨在显著降低实体密集区域的碰撞计算开销
通过 Java 21+ 的 Foreign Function & Memory (FFM) API，调用GPU来接管实体AABB碰撞检测，将计算压力从主线程转移。

---

## 前置要求

* **Java 21 或更高版本**：FFM API 是 Java 21 的正式功能。**你必须使用 Java 21+ 来启动你的服务器/客户端。**
* **64位操作系统**：本机库（`.dll` / `.so`）需要64位环境。
* **Windows 平台** [**Microsoft Visual C++ 运行库**](https://aka.ms/vs/17/release/vc_redist.x64.exe)：如果启动失败，请首先安装此运行库。
* **Linux 平台**：如果启动失败，请首先安装 **OpenCL** 运行库。
    * 对于 Debian/Ubuntu 系统，通常可以通过 `sudo apt install ocl-icd-libopencl1` 来安装。
    * 对于其他发行版，请使用您的包管理器安装对应的 OpenCL 运行时。
* **OpenCL** 你需要一个兼容的显卡或能正常运行OpenCL的驱动程序。

* **如果游戏/服务器无法启动，请首先尝试添加JVM参数 `--enable-preview`**

---

## 安装
模组在首次启动时，会自动在服务器/客户端**根目录**下解压 `acceleratedRecoilingLib.dll`（或 `.so`）文件，并创建一个 `acceleratedRecoiling.json` 配置文件。

你可以在服务器/客户端**根目录**（与 `mods文件夹` 同级）找到 `acceleratedRecoiling.json` 文件。

如果客户端无法启动，**请首先检查JVM参数中是否`--enable-preview`参数。**

**默认配置：**
```json
{
    "useFold": true, 
    "gridSize": 8,
    "maxCollision": 32, 
    "gpuIndex": 0, 
    "useCPU": false 
}
```
* useFold 是否启用优化 (游戏内可以通过`/togglefold`来切换)
* gridSize 世界分格大小
* maxCollision 每个实体最多与其周围几个实体相互碰撞（受限于OpenCL输出缓冲区）
* gpuIndex 使用的GPU索引
* useCPU 是否使用CPU
<img width="1364" height="588" alt="image" src="https://github.com/user-attachments/assets/4aac1540-3646-4486-a0f2-8d8ddab89d9c" />
