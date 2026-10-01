package computer.heather.advancedbackups.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端 → 客户端发包的统一出口。
 *
 * <h2>移植说明</h2>
 * 只有一处改名：{@code net.minecraft.server.network.ServerPlayerEntity}
 * → {@link ServerPlayer}（26.x 起为 Mojang 官方命名）。
 * {@link ServerPlayNetworking#send(ServerPlayer, net.minecraft.network.protocol.common.custom.CustomPacketPayload)}
 * 的签名本身没有变化，只是玩家类换了包。
 */
public class NetworkHandler {

    /**
     * 把备份状态包发给指定玩家。
     *
     * @param player 目标玩家（26.2：{@link ServerPlayer}）
     * @param packet 备份状态包
     */
    public static void sendToClient(ServerPlayer player, PacketBackupStatus packet) {

        ServerPlayNetworking.send(player, packet);

    }



}
