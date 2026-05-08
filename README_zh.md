# ViaBedrockUtility

Fabric 客户端 Mod，为通过 [ViaProxy](https://github.com/ViaVersion/ViaProxy) + [ViaBedrock](https://github.com/RaphiMC/ViaBedrock) 连接 Bedrock 服务器的 Java 版客户端提供增强渲染能力。

灵感来源于 [BedrockSkinUtility](https://github.com/Camotoy/BedrockSkinUtility)，部分代码复用自 ViaBedrock。

[English](README.md)

## 功能

- **自定义实体渲染** — 解析 Bedrock 几何模型、渲染控制器和骨骼动画，在 Java 版客户端渲染 Bedrock 自定义实体
- **自定义玩家皮肤** — 支持 Bedrock 自定义几何皮肤模型、披风和动态皮肤覆盖（眨眼动画等）
- **Bedrock Camera API** — 相机预设、位置/旋转缓动、淡入淡出、相机抖动
- **动画 LOD 系统** — 基于距离的动画细节层级，优化远距离实体的性能

## 整体架构

```
Bedrock 服务器
    ↓ Bedrock Protocol
ViaProxy (代理)
    ├── ViaBedrock (协议转换)
    │       ↓ Custom Payload
    └── ViaBedrockUtility (Fabric Mod, 本项目)
            ├── 自定义实体渲染
            ├── 自定义玩家皮肤
            └── Bedrock Camera API
                    ↓
                BECamera (Fabric Mod, 相机库)
```

### 通信链路

ViaBedrock 在代理端完成 Bedrock → Java 协议转换，同时将 Java 版无法原生表达的数据（自定义实体模型、Bedrock 皮肤、相机指令等）通过 **Custom Payload** 通道转发给客户端 Mod：

| 通道 | 方向 | 用途 |
|------|------|------|
| `viabedrockutility:data` | S→C | 实体模型请求、动画数据、皮肤/披风数据 |
| `becamera:data` | S→C | 相机预设、指令、抖动 |

## 模块说明

### 自定义实体渲染

| 组件 | 说明 |
|------|------|
| `CustomEntityTicker` | 每个自定义实体的状态机，管理 MoLang 作用域、渲染控制器评估、动画条件计算 |
| `CustomEntityRenderer` | 实体渲染器，管理 Animator 实例，支持多模型混合动画 |
| `CustomEntityModel` | 模型包装，支持 Bedrock 骨骼可见性控制 |
| `McBoneModel` / `ModelPartBoneTarget` | 适配层，将 Minecraft ModelPart 包装为 BedrockMotion 的 IBoneModel/IBoneTarget |
| `GeometryUtil` | Bedrock 几何模型 → Minecraft Model 转换，处理坐标系差异和 UV 缩放 |

**渲染流程**：
1. ViaBedrock 发送 `MODEL_REQUEST` payload（实体标识、变体、皮肤 ID 等）
2. `CustomEntityTicker` 根据实体定义评估渲染控制器，确定当前使用的模型/材质/纹理
3. `CustomEntityRenderer` 每帧评估动画条件（MoLang 表达式作为混合权重），驱动骨骼动画
4. 通过 LOD 系统按距离降低远处实体的动画更新频率

### 自定义玩家皮肤

| 组件 | 说明 |
|------|------|
| `PayloadHandler` | 管理皮肤数据的分块接收和组装 |
| `CustomPlayerRenderer` | 自定义玩家渲染器，支持自定义纹理和动画覆盖 |
| `AnimatedSkinOverlay` | 动态皮肤覆盖，支持 LINEAR（逐帧）和 BLINKING（自动眨眼）两种播放模式 |
| `PlayerAnimationManager` | 管理玩家的 Bedrock 动画覆盖，处理与原版动画的轴向清除 |
| `PlayerSkinBuilder` | 跨版本兼容的 SkinTextures 构建器 |

**皮肤数据流**：
1. `SKIN_INFORMATION` — 皮肤尺寸、几何 JSON、分块数
2. `SKIN_DATA` × N — 分块传输 RGBA 纹理数据
3. `CAPE` — 披风纹理（可选）
4. `SKIN_ANIMATION_INFO` + `SKIN_ANIMATION_DATA` — 动态覆盖层（可选）

### Bedrock Camera API

| 组件 | 说明 |
|------|------|
| `CameraPayloadHandler` | 处理相机指令、抖动和预设 |
| `CameraPresetsPayload` | 解码服务端注册的相机预设（含 parent 继承） |
| `CameraInstructionPayload` | 相机设置（位置/旋转/缓动）、清除、淡入淡出 |
| `CameraShakePayload` | 位置型/旋转型相机抖动 |

底层由 [BECamera](https://github.com/EaseCation/BECamera) 库实现相机状态管理和 Mixin 注入。

### 配置系统

通过 ModMenu 或 Sodium 提供配置界面：

- **动画 LOD 预设**：HIGH_QUALITY（无 LOD）、BALANCED、PERFORMANCE、CUSTOM
- **自定义分级**：按距离阈值和帧间隔控制动画更新频率

## 依赖链

```
CubeConverter          ← Bedrock 几何模型解析
    ↓
BedrockMotion          ← 骨骼动画引擎 (MoLang/动画控制器/渲染控制器)
    ↓
ViaBedrockUtility      ← 本项目
    ↓
BECamera               ← Bedrock Camera API 库 (相机控制/路径插值)
```

| 依赖 | 版本 | 来源 | 说明 |
|------|------|------|------|
| [CubeConverter](https://github.com/EaseCation/CubeConverter) | 1.3 | mavenLocal / JitPack | Bedrock 几何模型解析 |
| [BedrockMotion](https://github.com/EaseCation/BedrockMotion) | 1.0.0 | mavenLocal / JitPack | 骨骼动画引擎 |
| [BECamera](https://github.com/EaseCation/BECamera) | 1.2.0 | mavenLocal | Bedrock Camera API |
| Fabric API | 0.119.5 ~ 0.141.3 | Maven | 按 MC 版本变化 |
| Fabric Loader | ≥0.18.0 | Maven | |
| Lombok | 1.18.36 | Maven | 编译期注解处理 |

## 支持的 Minecraft 版本

使用 [Stonecutter](https://stonecutter.kikugie.dev/) 多版本构建：

**1.21.5** · **1.21.6** · **1.21.7** · **1.21.8** · **1.21.9** · **1.21.10** · **1.21.11**

活动开发版本为 **1.21.11**，通过 `//? if >=1.21.9 {` 注释语法实现版本条件编译。

## 搭配使用

### 必需组件

1. **ViaProxy** — 代理服务器，Java 客户端通过它连接 Bedrock 服务器
2. **ViaBedrock** — ViaProxy 插件，执行 Java ↔ Bedrock 协议转换
3. **ViaBedrockUtility** — 本 Mod，安装在 Fabric 客户端

### 可选组件

- **BECamera** — 如需 Bedrock Camera API 支持（已作为依赖自动引入）
- **ModMenu** / **Cloth Config** — 图形化配置界面
- **Sodium** — 1.21.11+ 可集成 Sodium 配置面板

### 安装

1. 安装 [Fabric Loader](https://fabricmc.net/) 和 Fabric API
2. 将 `viabedrockutility-mc<版本>-1.0.0.jar` 和 `bedrockcameralib-mc<版本>-1.2.0.jar` 放入客户端 `mods/` 目录
3. 配置 ViaProxy 连接 Bedrock 服务器
4. 启动游戏，通过 ViaProxy 代理连接

> **注意**：选择与你的 Minecraft 客户端版本**对应**的 jar，不要使用根目录的 `viabedrockutility-1.0.0.jar`（那是基础版本 1.21.5 的）。版本化 jar 在 `versions/<MC版本>/build/libs/` 下。

## 构建

### 前置条件

- JDK 21+
- 依赖项目已发布到 mavenLocal（见下方说明）

### 本地构建

```bash
# 1. 构建并发布 CubeConverter
cd CubeConverter && ./gradlew publishToMavenLocal

# 2. 构建并发布 BedrockMotion
cd BedrockMotion && ./gradlew publishToMavenLocal

# 3. 构建并发布 BECamera
cd BECamera && ./gradlew publishToMavenLocal

# 4. 构建 ViaBedrockUtility（所有 MC 版本）
cd ViaBedrockUtility && ./gradlew chiseledBuild
```

构建产物位于 `versions/<MC版本>/build/libs/viabedrockutility-mc<版本>-1.0.0.jar`。

如果使用 [ViaProxyWorkspace](https://github.com/EaseCation/ViaProxyWorkspace)，可以一步完成：

```bash
cd ViaProxyWorkspace && ./gradlew buildViaBedrockUtility
```

### CI 构建

GitHub Actions 自动从源码构建所有依赖（绕过 JitPack 缓存问题）：

```
CubeConverter → BedrockMotion → BECamera → ViaBedrockUtility
```

每个依赖先 `publishToMavenLocal`，最终执行 `chiseledBuild` 构建所有 MC 版本。

## 许可证

请参阅 [LICENSE](LICENSE) 文件。
