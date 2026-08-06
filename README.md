# ViaBedrockUtility

A NeoForge client mod that provides enhanced rendering capabilities for Minecraft Java Edition clients connecting to Bedrock servers via [ViaProxy](https://github.com/ViaVersion/ViaProxy) + [ViaBedrock](https://github.com/RaphiMC/ViaBedrock).

Inspired by [BedrockSkinUtility](https://github.com/Camotoy/BedrockSkinUtility), with some code reused from ViaBedrock.

[中文文档](README_zh.md)

## Features

- **Custom Entity Rendering** — Parses Bedrock geometry models, render controllers, and skeletal animations to render Bedrock custom entities on the Java Edition client
- **Custom Player Skins** — Supports Bedrock custom geometry skin models, capes, and animated skin overlays (blinking animations, etc.)
- **Bedrock Camera API** — Camera presets, position/rotation easing, fade in/out, camera shake
- **Animation LOD System** — Distance-based animation level-of-detail to optimize performance for distant entities

## Architecture

```
Bedrock Server
    ↓ Bedrock Protocol
ViaProxy (Proxy)
    ├── ViaBedrock (Protocol Translation)
    │       ↓ Custom Payload
    └── ViaBedrockUtility (NeoForge Mod, this project)
            ├── Custom Entity Rendering
            ├── Custom Player Skins
            └── Bedrock Camera API
                    ↓
                BECamera (NeoForge mod, camera library)
```

### Communication Channels

ViaBedrock handles Bedrock → Java protocol translation on the proxy side, while forwarding data that Java Edition cannot natively express (custom entity models, Bedrock skins, camera instructions, etc.) to the client mod via **Custom Payload** channels:

| Channel | Direction | Purpose |
|---------|-----------|---------|
| `viabedrockutility:data` | S→C | Entity model requests, animation data, skin/cape data |
| `becamera:data` | S→C | Camera presets, instructions, shake |

## Modules

### Custom Entity Rendering

| Component | Description |
|-----------|-------------|
| `CustomEntityTicker` | Per-entity state machine managing MoLang scope, render controller evaluation, and animation condition computation |
| `CustomEntityRenderer` | Entity renderer managing Animator instances with multi-model blended animations |
| `CustomEntityModel` | Model wrapper supporting Bedrock bone visibility control |
| `McBoneModel` / `ModelPartBoneTarget` | Adapter layer wrapping Minecraft ModelPart as BedrockMotion IBoneModel/IBoneTarget |
| `GeometryUtil` | Bedrock geometry → Minecraft Model conversion, handling coordinate system differences and UV scaling |

**Rendering Pipeline**:
1. ViaBedrock sends `MODEL_REQUEST` payload (entity identifier, variant, skin ID, etc.)
2. `CustomEntityTicker` evaluates render controllers based on entity definition to determine current models/materials/textures
3. `CustomEntityRenderer` evaluates animation conditions each frame (MoLang expressions as blend weights) to drive skeletal animations
4. LOD system reduces animation update frequency for distant entities

### Custom Player Skins

| Component | Description |
|-----------|-------------|
| `PayloadHandler` | Manages chunked skin data reception and assembly |
| `CustomPlayerRenderer` | Custom player renderer with texture override and animation overlay support |
| `AnimatedSkinOverlay` | Animated skin overlays supporting LINEAR (sequential frame) and BLINKING (automatic eye blink) playback modes |
| `PlayerAnimationManager` | Manages Bedrock animation overrides for players, handling axis clearing against vanilla animations |
| `PlayerSkinBuilder` | Cross-version compatible SkinTextures builder |

**Skin Data Flow**:
1. `SKIN_INFORMATION` — Skin dimensions, geometry JSON, chunk count
2. `SKIN_DATA` × N — Chunked RGBA texture data transmission
3. `CAPE` — Cape texture (optional)
4. `SKIN_ANIMATION_INFO` + `SKIN_ANIMATION_DATA` — Animated overlay layers (optional)

### Bedrock Camera API

| Component | Description |
|-----------|-------------|
| `CameraPayloadHandler` | Processes camera instructions, shake, and presets |
| `CameraPresetsPayload` | Decodes server-registered camera presets (with parent inheritance) |
| `CameraInstructionPayload` | Camera set (position/rotation/easing), clear, fade in/out |
| `CameraShakePayload` | Positional/rotational camera shake |

Powered by the [BECamera](https://github.com/EaseCation/BECamera) library for camera state management and Mixin injection.

### Configuration

Configuration options in the current NeoForge client build:

- **Animation LOD Presets**: HIGH_QUALITY (no LOD), BALANCED, PERFORMANCE, CUSTOM
- **Custom Tiers**: Control animation update frequency by distance threshold and frame interval

## Dependency Chain

```
CubeConverter          ← Bedrock geometry model parsing
    ↓
BedrockMotion          ← Skeletal animation engine (MoLang / animation controllers / render controllers)
    ↓
ViaBedrockUtility      ← This project
    ↓
BECamera               ← Bedrock Camera API library (camera control / path interpolation)
```

| Dependency | Version | Source | Description |
|------------|---------|--------|-------------|
| [CubeConverter](https://github.com/EaseCation/CubeConverter) | 1.3 | mavenLocal / JitPack | Bedrock geometry model parsing |
| [BedrockMotion](https://github.com/EaseCation/BedrockMotion) | 1.0.0 | mavenLocal / JitPack | Skeletal animation engine |
| [BECamera](https://github.com/EaseCation/BECamera) | 1.2.0 | mavenLocal | Bedrock Camera API |
| [BEParticle](https://github.com/EaseCation/BEParticleNeoForge) | 1.0.0 | Local workspace / Maven | Bedrock particle engine used by the NeoForge client stack |
| NeoForge | 21.8.52 | Maven | Client mod runtime |
| Lombok | 1.18.36 | Maven | Compile-time annotation processing |

## Supported Minecraft Versions

Current NeoForge workspace target: **1.21.8**.

## Usage

### Required Components

1. **ViaProxy** — Proxy server through which the Java client connects to Bedrock servers
2. **ViaBedrock** — ViaProxy plugin that performs Java ↔ Bedrock protocol translation
3. **ViaBedrockUtility** — This mod, installed on the NeoForge client

### Optional Components

- Additional client-side optimization or UI mods can be layered on top of this NeoForge setup as needed

### Installation

1. Install NeoForge 21.8.52 for Minecraft 1.21.8
2. Place `viabedrockutility-1.0.0.jar`, `bedrockcameralib-1.2.0.jar`, and `beparticle-1.0.0.jar` into the client `mods/` directory
3. Configure ViaProxy to connect to a Bedrock server
4. Launch the game and connect through the ViaProxy proxy

## Building

### Prerequisites

- JDK 21+
- This NeoForge workspace checked out with its companion projects available locally

### Local Build

```bash
# 1. Build and publish CubeConverter
cd CubeConverter && ./gradlew publishToMavenLocal

# 2. Build and publish BedrockMotion
cd BedrockMotion && ./gradlew publishToMavenLocal

# 3. Build and publish BECameraNeoForge
cd BECameraNeoForge && ./gradlew publishToMavenLocal

# 4. Build and publish BEParticleNeoForge
cd BEParticleNeoForge && ./gradlew publishToMavenLocal

# 5. Build ViaBedrockUtilityNeoForge
cd ViaBedrockUtilityNeoForge && ./gradlew build
```

Build artifacts are located at `build/libs/viabedrockutility-1.0.0.jar`.

### CI Build

GitHub Actions automatically builds all dependencies from source (bypassing JitPack cache issues):

```
CubeConverter → BedrockMotion → BECamera → ViaBedrockUtility
```

Each dependency is published to `mavenLocal` first, then the NeoForge workspace build is executed.

## License

See the [LICENSE](LICENSE) file.
