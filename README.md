# Create: Structured Mechanical Spawner

Create: Mechanical Spawner（动力刷怪笼）的附属模组，构建于 **Minecraft 1.21.1 / NeoForge 21.1.249**。

核心特性：**让动力刷怪笼的"随机刷怪"跟随刷新点所在结构的原版刷怪表**，
并可给自定义刷怪配方加"限定结构"条件。

## 前置模组

| 模组 | 版本 | 说明 |
|---|---|---|
| [Create（机械动力）](https://www.curseforge.com/minecraft/mc-mods/create) | 6.0.10+ | 提供旋转动力与配方体系 |
| [Create: Mechanical Spawner（动力刷怪笼）](https://www.curseforge.com/minecraft/mc-mods/create-mechanical-spawner) | 1.3.0+ | 本模组挂靠的宿主 |
| Mechanicals Lib（自动带入） | 1.1.4 – 1.2.0 | 动力刷怪笼自身前置，同时提供配方条件注册表 |

三个依赖都在 `src/main/resources/META-INF/neoforge.mods.toml` 里声明为 `mandatory`，
缺任意一个客户端会拒绝加载。

## 构建

```bat
gradlew.bat build
```

产物：`build/libs/create_structured_mechanical_spawner-<版本>.jar`

```bat
gradlew.bat runClient    :: 开发客户端
gradlew.bat runServer    :: 开发服务端
gradlew.bat runData      :: 数据生成
```

## 结构感知随机刷怪

### 解析顺序

`spawn_fluid_random`（无 `output` 的配方）每次抽取时：

```
刷新点在结构内？
├─ 该结构有数据包自定义池（structure_spawn_pool）→ 用自定义池
├─ 该结构在黑名单里 → 跳过原版表，回退群系池
├─ 该结构声明了 monster 刷怪表（spawn_overrides）
│  ├─ 列表非空 → 用这张表（按 bounding_box 粒度判定）
│  └─ 列表为空 → 结构内不刷怪（可配置为回退群系）
└─ 都没有 → 回退群系 MONSTER 池（原版行为）
```

判定位置是**刷新点**（刷怪笼上方按滚动距离那格），不是刷怪笼本体。

命中判定用的是结构的**实际范围**：`full` 取结构包围盒、`piece` 取拼图块，所以站在结构正上方
很高的空中、或同一个区块里但结构范围之外，都不会命中（与原版 `ChunkGenerator#getMobsAt` 一致）。

### 原版数据覆盖了哪些地方

1.21.1 里 34 个结构只有 6 个声明了 `monster` 刷怪表：

| 结构 | 内容 |
|---|---|
| `minecraft:fortress` | 烈焰人 / 凋灵骷髅 / 僵尸猪灵 / 岩浆怪 / 骷髅（piece 粒度） |
| `minecraft:monument` | 守卫者（full 粒度） |
| `minecraft:pillager_outpost` | 掠夺者 |
| `minecraft:swamp_hut` | 女巫（怪物）、猫（生物） |
| `minecraft:ancient_city` | 空表 → 结构内禁止自然刷怪 |
| `minecraft:trial_chambers` | 空表 → 结构内禁止自然刷怪 |

**不覆盖**的典型是猪灵堡垒：它的猪灵/蛮兵是结构拼图自带的实体，生成时摆放一次、打掉不复活，
不属于刷怪表。所以堡垒内仍是下界群系池（有猪灵、疣猪兽，没有蛮兵）——这是刻意保持原版机制。

### 配置

`config/create_structured_mechanical_spawner-server.toml`：

| 键 | 默认 | 说明 |
|---|---|---|
| `structureAwareRandom` | `true` | 总开关；关闭后完全恢复原版随机逻辑 |
| `emptySpawnOverride` | `SUPPRESS` | 结构有空表时：`SUPPRESS` 不刷怪 / `FALLBACK` 回退群系 |
| `structureBlacklist` | `[]` | 不走原版表的结构，写法 `minecraft:fortress` 或 `#minecraft:village` |
| `weightBySpawnCount` | `true` | 按「权重 × 平均数量」抽取，见下节 |
| `logStructureResolution` | `false` | 调试用日志 |

### 抽取权重：为什么不是直接用 weight

原版的 `weight` 抽的是**一次刷新事件**，事件里再刷 `minCount~maxCount` 只；而刷怪笼一个周期只产 1 只。
直接沿用原始权重就变成按「事件占比」抽，**权重低但成组数量大的生物会被系统性压低**。

所以默认启用 `weightBySpawnCount`，改用「个体占比」：

```
有效权重 = weight × (minCount + maxCount)      // = 2 × weight × 平均数量
```

以要塞为例（100 万次抽样实测）：

| 生物 | weight | 数量 | 原始事件占比 | 修正后 |
|---|---|---|---|---|
| 烈焰人 | 10 | 2–3 | 35.71% | **23.38%** |
| 凋灵骷髅 | 8 | 5 | 28.57% | **37.40%** |
| 僵尸猪灵 | 5 | 4 | 17.86% | 18.71% |
| 岩浆怪 | 3 | 4 | 10.71% | 11.18% |
| 骷髅 | 2 | 5 | 7.14% | 9.34% |

修正后的比例 = 把原版 N 次刷新事件摊平成 N×平均数量 个个体的构成。
想要原始事件占比就把 `weightBySpawnCount` 设为 `false`。

### 黑名单 + 自定义池：接管某个结构

两步走，先禁掉默认行为，再定义自己的池子。

**1) 配置里点名禁掉原版表**（`config/create_structured_mechanical_spawner-server.toml`）：

```toml
structureBlacklist = ["minecraft:fortress", "#minecraft:village"]
```

**2) 数据包里给该结构写一个池**：`data/<命名空间>/structure_spawn_pool/<任意名>.json`

