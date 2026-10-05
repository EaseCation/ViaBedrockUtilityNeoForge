# 基岩实体移动 MoLang query：原生证据与 JE 修复

调查日期：2026-10-05 至 2026-10-06。Möbius：[#726](https://mobius.easecation.net/issues/726)。

## 问题与范围

圣符第三、第四章的猎归精英、恶兽及恶化猎归，直接把
`query.modified_move_speed * 2.5` 用作动画混合权重。部分 NPC 的 `pre_animation`
还使用 `modified_distance_moved` 决定摆动相位。旧 JE 自定义实体实现把这两个动画量
与世界位移、米/秒混用，并在每个渲染 pass 里累计位移，导致权重放大及帧率相关波动。

本轮修复当前 1.21.8 VBU 的自定义实体、玩家宿主、附加物帧查询，以及 BEParticle 的
`frame_alpha`。资源包中的原始权重与动画表达式没有修改。ViaBedrock 的协议、ordinal、
payload 字段、能力和正式运行目录没有修改。

## 样本与复现工具

| 样本 | 路径（相对于 mcpelauncher-manifest） | SHA-256 |
|---|---|---|
| 网易开发者版 3.10 | `build-macos-arm64/netease-310/libminecraftpe.so` | `e6d624173d5f417ab2149eb52b51c21a1a16a941b61493265b292894f60eaa69` |
| 网易开发者版 3.9 | `build-macos-arm64/netease-dev/game/lib/arm64-v8a/libminecraftpe.so` | `a0f5332d443f20063cc0adce5cf935597cccf6c790ea6a9a7e79ec8a067b72f3` |

原始分析产物保存在 `mcpelauncher-manifest/build-macos-arm64/molang-motion-analysis/`。
这不是 Git 管理目录，交接时需同时复制原始产物。`inspect_motion.py` 复用现有
`tools/inspect_android_elf.py` 和 `tools/analyze_developer_binary.py`，由 PT_DYNAMIC
恢复重定位指针，用 Capstone 反汇编，并通过 `.eh_frame_hdr` 辅助确定函数边界。

定位链为：完整的 `query.*` 注册字符串 → 初始化函数中的 std::function 虚表 →
虚表 `+0x30` 调用入口 → Actor/ECS 状态 getter 与状态更新函数。
主游戏函数名并未完整保留在动态导出表中；实际定位同时依靠未混淆的 RTTI 和注册字符串。
字符串旁的解释文本只能提供线索，结论以实际执行指令为准。

## 3.10 已核实的执行入口

以下均为 ELF 相对虚拟地址，不是进程 ASLR 地址。

| 内容 | 地址 | 证据 |
|---|---|---|
| MoLang 注册函数 | `0x0cf14e74` | 注册完整 query 名称及对应闭包虚表 |
| `modified_move_speed` | `0x0cf56e94` | 插值前后步行状态；`FMINNM` 上限 1；BABY 倍率 1.5 |
| `modified_distance_moved` | `0x0cf56db0` | 读取累计步行状态，扣除本 tick 尚未插值的部分 |
| `ground_speed` | `0x0cf5629c` → `0x0ce2d678` | PostTickPositionDeltaComponent 三维分量分别乘 20，再求长度 |
| `vertical_speed` | `0x0cf56348` → `0x0ce2d70c` | 同一组件的 Y 分量乘 20，保留正负号 |
| `position_delta` | `0x0cf5c94c` → `0x0ce2b2fc` | 按轴读取 StateVectorComponent 的运动向量；不乘 20 |
| `walk_distance` | `0x0cf56f9c` → `0x0d13d658` | WalkDistComponent 的采样结果乘 0.6 |
| `frame_alpha` | `0x0cf63ae8` | 直接读取渲染上下文 `+0x118`，为 AI tick 间插值比例 |
| `delta_time` | `0x0cf63be8` | 直接读取渲染上下文 `+0x128`，单位为秒 |
| ActorWalkAnimationComponent 默认构造 | `0x09ab0e20` | 倍率初值 1，前后步行状态与累计状态初值 0 |
| 步行状态输入更新 | `0x09a98730`、`0x09a98758` | 水平 tick 位移，并可叠加 DynamicRenderOffsetComponent 的位移 |
| HardcodedAnimationSystem 更新 | `0x09a9878c` | 平移/转身输入、状态倍率、0.6 的旧状态保留与累计 |

ECS 类型身份还通过 entt 类型哈希核实：
`ActorWalkAnimationComponent = 0x666777c6`，
`PostTickPositionDeltaComponent = 0xf96968b0`，
`StateVectorComponent = 0x1b5d5238`，`WalkDistComponent = 0xcf59bfcf`。

3.9 交叉核对得到 `modified_move_speed` 入口 `0x0d962498`、
`modified_distance_moved` 入口 `0x0d9623b4`，其插值、上限、BABY 倍率和累计距离采样结构
与 3.10 一致。不能把 3.9 地址用于 3.10 样本。

## 普通实体的恢复公式

令 `d` 为本 tick 水平位移，`r` 为归一化的身体 yaw 变化，`s` 为步行状态，`D` 为累计动画距离，
`a` 为当前渲染帧插值比例：

```text
input = d == 0 ? min(abs(r) * 0.02, 0.2) : min(d * 1.6, 0.4)
previous_s = s
s = input + previous_s * 0.6
D = D + s

modified_move_speed = min(previous_s + (s - previous_s) * a, 1)
                     * (is_baby ? 1.5 : 1)
modified_distance_moved = D - s * (1 - a)
ground_speed = length(post_tick_position_delta_xyz * 20)
vertical_speed = post_tick_position_delta_y * 20
position_delta(axis) = state_vector_motion[axis]
```

乘客状态将前后步行状态归零。普通实体不应把 `modified_move_speed` 当作米/秒。
BABY 倍率位于上限之后，且没有用于 `modified_distance_moved` 的 MoLang getter。
动画表达式自身仍允许产生大于 1 的混合权重，不应再对整个混合权重统一限幅。

原生 `FNMSUB(a, s, s)` 的结果为 `a * s - s`；已用本机 AArch64 小程序单独核实其符号，
避免将累计距离的采样方向解释反。

例如恒定 `0.1 格/tick` 移动时，原生步行状态收敛到 `0.4`，圣符 `* 2.5` 的权重为 `1`。
旧 JE 自定义实体实现收敛到 `2 米/秒`，相同表达式得到 `5`。这不是资源包应该额外补偿的单位差异。

## 查询审计与实际改动

| Query / 输入 | 旧实现的问题 | 本轮处理 |
|---|---|---|
| `modified_move_speed` | 自定义实体与物理速度混用；玩家依赖 Java 原版步行状态 | 两条 VBU 路径改为独立 tick 状态及原生插值/上限 |
| `modified_distance_moved` | 自定义实体累计世界位移，且受绘制次数和可见性影响 | 累计原生动画运动量，渲染只采样 |
| `ground_speed` | 自定义实体读取平滑水平动画量；玩家读取归一化步行强度 | 独立 tick 位移的三维米/秒长度 |
| `vertical_speed` | 自定义实体过度平滑；玩家少乘 20 | 独立 tick 位移 Y × 20 |
| `position_delta` | 自定义实体读取相邻渲染帧差值；玩家已读取运动向量 | 自定义实体改为 Java 接收的运动向量，玩家保留原始向量单位 |
| `frame_alpha` | 自定义实体/附加物没有绑定；粒子误乘 0.05 | 绑定相同归一化插值比例；修复粒子的 20 倍缩小 |
| `delta_time` | 自定义实体 pre_animation、玩家/附加物没有基础绑定 | 在 Minecraft 帧入口测量一次单调实际帧耗时，供全部 pass 共用 |
| `walk_distance` | 玩家已有落地、非潜行判断，但缺少原生 0.6 换算 | 补上 0.6 换算；完整原生 WalkDistComponent 插值仍未复刻 |
| `variable.gliding_speed_value` | 自定义实体写死物理步速 4.317 | 改为原生多个宿主初始化位置已核实的默认 1，允许资源脚本覆盖 |

客户端运动状态在 `ClientLevel.tickNonPassenger/tickPassenger` 结束后更新。
Interaction 自身的 `tick()` 是空方法，不能假设 LivingEntity 的原版 WalkAnimationState 会替它推进。
客户端每个 UUID 的 renderer/ticker 独立持有运动状态；玩家实例替换时更换状态，避免重生后的旧坐标残留。
渲染、视锥剔除、LOD 和纸娃娃重复 pass 均不会累计新运动量。

## 未复刻的输入与其他发现

- 原生 HardcodedAnimationSystem 还有 hurt/fire 条件倍率、跳跃倍率、特殊实体及
  DynamicRenderOffsetComponent 输入。普通自定义 Interaction 缺少对应原生组件状态；本轮
  没有把“离地”猜成“正在跳跃”，也没有根据未知 flag 枚举推断原生 fire 谓词。
- `walk_distance` 原生 getter 对 WalkDistComponent 采用独立采样。当前玩家侧仍由已存在的
  落地/非潜行世界距离跟踪器提供累计输入，补上单位换算不等于已完整移植原生组件生命周期。
- `query.life_time` 与 `query.anim_time` 的注册解释文本相同，但 getter 读取不同上下文字段：
  `+0x130` 与 `+0x12c`。本轮未追完这两个字段的写入，不凭解释文本改变其语义。
- 圣符资源实际使用 `query.is_jumping`，自定义实体目前没有对应提供者；`is_jump_goal_jumping`
  是另一条 query。`hurt_time` 和巴比伦的 `query.property('easecation:dash_time')` 等也存在独立数据缺口，
  不能声称此次移动数值修正已覆盖这些技能状态。
- BedrockLoader 26.1 的玩家运行时拷贝代码，以及未安装 VBU 时的代理 ServerEntityTicker，
  仍有同类单位绑定或近似实现。本轮改动针对当前 VBU 1.21.8 消费路径，未更改这些后端。

## 验证与交付状态

- Java 21，从 NeoForgeWorkspace 根目录编译 VBU 和 BEParticle 已通过。
- 运动状态回归覆盖普通运动/停止、半 tick 插值、不同绘制次数与顺序、极大位置跳变、乘客、
  转身跨 ±180°、三维物理速度与生命周期重置。
- 圣符回归输入完整保留猎归精英移动控制器及其 idle/move_arm/move_leg 动画，直接运行原始权重；
  另运行戴斯与叛军的原始 NPC pre_animation。
- 新增玩家查询绑定、附加物帧比例、共享帧耗时与粒子渲染表达式的测试。
- 全量 VBU 测试仍有 7 项既存资源闭包/姿态测试失败。已临时还原本次修改过的全部原有文件，
  对失败测试做原始代码对照，再恢复本次改动；失败名称和错误信息完全相同。
  对照产物位于原生分析目录的 `baseline-comparison.json`。
- 源码/测试通过、构建 JAR、客户端目录替换及 JE/网易实机画面验收是独立状态。本轮不自动
  替换运行客户端或代理，不将离线公式与回归测试宣称为实机视觉验收。
