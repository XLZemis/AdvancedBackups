# Fabric - 26.2

本目录是 Advanced Backups 的 Fabric **Minecraft 26.2** 移植版。
完整文档见仓库根目录与 `core`。

## 状态

**✅ 移植完成，并通过服务端与客户端两侧的实机验证。**

- `src/main/java` 下 10 个源文件全部移植完毕（Yarn → Mojang 官方命名）
- `./gradlew build` 零警告通过，产出可加载的 jar
- **服务端**（`runServer`）：mod 正常加载、配置生成，
  `/backup start` 与 `/backup snapshot <name>` 均成功产出合法备份包
- **客户端 + 集成服务端**（`runClient`）：完整往返链路跑通，见下方「实机验证证据」

### 实机验证证据

服务端（`runServer` 实跑，从控制台输入命令）：

```
[Server thread/INFO] (advanced-backups) Config loaded!!
[Server thread/INFO] (Minecraft) Starting backup...
[Server thread/INFO] (advanced-backups) SAVING DISABLED - PREPARING FOR BACKUP!
[AB Active Backup Thread/INFO] (advanced-backups) Preparing differential backup with name:
  backup_2026-10-01_18-30-51
[AB Active Backup Thread/INFO] (Minecraft) Backup complete!
[AB Active Backup Thread/INFO] (advanced-backups) Preparing snapshot backup with name:
  testsnap_2026-10-01_18-31-21.zip
[AB Active Backup Thread/INFO] (Minecraft) Snapshot created! This will not be auto-deleted.
```

产出物（用 `unzip -l` 与 `manifest.json` 核对过，内容为真实世界数据）：
`backups/world/differential/backup_...-full.zip`、`backups/world/snapshots/testsnap_....zip`

客户端（`runClient`，进世界后由 `frequency.startup` 自动触发备份，全程无异常）：

| 验证项 | 证据 |
|---|---|
| Toast 收到并渲染 | 轮询 `ToastManager` 命中 `BackupToast` 实例，`exists=true, starting=true` |
| 5 秒终态计时 | 实例存在 tick 89 → 206（≈5.85 秒）后被移除，`exists=false` |
| 渲染 API 可用 | 全程无任何异常/崩溃报告 —— `extractRenderState` 每帧执行，说明 `blitSprite(RenderPipelines.GUI_TEXTURED,…)`、`fakeItem`、`fill`、`text` 在真实 GL 管线上全部可执行 |
| 客户端命令注册 | 客户端命令树中 `backup` 节点子命令齐全：`start reload-config reset-chain snapshot cancel reload-client-config` |
| 客户端命令执行 | 真跑 `backup snapshot probe-run` → 服务端建出 `probe-run_2026-10-01_18-57-09.zip`，并把 `Snapshot created!` 回显到玩家聊天栏 |

> 说明：本次验证借助一个**一次性探针 mod**（放在 `run/mods/`，验证后已删除，不入库）。
> 原因是本机为 Wayland 会话，`java.awt.Robot` 的截屏/按键在 XWayland 下会挂死、
> GNOME 又拒绝 D-Bus 截屏请求，无法用截图肉眼确认；
> 且 Loom 的 `runClient` 传游戏参数会被 Fabric 的 dev-launch-injector 丢弃，
> `--quickPlaySingleplayer` 实测无效。探针改为在客户端进程内直接调用原版
> `createWorldOpenFlows().openWorld(...)` 打开测试世界，之后全走真实链路。

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
| Fabric Loom | 1.18-SNAPSHOT（实际解析到 1.18.2） | 当前官方模板所用版本 |
| Fabric Loader | 0.19.5 | 官方模板值 |
| Fabric API | 0.161.0+26.2 | Modrinth 上 26.2 的最新 API |
| `core` 模块 | 需先构建 | 本模块 `apply from: '../../global.properties'`，依赖 `core/build/libs/advancedbackups-corelib.jar`；`core` 自身要求 **JDK 8**（toolchain） |

## 与 1.21 版本的关键差异

1. **不再有映射（mappings）**。Minecraft 26.x 已不再混淆
   （Loom 报 `Cannot use Mojang mappings in a non-obfuscated environment`），
   因此：不存在 `yarn_mappings` 字段；也不能声明 `officialMojangMappings()`；
   类与方法名是 **Mojang 官方命名**。
2. **依赖配置改用 `implementation`**（无混淆环境不需要 remap，`modImplementation` 不再适用）。
3. **`archivesBaseName` → `base { archivesName = ... }`**（Gradle 9 移除顶层属性）。
4. **access widener 已移除**（1.21 那条是历史残留且零引用；无混淆环境下如需 AW，
   命名空间必须写 `official`）。
