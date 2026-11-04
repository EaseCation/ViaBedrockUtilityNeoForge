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
| `PlayerAnimationRuntime` | Runs the merged Bedrock player scripts and root controller with per-player animation alias overrides |
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

## Owned player pose layers (AD integration)

`animation.PlayerPoseLayers` is a local rendering boundary. Prepare immutable animation IDs,
conditions and ordered event-free animation steps for the current resource generation; acquire a
lease for the actual client player; publish only declared finite `query.mod.*` parameters. Each
lease/view owns its variables and clock. Pose layers reject sound/particle/timeline events: those
belong to the caller's action/effect lifecycle, not to repeated render passes.

A layer can declare `nativeRotationOwners` as bone-name → condition-expression. A true condition
transfers that bone's Java native rotation channels to the layer; a false condition leaves normal
host motion intact. This is explicit channel ownership, not an implementation of Bedrock
`relative_to`. Overlapping owned bones are rejected. Closing a lease removes only that lease;
the next host pose restores the previous contribution before calculating normal player motion.
Saved pose storage is reused per model. The shared `PlayerAnimationInputs` supplies native queries
for both base controllers and these layers. Raw resource bone names remain resolvable even when
the Java model uses canonical aliases such as `right_arm`.

Legacy skeletal attachables bind a recognized root name to that host bone and inherit matched
non-root actor bones before applying their own animation. Explicit bindings keep their separate
binding path. `HostBoneInheritance` caches topology per geometry/host model; it does not parse
resources each frame. Both temporary and persistent attachable scopes use BedrockMotion's
`MoLangEngine.newScope()` so ordinary `math.*` calls are available.

Explicit geometry `binding` is interpreted only for format 1.16 or newer. An unbound root whose
name matches the host skeleton still follows that bone; an unrelated root stays in entity space,
without an implicit hand-slot binding. Entity-space roots keep the entity geometry origin instead
of subtracting a host-bone pivot. The model cache includes that resolved coordinate-space set, so
a host skeleton change cannot reuse geometry built for another origin. `@entity` is an internal
diagnostic label, not an author-selectable bone or a public weapon setting.

Attachable definitions retain an immutable `AttachableProgram` for their resource generation,
including initialize/pre-animation, selection and scale expressions. Rendering does not depend
on entries surviving in MoLangEngine's bounded global parse cache. `BedrockFrameTime.frame()`
identifies a displayed frame; player pose layers prepare once per frame/view, and attachables
prepare before evaluating render controllers or submitting material passes. Pre-animation may
write variables, so it is no longer also executed by the attachable tick. Each prepared display
selects its ordered animation stack immediately, including camera switches between ticks.
Lifecycle/events advance at most once per playback and integer tick; a newly selected playback
is initialized at that integer tick before its first draw, then the fractional display time is
restored. Existing playbacks do not restart on a view change. Off-screen ticks continue lifecycle
without inventing display frames. Material sampling does not advance lifecycle or emit events.
Failed initialization is retained as a failure, and a failed frame is not re-executed by another
material pass. Diagnostics distinguish `active_animations` from mere `cached_animations`.
Each frame/context caches the selected stack, root scale, evaluated render passes and sampled
model poses. Returning FP → TP → FP reuses the first FP output without rerunning pre-animation,
conditions or bone channels. Author variables and playback clocks remain actor-owned; visual
replay does not clone or rewind the Molang scope. After a display plan exists, ticks advance the
last selected plan without reevaluating display conditions; tick-only startup can establish an
initial plan. `inspectVariables` reports persistent runtime values, not a cached view output.

Different native models require no pose copies. If another context overwrites the same model,
the runtime lazily captures its complete pose and restores it on return. `IBoneModel.capturePose`
is bound to the exact model; hosts with extra channels must override it. VBU captures native TRS,
Bedrock rotation/offset/pivot, visibility, inherited-frame flags and copied parent matrices.
New frames/ticks retire these caches, including ticks while off-screen. A context's render-pass
and model set is frozen for the frame: replay rejects previously unsampled models. Resource or
layout changes require a new frame/generation. Explicit `frame_cache` diagnostics count samples,
reuses and copies; these counters do not measure render time. Sequential same-frame draws do
not prove recursive rendering, shadow-camera contexts or controller/event lifecycle acceptance.
The separate client-entity runtime's preparation policy is unchanged.

Held-attachable material and visibility selectors cache only the association between an installed
model's part names and the ordered selector keys. Material names and visibility values remain live
render-controller outputs; the last matching rule still wins. Canonical bones own material selection,
while visibility retains its existing cube-group behavior. Each model holds one plan per selector use;
changed keys or order replace that plan, and resource replacement discards it with the model. Do not
compile regexes or repeat name matching for every bone/material/frame, or cache dynamic rule values.
The current case-insensitive selector syntax is preserved by this optimization; it does not introduce
a new pattern language or alter Molang evaluation. Selection restoration still runs in `finally`.

