# Fabric - 26.2

本目录是 Advanced Backups 的 Fabric **Minecraft 26.2** 移植版。
完整文档见仓库根目录与 `core`。

## 状态

构建环境已打通并可产出 jar，**Java 源码尚未移植**（当前 `src/main/java` 为空）。
`fabric/1.21` 的源码是 Yarn 命名，26.2 需要改写为 Mojang 官方命名，见下。

## 构建

```bash
# 在仓库根目录
./build-fabric-26.2.sh          # core + fabric/26.2 全量
./build-fabric-26.2.sh fabric   # 只构建本模块
```

产出：`fabric/26.2/build/libs/AdvancedBackups-fabric-26.2-<version>.jar`

模块也可单独构建（需先有 `core/build/libs/advancedbackups-corelib.jar`）：

```bash
cd fabric/26.2
JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew -Pversion=2.2.0 build
```

## 环境要求（实测结论，均非可选）

| 项 | 值 | 原因 |
|---|---|---|
| JDK | **25** | MC 26.2 的 `javaVersion.majorVersion = 25`，`options.release = 25` |
| Gradle | 9.7.1（wrapper 自带） | Loom 1.18 需要 Gradle 9；Gradle 9 已移除顶层 `archivesBaseName` |
| Fabric Loom | 1.18-SNAPSHOT | 当前官方模板所用版本 |
| Fabric Loader | 0.19.5 | 官方模板值 |
| Fabric API | 0.161.0+26.2 | Modrinth 上 26.2 的最新 API |
| `core` 模块 | 需先构建 | 本模块 `apply from: '../../global.properties'`，依赖 `core/build/libs/advancedbackups-corelib.jar`；`core` 自身要求 **JDK 8**（toolchain） |

## 与 1.21 版本的关键差异（移植时必须注意）

1. **不再有映射（mappings）**。Minecraft 26.x 已不再混淆
   （Loom 报 `Cannot use Mojang mappings in a non-obfuscated environment`），
   因此：
   - 不存在 `yarn_mappings` 字段，用 Yarn 无解；
   - 也不能声明 `officialMojangMappings()`；
   - 类与方法名是 **Mojang 官方命名**：`net.minecraft.client.Minecraft`（不是 Yarn 的
     `MinecraftClient`）、`net.minecraft.server.MinecraftServer` 等。
2. **依赖配置改用 `implementation`**。无混淆环境下不需要 remap，
   `modImplementation` 不再适用，`fabric-loader` / `fabric-api` 按官方模板走 `implementation`。
3. **客户端命令 API 改名**：`ClientCommandManager` → **`ClientCommands`**
   （包名仍是 `net.fabricmc.fabric.api.client.command.v2`）。
   `FabricClientCommandSource`、`ClientCommandRegistrationCallback` 仍在。
4. **`archivesBaseName` → `base { archivesName = ... }`**（Gradle 9 移除顶层属性）。
5. **access widener 已移除**。1.21 模块的 AW 只加宽了
   `ClientPlayNetworkHandler.lastSeenMessagesCollector`，该字段在 1.20.5 后已不存在，
   且工程源码中无任何引用（历史残留）。26.2 模块不带 AW。
   如需重新引入，注意无混淆环境下命名空间必须写 `official`，不能写 `named`。
6. 保留的服务端事件 API 仍在原包：
   `net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents`、
   `net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents`。

## 移植顺序建议

1. 先移植 `AdvancedBackups.java`（入口，只用 `ServerLifecycleEvents`，改动最小）；
2. 再移植 `network/`（`ServerPlayConnectionEvents` + 自定义包，需核对 `PacketCodec` 写法）；
3. 最后移植 `client/`（Yarn→Mojang 改名最密集，且涉及 `ClientCommands` 新 API 与 Toast 渲染）。

`fabric/1.21.2` 与 `fabric/1.21` 的差异可作为"小版本升级改动量"的参考。