5. **`pack.mcmeta` 改用 26.2 的新格式**（见下）。

## 已逐项核实的 API 对照（`javap` + `genSources` 反编译 26.2 源码得出）

### 原版类改名

| 1.21（Yarn） | 26.2（Mojang） |
|---|---|
| `net.minecraft.server.MinecraftServer` | 同名 ✓ |
| `net.minecraft.server.network.ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `net.minecraft.server.world.ServerWorld` | `net.minecraft.server.level.ServerLevel` |
| `net.minecraft.util.WorldSavePath` | `net.minecraft.world.level.storage.LevelResource` |
| `net.minecraft.util.Identifier` | **`net.minecraft.resources.Identifier`**（`ResourceLocation` 已不存在） |
| `net.minecraft.network.PacketByteBuf` | `net.minecraft.network.FriendlyByteBuf` |
| `net.minecraft.network.packet.CustomPayload` | `net.minecraft.network.protocol.common.custom.CustomPacketPayload` |
| `CustomPayload.Id<T>` / `getId()` | `CustomPacketPayload.Type<T>` / `type()` |
| `net.minecraft.network.codec.PacketCodec` | `net.minecraft.network.codec.StreamCodec` |
| `net.minecraft.network.codec.PacketCodecs` | `net.minecraft.network.codec.ByteBufCodecs` |
| `PacketCodec.tuple(...)` | `StreamCodec.composite(...)` |
| `net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket` | `net.minecraft.network.protocol.game.ServerboundChatCommandPacket` |
| `net.minecraft.client.MinecraftClient` | `net.minecraft.client.Minecraft` |
| `net.minecraft.client.network.ClientPlayNetworkHandler` | `net.minecraft.client.multiplayer.ClientPacketListener` |
| `LocalPlayer.networkHandler` / `sendPacket` | `LocalPlayer.connection` / `send` |
| `net.minecraft.server.command.CommandManager` | `net.minecraft.commands.Commands` |
| `net.minecraft.server.command.ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `net.minecraft.text.Text` / `Text.of(s)` | `net.minecraft.network.chat.Component` / `Component.nullToEmpty(s)` |
| `ServerCommandSource.sendFeedback(Supplier, boolean)` | `CommandSourceStack.sendSuccess(Supplier, boolean)` |
| `MinecraftServer.getSaveProperties()` | `MinecraftServer.getWorldData()` |
| `MinecraftServer.getSavePath(WorldSavePath)` | `MinecraftServer.getWorldPath(LevelResource)` |
| `MinecraftServer.getWorlds()` | `MinecraftServer.getAllLevels()` |
| `MinecraftServer.saveAll(b,b,b)` | `MinecraftServer.saveAllChunks(b,b,b)` |
| `MinecraftServer.isDedicated()` | `MinecraftServer.isDedicatedServer()` |
| `MinecraftServer.getPlayerManager().getPlayerList()` | `MinecraftServer.getPlayerList().getPlayers()` |
| `ServerLevel.savingDisabled` | `ServerLevel.noSave` |
| `ServerPlayerEntity.getUuidAsString()` | `ServerPlayer.getStringUUID()` |
| `MinecraftClient.getToastManager().add(t)` | `Minecraft.gui.toastManager().addToast(t)` |
| `FabricLoaderImpl.INSTANCE` | `net.fabricmc.loader.api.FabricLoader`（公开 API） |
| `ModContainer.getOrigin().getPaths().get(0)` | `ModContainer.getRootPaths().get(0)`（`getRootPath()`/`getRoot()` 已废弃） |

### 26.2 特有的“体系级”重构（不是简单改名）

这几个是本次移植真正的难点，逐条记录：

#### ① 权限体系整体重写

`hasPermissionLevel(int)` 已从 `CommandSourceStack` / `ServerPlayer` 上**彻底移除**，
改为 `PermissionSet` + `Permission` 常量：

```java
// 旧：runner.hasPermissionLevel(2)
runner.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
// 旧：player.hasPermissionLevel(3)
player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)
```

`PermissionLevel` 枚举为 `ALL(0) / MODERATORS(1) / GAMEMASTERS(2) / ADMINS(3) / OWNERS(4)`，
与旧的 0–4 数字等级一一对应。

#### ② Toast / GUI 渲染模型重构

`net.minecraft.client.gui.DrawContext` **已从 jar 中消失**，取而代之的是
`net.minecraft.client.gui.GuiGraphicsExtractor`（“先抽取渲染状态、再统一提交”）。
`Toast` 接口由 1 个方法拆成 3 个：