Held-attachable timeline expressions execute in the current actor scope at lifecycle advancement,
including off-screen ticks. Transient particle/sound records have a combined limit of 128 and
expire after two logical ticks waiting for a displayed pose (100 ms at 20 TPS); the tick path
expires them even without rendering. Overflow is dropped with a bounded warning. Dispatch occurs only after a material submission
succeeds; a failed pass leaves still-valid events available for a later successful pass, subject
to the same expiry. Dispatch removes the batch before calling external sinks and isolates runtime
failures per event, so a second material pass cannot replay a batch whose sink failed. Explicit `events` diagnostics report pending,
expired, overflow and consumed counts. “Consumed” means a delivery attempt, including unresolved
aliases; it does not assert audible sound or visible particles. FP skin layers remain event-free.
These limits are transient host delivery policy, not weapon input/ACK or action timing rules.
Sound `pre_effect_script`/variable-driven pitch and volume, native locator placement and complete
rendered effect acceptance remain separate work; the current sound sink uses fixed pitch/volume.


Molang `this` reads the accumulated channel before the current transformation, including scale
(neutral scale is 1, not a missing value). `IBoneTarget.readAnimationOffset` reads Bedrock coordinates;
VBU converts its stored negative Y back at that boundary. Raw `getOffset` remains mutable storage
for pose capture and restoration. Interpolation and endpoint sampling use the same channel snapshot
without allocating a new vector per bone.

`override_previous_animation` resets only the animation's compiled bone set before each pose
sample, including render-only samples. `IBoneTarget.resetForAnimationOverride` separates that
operation from the ordinary frame reset. VBU restores the affected ModelPart's native bind pose
as well as its Bedrock channels; unrelated limbs keep their host animation. Owned player layers
capture and restore native translation, rotation and scale when they hand control back.

Attachable `scripts.animate` is one ordered stack: bare animations and controllers are sampled in
their declaration order. Playback wrappers are cached per runtime; tick advances each selected
entry and material passes sample that same order without firing events. Separating controllers
from bare animations changes override, `this` and multiplicative-scale results and is not valid.

The cross-mod classes and signatures are retained and checked in workspace ZKM packaging.
Acquiring a scope or receiving variables is not evidence of final pose correctness. Full
`relative_to`, frame timing, first-person ownership, custom skeleton and non-uniform-parent
acceptance remain part of the framework AD2 matrix; do not advertise full gun-client support
from this integration boundary alone.

Entity-relative rotation metadata is retained through animation sampling, override and pose capture.
Its render basis is captured before the physical hand binding; FP adds the established camera basis.
The common matrix operation preserves the inherited pivot position without inverting animated
ancestors. Flattened player parents and semantic pose readback must use that same operation.
ModelPart and layer scopes are balanced with `finally`, including the non-indexed render path.
Bedrock removes identically-zero constant rotation channels, including constant curves; a dynamic
expression returning zero is still a rotation channel. This distinction is resolved at installation,
using Mocha constant analysis. Full player, armor, FP and controller-transition acceptance remains
in the framework AD2 plan; this work does not declare those paths or other BedrockMotion consumers ready.

When a semantic Bedrock player skeleton uses vanilla Java animation (no Bedrock player runtime),
vanilla head look is declared in actor space before owned layers. Restoring the semantic torso
parent must not rotate or scale that head look again. An overriding authored head track can reset
this base declaration; a resource-provided Bedrock player runtime owns its own rotation space.
This is a player-host convention, not a gun-specific head-angle correction.

Held-item runtime ownership is checked against the live player's actual logical hand before tick
advancement. Empty hands, changed item identifiers and missing owners retire only the corresponding
runtime, using an expected-object check so an old snapshot cannot remove a replacement. The render
TTL still handles unseen held objects; it is not an equipment lifetime. The check reads identity
without copying full item data and does not restart controllers for ammo/component-only updates.

Player animation query `query.get_default_bone_pivot(name, axis)` reads the selected player geometry's original Bedrock pivots. Both the base player runtime and owned pose layers bind the same metadata-backed query. It does not read animated pivots or invert Java presentation coordinates; source bone names and canonical aliases resolve through the existing metadata map. The 1.21.8 local-pose path was checked in a real client; this does not declare the complete FP weapon or other host versions verified.

