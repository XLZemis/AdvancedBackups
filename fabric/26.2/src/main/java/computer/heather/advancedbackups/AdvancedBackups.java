package computer.heather.advancedbackups;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import computer.heather.advancedbackups.client.ClientContactor;
import computer.heather.advancedbackups.core.ABCore;
import computer.heather.advancedbackups.core.backups.BackupTimer;
import computer.heather.advancedbackups.core.backups.BackupWrapper;
import computer.heather.advancedbackups.core.config.ConfigManager;
import computer.heather.advancedbackups.network.PacketBackupStatus;
import computer.heather.advancedbackups.network.PacketToastSubscribe;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Advanced Backups 的服务端主入口（{@code fabric.mod.json} 中的 {@code main} 入口点）。
 *
 * <h2>移植说明（1.21 Yarn → 26.2 Mojang 官方命名）</h2>
 * 事件 API 的位置基本没变，改动集中在原版类的成员名上，逐条列出：
 * <ul>
 *   <li>{@code server.getSaveProperties().getLevelName()}
 *       → {@code server.getWorldData().getLevelName()}
 *       （{@code getSaveProperties()} 已改名为 {@code getWorldData()}，
 *       返回的 {@code WorldData} 上仍有 {@code getLevelName()}）</li>
 *   <li>{@code server.getSavePath(WorldSavePath.ROOT)}
 *       → {@code server.getWorldPath(LevelResource.ROOT)}；
 *       {@code net.minecraft.util.WorldSavePath} → {@code net.minecraft.world.level.storage.LevelResource}，
 *       返回值仍是 {@code java.nio.file.Path}（{@code ABCore.worldDir} 的类型，无需改动）</li>
 *   <li>{@code server.getWorlds()} → {@code server.getAllLevels()}，
 *       元素类型 {@code ServerWorld} → {@link ServerLevel}</li>
 *   <li>{@code ServerLevel.savingDisabled} → {@link ServerLevel#noSave}
 *       （同样是 public 字段，可直接读写的布尔量）</li>
 *   <li>{@code server.saveAll(boolean, boolean, boolean)} → {@code server.saveAllChunks(boolean, boolean, boolean)}</li>
 *   <li>{@code server.getPlayerManager().getPlayerList()}
 *       → {@code server.getPlayerList().getPlayers()}，
 *       元素类型 {@code ServerPlayerEntity} → {@link ServerPlayer}</li>
 *   <li>{@code FabricLoaderImpl.INSTANCE.getModContainer(id).get().getOrigin().getPaths().get(0)}
 *       → {@code FabricLoader.getInstance().getModContainer(id).get().getRootPaths().get(0)}：
 *       fabric-loader 0.19.5 上 {@code ModContainer.getRootPath()} 与 {@code getRoot()}
 *       都已被标记 {@code @Deprecated}，未废弃的等价访问器是 {@code getRootPaths()}；
 *       同时这里改用公开 API {@code FabricLoader} 而非 impl 类 {@code FabricLoaderImpl}。</li>
 *   <li>{@code PayloadTypeRegistry.playS2C()} → {@code PayloadTypeRegistry.clientboundPlay()}；
 *       {@code playC2S()} → {@code serverboundPlay()}（这两个注册表现返回带
 *       {@code RegistryFriendlyByteBuf} 泛型参数的实例，因包内不涉及注册表对象，类型可直接推断）</li>
 * </ul>
 */
public class AdvancedBackups implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.

    public static final Logger LOGGER = LoggerFactory.getLogger("advanced-backups");

    /** 转交给 core 的三个日志出口（core 不直接依赖 slf4j）。 */
    public static final Consumer<String> infoLogger = LOGGER::info;
    public static final Consumer<String> warningLogger = LOGGER::warn;
    public static final Consumer<String> errorLogger = LOGGER::error;

    /** 已订阅进度 Toast 的玩家 UUID 字符串集合（见 {@link PacketToastSubscribe#handle}）。 */
    public static final ArrayList<String> players = new ArrayList<>();

    /** 当前服务端实例，由 SERVER_STARTING 事件填充。 */
    public static MinecraftServer server;

    @Override
    public void onInitialize() {

        ServerLifecycleEvents.SERVER_STARTING.register((server) -> {
            AdvancedBackups.server = server;
            // 存档名与存档根目录：core 靠这两个值定位备份目录
            ABCore.worldName = server.getWorldData().getLevelName();
            ABCore.worldDir = server.getWorldPath(LevelResource.ROOT);

            // 把「关保存 / 开保存 / 立即保存一次」三个动作注入 core
            ABCore.disableSaving = AdvancedBackups::disableSaving;
            ABCore.enableSaving = AdvancedBackups::enableSaving;
            ABCore.saveOnce = AdvancedBackups::saveOnce;

            // 注入日志出口
            ABCore.infoLogger = infoLogger;
            ABCore.warningLogger = warningLogger;
            ABCore.errorLogger = errorLogger;

            // 玩家活动状态回调（用于「有玩家在线才备份」这类策略）
            ABCore.resetActivity = AdvancedBackups::resetActivity;

            // 服务端侧的客户端联系器：真正执行发包
            ABCore.clientContactor = new ClientContactor();
            // 记录本 mod 的 jar 路径（core 的自更新/自检会用到）
            // fabric-loader 0.19.5 上 ModContainer.getRootPath()/getRoot() 都已被标记 @Deprecated，
            // 未废弃的等价写法是 getRootPaths().get(0)（普通 mod 只有一个根路径）。
            ABCore.modJar = new File(FabricLoader.getInstance().getModContainer("advancedbackups").get().getRootPaths().get(0).toAbsolutePath().toString());


            ConfigManager.loadOrCreateConfig();
            LOGGER.info("Config loaded!!");
        });

        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            // 服务器完全启动后才有必要检查「启动时备份」
            BackupWrapper.checkStartupBackups();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
            // 关服前检查「关闭时备份」
            BackupWrapper.checkShutdownBackups();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // 一旦有玩家加入就标记为「活跃」，让定时备份按策略运行
            ABCore.setActivity(true);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdvancedBackupsCommand.register(dispatcher);
        });


        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            // 每 tick 驱动一次备份计时器（core 内部自行判断是否到点）
            BackupTimer.check();
        });

        // 注册两个自定义包：S2C 的状态包 + C2S 的订阅包
        PayloadTypeRegistry.clientboundPlay().register(PacketBackupStatus.ID, PacketBackupStatus.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PacketToastSubscribe.ID, PacketToastSubscribe.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PacketToastSubscribe.ID, PacketToastSubscribe::handle);


    }




    /** 关闭所有维度的自动保存（备份期间保证磁盘上是一致的快照）。 */
    public static void disableSaving() {
        MinecraftServer server = AdvancedBackups.server;
        for (ServerLevel level : server.getAllLevels()) {
            if (level != null && !level.noSave) {
                level.noSave = true;
            }
        }
    }

    /** 恢复所有维度的自动保存。 */
    public static void enableSaving() {
        MinecraftServer server = AdvancedBackups.server;
        for (ServerLevel level : server.getAllLevels()) {
            if (level != null && level.noSave) {
                level.noSave = false;
            }
        }
    }

    /**
     * 立即把所有维度保存到磁盘一次。
     *
     * @param flush 是否等待写入落盘（对应原 {@code saveAll} 的第二个参数）
     */
    public static void saveOnce(boolean flush) {
        MinecraftServer server = AdvancedBackups.server;
        server.saveAllChunks(true, flush, true);
    }

    /** 按当前在线玩家数刷新「活跃」状态。 */
    public static void resetActivity() {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        ABCore.setActivity(!players.isEmpty());
    }
}