```json
{
  "structures": ["minecraft:fortress"],
  "pool": [
    { "type": "minecraft:wither_skeleton", "weight": 10, "minCount": 1, "maxCount": 3 },
    { "type": "minecraft:blaze",           "weight": 6,  "minCount": 1, "maxCount": 2 }
  ]
}
```

字段和原版结构 JSON 的 `spawns` 完全一致（`type` / `weight` / `minCount` / `maxCount`），
`minCount` / `maxCount` 目前只用于展示，实际一次刷一只。

优先级：**自定义池 > 黑名单 > 原版表**。也就是说给黑名单里的结构写池，池子依然生效——
这正是"禁用默认、自定义接管"的组合。自定义池的判定比原版宽松（包围盒或任一块拼图命中都算）。

### 调试命令

```
/csmdebug structure                 # 用玩家所在位置
/csmdebug structure <x> <y> <z>     # 指定已加载坐标
```

会打印：所在结构、每张生效刷怪表的粒度与内容、以及本次判定结果。

## 配方限定结构

利用 Mechanicals Lib 的配方条件注册表，可给刷怪配方加条件，和原有条件（`min_y`、`biome`、`min_speed` 等）自由组合：

```json
{
  "type": "create_mechanical_spawner:spawner",
  "input": { "amount": 100, "fluid": "create_mechanical_spawner:spawn_fluid_zombie" },
  "output": "minecraft:zombie",
  "processingTime": 2500,
  "requirements": [
    { "type": "create_structured_mechanical_spawner:structure", "structure": "minecraft:fortress" }
  ]
}
```

- `structure`：结构 id；加 `"negate": true` 表示"除该结构以外"
- `structure_tag`：结构标签，写法 `{ "type": "...:structure_tag", "tag": "minecraft:village" }`

⚠️ 动力刷怪笼选配方用的是 `findAny()`，**条件不参与筛选**。同一液体写多条配方靠条件区分是不可靠的，
正确用法是「一条液体一条配方，用条件限制它能在哪生效」。

## 结构

```
src/main/java/io/github/ling/structured_spawner/
├── StructuredSpawner.java          入口（@Mod，modid = create_structured_mechanical_spawner）
├── CreateIntegration.java          前置模组检测
├── config/CsmConfig.java           服务端配置
├── structure/StructureSpawnPools.java  结构刷怪表的解析与抽取
├── structure/CustomStructurePools.java 数据包自定义池（structure_spawn_pool）
├── mixin/LivingEntityHelperMixin.java  两处注入（活体生成 / 掉落结算）
├── requirement/                    配方条件 structure / structure_tag
└── command/CsmDebugCommand.java    /csmdebug
```

## 技术要点

- **Mixin 只有两处**，都注入 `LivingEntityHelper`，且只替换"抽取来源"，生成流程复用原模组自己的
  `spawnLivingEntity`（`+0.51` 偏移、随机朝向、`checkSpawnObstruction` 全部一致）。
- 启动时会自检并把结果写进日志：
  `structure-aware random mixin applied on ...LivingEntityHelper`，
  未织入则打 `ERROR` 并退回原版行为。
- 顺带修掉上游一个缺陷：战利品收集器路径的 `spawn.get()` 未判空，在没有 MONSTER 的群系会静默不产出。
- 1.21.1 的 `neoforge.mods.toml` 必须显式写 `modLoader="javafml"` + `loaderVersion`。