Owned first-person viewmodels can use `FirstPersonSkinLayers` for event-free supplemental geometry driven by the existing animation and render-controller resources, with the current player skin. Geometry uses the common attachable renderer; only the texture source and player-view context differ. Programs belong to a resource generation, leases to actual player/item ownership, and all local mutable playback remains per lease. Framework callers must release their leases; host ticks also retire lost equipment and worlds. Classic/Slim selection reads current model metadata; only the Classic representative was exercised in the current AD run.

`FirstPersonViewSpace` is a separate opt-in rendering-space lease. Within the hand event's pushed stack, it converts a complete camera-local authored pose into the current submission basis without Java's additional hand-only lag/bobbing. It neither changes the world camera nor user settings. Unowned items keep their existing path. The new ports and nested API records are kept in the obfuscation profile; build success alone does not verify full action or cross-platform support.

A prepared, current lease also selects the actual held stack for its own hand during first-person
attachable rendering. Vanilla `RenderHandEvent` can still carry the outgoing or empty stack while
its equip-height cache transitions; waiting for that cache would leave an already prepared authored
viewmodel invisible. Selection is limited to the same player/hand/item and current resource generation,
and starts only after `setCameraSpace` succeeds. It does not write vanilla item caches, equip heights,
inventory or camera options. Unprepared, released, mismatched and unowned items retain the event stack.

Acquisition declares a bounded `geometry identifier -> reference bone` map, validated against the
loaded pack generation. `Lease.setCameraSpace(rotation, position, scale)` supplies a row-major 3×3
rotation, camera-space translation in blocks and positive reference scale. It replaces the accumulated
matrix at those exact reference bones during their geometry draw; their descendants still use the
ordinary animation stack, including zero scales. Choose a dedicated reference node: that node's own
local animation is intentionally replaced. This is a coordinate-space port, not an animation sampler
or a camera controller. Ordinary items, third-person draws and other actors are unaffected; geometry
scope storage is reused and nested scopes restore their previous root. Losing equipment, player or
resource generation retires the lease. Displayed-locator consumers must use this same root space,
not the uncorrected physical item attachment matrix.

A prepared camera-space owner can draw without a player first-person animation runtime or local
player pose layer. In that case every submitted geometry must have a declared reference root;
missing ownership fails that pass explicitly. The default hand/attachable path still requires its
ordinary player-pose source. Do not install a dummy player animation just to enable independent meshes.

For bounded first-person evidence, `ViaBedrockUtility.captureFirstPersonBones(owner, names)` selects up to 16 names for the next displayed frame (128 records, 2-second expiry); `inspectFirstPersonBones()` reads the actual render matrices without reevaluation. An empty selection clears it. In the local Minescript setup, capture a completed framebuffer from `tick_loop`; its default render-loop screenshot can precede hands and HUD.

## Selected attachable world anchors

`AttachableWorldPoses.acquire(player, itemIdentifier, offhand, targets)` opens an optional,
bounded subscription to named bones/locators. Each target is `(bone, locator)`; an empty
locator selects the bone pivot. Consumers own the lease and close it when no effect needs it.
No subscription is installed for ordinary items, and no animation is evaluated for readback.

`sample(alias)` returns only the current actual third-person display frame. Its defensive
matrix copy uses native world block coordinates and Java model axes. Geometry-local points
share the exact legacy-root or explicit-binding origin used to build the model. Entity
world placement comes from `LevelRenderer.renderEntity`, not GUI/paperdoll drawing or a
first-person hand projection. Failed passes publish nothing; disagreeing material passes,
zero-scale hidden ancestors, culled models, replaced items and resource generations cannot
supply a previous-frame fallback. Resource/connection changes and explicit close retire leases;
closing one consumer never removes another consumer's subscription.

This is separate from the existing entity/player particle pose provider. It neither changes
that provider nor enables previously unsupported animation-event locators. Integrations must
sample after entity rendering and define their own effect lifecycle when no anchor is available.
Cross-mod `Target`, `Snapshot`, and `Lease` signatures are included in the workspace obfuscation
profile. Actual behavior coverage is recorded by the consuming project's acceptance plan.

### Owned audio playback

`BedrockJavaSoundPlayer.resolveEvent` exposes the same canonical Bedrock/Java event mapping
used by the existing fire-and-forget helpers. Hosts that need independent playback handles can
create native SoundInstances without copying that mapping or stopping all instances of an alias.

`CancellableSoundInstance` is an optional audio-thread-readable cancellation contract. The engine
binds it when assigning a Channel, before asynchronous decoded buffers can start playback. A
cancelled instance cannot play or unpause, and the channel pool treats it as stopped even when
OpenAL retains the never-started source in INITIAL. Ordinary sounds reset the gate to null;
channel destruction releases the reference. Cancellation is terminal for one instance. This
does not add event scheduling, change ordinary sound pitch/volume, or replace the existing
extended Bedrock pitch handling. A source event callback can still represent a blocked start;
integrations must distinguish submission, actual source state and captured audio output.