| 1.21 | 26.2 |
|---|---|
| `Visibility draw(DrawContext, ToastManager, long)` | `void extractRenderState(GuiGraphicsExtractor, Font, long)`（只画）<br>`Visibility getWantedVisibility()`（只声明可见性）<br>`void update(ToastManager, long)`（只改状态） |
| `context.drawGuiTexture(id, x, y, w, h)` | `graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h)` |
| `context.drawItemWithoutEntity(stack, x, y)` | `graphics.fakeItem(stack, x, y)` |
| `context.drawText(font, s, x, y, c, shadow)` | `graphics.text(font, s, x, y, c, shadow)` |
| `manager.getClient().textRenderer` | `extractRenderState` 传进来的 `Font` 参数 |
| `Toast.getWidth()/getHeight()` | `width()/height()` |
| `I18n.translate(key)` | `I18n.get(key, ...)` |

#### ③ `pack.mcmeta` 改为 major.minor 格式

26.2 的 `version.json` 给出：`resource_major=88, resource_minor=0`，`data_major=107, data_minor=1`。
新版 schema（见 `PackFormat.IntermediaryFormat.PACK_CODEC`）在 major 超过
`lastPreMinorVersion`（资源包 64 / 数据包 81）时**必须**写 `min_format` / `max_format`，
且**不得**再出现 `supported_formats`。本模块因此改为：

```json
{
    "pack": {
        "description": "advancedbackups resources",
        "min_format": [88, 0],
        "max_format": [88, 0]
    }
}
```

（原为 1.21 遗留的 `"pack_format": 9` + 两个 forge 字段。）

#### ④ `ItemStack` 不能在类静态初始化里创建（本次实测发现的真实缺陷）

1.21 及更早版本的 `BackupToast` 里写的是：

```java
public static final ItemStack stack = new ItemStack(Items.PAPER);
```

26.2 上如果在「物品数据组件尚未绑定」的阶段加载该类，会直接抛：

```
java.lang.ExceptionInInitializerError
  Caused by: java.lang.NullPointerException: Components not bound yet
    at net.minecraft.core.Holder$Reference.components(Holder.java:265)
    at net.minecraft.world.item.ItemStack.<init>(ItemStack.java:249)
    at computer.heather.advancedbackups.client.BackupToast.<clinit>(BackupToast.java:96)
```

根因：26.2 的 `ItemStack` 构造会读取物品 `Holder` 上的数据组件，而这些组件要等到
`net.minecraft.client.multiplayer.RegistryDataCollector`（客户端登录收注册表数据）
或服务端 `ReloadableServerResources` 加载时才由
`DataComponentInitializers.PendingComponents#apply()` 绑定；
**标题界面阶段尚未绑定**（本次用客户端崩溃报告实证）。

危害不止那一次异常：`ExceptionInInitializerError` 会让该类的静态初始化永久标记为失败，
此后整个 JVM 生命周期内再访问本类都会变成 `NoClassDefFoundError`，Toast 彻底失效。

正常流程下 `BackupToast` 只在收到备份状态包（那时必定已在世界内）时才首次加载，
所以原写法在实战中不触发；但把类加载时机变成隐式前提并不安全，
故本模块改为**首次真正绘制时惰性创建**，行为完全一致：

```java
public static ItemStack stack;               // 不再在 <clinit> 里创建
private static ItemStack iconStack() { ... } // extractRenderState 里首次调用时才 new
```

> 该问题在上游全部 22 个 loader 模块中都存在（写法一致），属于历史遗留而非本次移植引入。

### Fabric API 侧

- `ServerLifecycleEvents.SERVER_STARTING / SERVER_STARTED / SERVER_STOPPING` ✓ 未变
- `ServerTickEvents.END_SERVER_TICK` ✓ 未变
- `ServerPlayConnectionEvents.JOIN` / `ClientPlayConnectionEvents.JOIN` ✓ 未变
- `PayloadTypeRegistry.playS2C()/playC2S()` → **`clientboundPlay()` / `serverboundPlay()`**
- `ClientCommandManager` → **`ClientCommands`**（同包 `...api.client.command.v2`）
- `ClientPlayNetworking` / `ClientPlayConnectionEvents` 仍在
  `net.fabricmc.fabric.api.client.networking.v1` ✓

## 遗留事项（不影响运行）

- `fabric.mod.json` 里的 `"icon": "assets/advanced-backups/icon.png"` 指向的文件
  **在本仓库中从来不存在**（`assets/` 下实际只有 `advancedbackups/lang/`，
  且目录名是 `advancedbackups` 而非 `advanced-backups`）。
  这是上游 9 个 loader 模块共有的历史遗留问题，为保持一致性本模块未擅自改动；
  实测 Fabric Loader 0.19.5 仅静默忽略，不影响加载。
  若要修：补齐该 png，或直接删掉 `icon` 字段。
