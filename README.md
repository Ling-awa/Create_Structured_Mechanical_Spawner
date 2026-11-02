# Create: Structured Mechanical Spawner

Create: Mechanical Spawner（动力刷怪笼）的附属模组，构建于 **Minecraft 1.21.1 / NeoForge 21.1.249**。

## 前置模组

| 模组 | 版本 | 说明 |
|---|---|---|
| [Create（机械动力）](https://www.curseforge.com/minecraft/mc-mods/create) | 6.0.10+ | 提供旋转动力与配方体系 |
| [Create: Mechanical Spawner（动力刷怪笼）](https://www.curseforge.com/minecraft/mc-mods/create-mechanical-spawner) | 1.3.0+ | 本模组挂靠的宿主 |
| Mechanicals Lib（自动带入） | 1.1.4 – 1.2.0 | 动力刷怪笼 1.3.x 的自身前置 |

三个依赖都在 `src/main/resources/META-INF/neoforge.mods.toml` 里声明为 `mandatory`，
缺任意一个客户端会拒绝加载。

## 构建

```bat
gradlew.bat build
```

产物：`build/libs/create_structured_mechanical_spawner-<版本>.jar`

其他常用任务：

```bat
gradlew.bat runClient    :: 开发客户端（需已下载上述前置）
gradlew.bat runServer    :: 开发服务端（首次要改 run\eula.txt）
gradlew.bat runData      :: 数据生成，输出到 src\generated\resources
```

## 结构

```
src/main/java/io/github/ling/structured_spawner/
├── StructuredSpawner.java     模组入口（@Mod，modid = create_structured_mechanical_spawner）
└── CreateIntegration.java     与两个前置的对接点（ModList 检测）
src/main/resources/META-INF/neoforge.mods.toml   元数据与前置依赖
```

## 技术要点

- 1.21.1 是 NeoForge 的独立钉选线：`neoforge.mods.toml` 里**必须**显式写 `modLoader="javafml"` 和
  `loaderVersion`，否则客户端报 `Missing ModLoader`（21.11 起才默认 javafml）。
- 依赖坐标：Create 走官方 `maven.createmod.net`（只有裸 jar，无 `-all` classifier）；
  动力刷怪笼只在 CurseForge 发布，走 `curse.maven` 代理。
- 编译期依赖均为 `compileOnly`，本体不会被 shadow 进产物；`runtimeOnly` 只服务 `runClient/runServer`。