### Owned attachable UV inputs

`AttachableSurfaces.acquire(player, itemIdentifier, offhand)` owns one surface lease per actual actor/hand. Publish an immutable `Uv(offsetU, offsetV, scaleU, scaleV)` with finite coordinates and positive scale; the caller owns animation time, atlas/heat sampling and overlay arbitration. Closing a stale lease never removes a successor. Connection, resource generation, entity identity and actual held item bound the lease; the runtime tick retires invalid owners.

Only the held attachable uses these coordinates. Separately rendered first-person player skin geometry is excluded. Affine coordinates are submitted without per-vertex modulo; independent Bedrock textures explicitly use GPU repeat addressing. Identity input preserves the ordinary buffer path and existing RenderType/shader-mod routing. This API does not claim emissive-gain, fade or Bedrock emissive-alpha parity. Public classes/methods are retained and verified in the obfuscated distribution. Real rendering/lifecycle acceptance is tracked by the consuming project, separately from successful compilation.

### Emissive-alpha material (integration in progress)

The attachable `entity_emissive_alpha` path uses texture alpha as a lighting weight, rather than treating the entire part as full-bright or interpreting low alpha as transparency. Transparent black is discarded; alpha-zero colored pixels remain opaque. Vertex/render-controller tint opacity remains independent. Two registered pipelines cover opaque and translucent overall tint, and the texture-keyed RenderType cache is retired with the pack texture generation. This is a native material implementation, not a gameplay or cosmetic-time API.

Native rendering with Iris installed but shaders disabled has passed real in-game pixel comparisons and resource-reload checks. Active Iris 1.9.6 / ComplementaryReimagined_r5 has now passed world-space color, reload, transparent-black holes, overall tint alpha and real-block occlusion checks. The neutral ENTITY_SNIPPET avoids inheriting Iris's solid shader mapping. For these two owned pipelines only, an optional bridge binds the current Iris-owned default target after native/Iris draw setup; it preserves our shader and never caches framebuffer IDs or invents attachment slots. This keeps native lighting semantics through the shaderpack composite, rather than claiming shaderpack PBR or shadow-lighting parity. Actual Carbine first-person muzzle flashes have also been verified with this pack in daylight and at night after fixing BedrockMotion to retain resolved per-bone material identifiers instead of resource aliases. The shadow-output branch, other packs and performance remain unverified. Do not advertise full shaderpack compatibility or emissive-gain/fade support from these checks.

PlayerPoseLayers.Step retains both the animation identifier and its condition. All step bones join the lease ownership set; declared steps sample before the final pose animation. Condition preparation runs once per display frame, while sample-dependent arithmetic belongs in a later animation channel. The framework consumer uses Bedrock binding version 6; legacy condition-only lists are not supported.

Per-pass visibility and material selection use model-owned primitive restore buffers. Rule values remain live and later matching rules retain precedence. Nested draws use separate stack frames; partial application and draw failures restore the captured flags. Geometry diagnostic counts are computed only when explicitly requested, not on every render pass.


### Numerical pose ownership and rigid attachable geometry

`PlayerPoseLayers.preparePose` accepts a finite owned bone set and a numerical local pose source (rotation and finite positive scale). `AttachablePoseLayers.acquire` delegates only the third-person equipment pose for an actual actor, held item, connection and pack generation. The source replaces the owned rotation/scale channels; the renderer restores their previous values after drawing. Producers own scale policy, clocks, curve sampling and rules; VBU owns reset/base transforms, inheritance, render controllers and actual locator matrices. Existing animation consumers keep the ordinary animation API. Both ports are retained in the obfuscated public ABI.

Rigid attachable meshes reuse the existing GPU LRU and upload backend. Local geometry is shared by immutable content; native model references release it. The first model use queues preparation with a bounded per-frame upload budget; unprepared parts continue through the original vertex/Sodium path. Canonical author bones remain separate so their matrices, light and affine UV stay live. Geometry/topology changes and resource reload invalidate owned data; no actor pose is stored in the geometry cache.

Ordinary opaque/cutout and owned emissive-alpha pipelines can use the cache. Translucent ordering, glint, animated per-cube UV and unsupported Iris passes remain on the original path. Active Iris ordinary materials are not advertised as accelerated; the owned emissive-alpha integration preserves the current Iris output target. Successful partial draws are marked before fallback, so only undrawn parts are resubmitted. Shared-cache capacity checks distinguish inserting a new entry from touching a full cache.

Molang bytecode execution is not a prerequisite. BedrockMotion and particles currently retain prepared AST interpretation and the independently reviewed semantic fixes; the optional compiler must not be re-enabled by a consumer update without real-game acceptance.
