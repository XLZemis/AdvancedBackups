package computer.heather.advancedbackups.client;

import java.util.List;

import computer.heather.advancedbackups.AdvancedBackups;
import computer.heather.advancedbackups.interfaces.IClientContactor;
import computer.heather.advancedbackups.network.NetworkHandler;
import computer.heather.advancedbackups.network.PacketBackupStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * {@link IClientContactor} 的 Fabric 实现：在备份状态变化的各个节点，
 * 把 {@link PacketBackupStatus} 推送给订阅了 Toast 的玩家。
 *
 * <p>类名虽然带 {@code client} 包，但它实际运行在<b>服务端</b>——
 * 只是它负责「与客户端通信」而已（1.21 起就一直放在这个包里，移植时保持包结构不变）。</p>
 *
 * <h2>移植说明</h2>
 * <ul>
 *   <li>{@code net.minecraft.server.network.ServerPlayerEntity} → {@link ServerPlayer}</li>
 *   <li>{@code player.getUuidAsString()} → {@code player.getStringUUID()}</li>
 *   <li>{@code player.hasPermissionLevel(3)} → {@code player.permissions().hasPermission(Permissions.COMMANDS_ADMIN)}：
 *       26.x 的权限体系被整体重写，{@code hasPermissionLevel(int)} 已不存在；
 *       原来的数字等级 3（管理员）对应 {@link Permissions#COMMANDS_ADMIN}。</li>
 *   <li>{@code server.isDedicated()} → {@code server.isDedicatedServer()}</li>
 *   <li>{@code server.getPlayerManager().getPlayerList()} → {@code server.getPlayerList().getPlayers()}</li>
 * </ul>
 *
 * <p><b>关于 {@code @SuppressWarnings("deprecation")}</b>：{@link IClientContactor} 里这 5 个方法
 * 在上游 core 中就被标成了 {@code @Deprecated}（注释写明「不该再被直接调用」，真实入口是
 * {@code handle(...)}）；但因为 core 仍锁定 Java 8 目标、接口方法不能是 private，
 * 实现类必须逐个覆写它们。所以这里的警告是<b>上游接口设计使然</b>，
 * 不是本次移植引入的用法问题，就地抑制以免掩盖真正的新警告。</p>
 */
@SuppressWarnings("deprecation")
public class ClientContactor implements IClientContactor {

    @Override
    public void backupComplete(boolean all) {
        MinecraftServer server = AdvancedBackups.server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, false, true, false, 0, 0);
        for (ServerPlayer player : players) {
            if (!AdvancedBackups.players.contains(player.getStringUUID())) continue;
            if (!server.isDedicatedServer() || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) || all) {
                NetworkHandler.sendToClient(player, packet);
            }
        }
    }

    @Override
    public void backupFailed(boolean all) {
        MinecraftServer server = AdvancedBackups.server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, true, false, false, 0, 0);
        for (ServerPlayer player : players) {
            if (!AdvancedBackups.players.contains(player.getStringUUID())) continue;
            if (!server.isDedicatedServer() || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) || all) {
                NetworkHandler.sendToClient(player, packet);
            }
        }
    }

    @Override
    public void backupProgress(int progress, int max, boolean all) {
        MinecraftServer server = AdvancedBackups.server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        PacketBackupStatus packet = new PacketBackupStatus(false, true, false, false, false, progress, max);
        for (ServerPlayer player : players) {
            if (!AdvancedBackups.players.contains(player.getStringUUID())) continue;
            if (!server.isDedicatedServer() || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) || all) {
                NetworkHandler.sendToClient(player, packet);
            }
        }
    }

    @Override
    public void backupStarting(boolean all) {
        MinecraftServer server = AdvancedBackups.server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        PacketBackupStatus packet = new PacketBackupStatus(true, false, false, false, false, 0, 0);
        for (ServerPlayer player : players) {
            if (!AdvancedBackups.players.contains(player.getStringUUID())) continue;
            if (!server.isDedicatedServer() || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) || all) {
                NetworkHandler.sendToClient(player, packet);
            }
        }
    }

    @Override
    public void backupCancelled(boolean all) {
        MinecraftServer server = AdvancedBackups.server;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        PacketBackupStatus packet = new PacketBackupStatus(false, false, false, false, true, 0, 0);
        for (ServerPlayer player : players) {
            if (!AdvancedBackups.players.contains(player.getStringUUID())) continue;
            if (!server.isDedicatedServer() || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN) || all) {
                NetworkHandler.sendToClient(player, packet);
            }
        }
    }
}
