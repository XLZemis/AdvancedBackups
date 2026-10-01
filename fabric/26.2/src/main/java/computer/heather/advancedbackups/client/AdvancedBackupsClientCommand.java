package computer.heather.advancedbackups.client;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;

import computer.heather.advancedbackups.core.CoreCommandSystem;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;

/**
 * 客户端 {@code /backup} 命令。
 *
 * <p>存在意义：单人/专用服务器的服务端命令在客户端的补全与提示体验较差，
 * 因此客户端自己注册一份同名命令，把动作<b>转成一条聊天命令</b>发给服务端执行。
 * 但 {@code reload-client-config} 例外——它纯粹是本地的。</p>
 *
 * <h2>移植说明</h2>
 * <ul>
 *   <li>{@code net.fabricmc.fabric.api.client.command.v2.ClientCommandManager}
 *       → {@link ClientCommands}（包名不变，只是类名去掉了 {@code Manager}）；
 *       {@code FabricClientCommandSource} 不变。</li>
 *   <li>{@code MinecraftClient} → {@link Minecraft}。</li>
 *   <li>{@code net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket}
 *       → {@link ServerboundChatCommandPacket}（包名 {@code net.minecraft.network.protocol.game}，
 *       构造器仍是单 {@code String} 参数）。</li>
 *   <li>{@code MinecraftClient.getInstance().player.networkHandler}
 *       → {@code Minecraft.getInstance().player.connection}
 *       （{@code LocalPlayer.connection} 是 public final 字段，类型 {@code ClientPacketListener}）；
 *       {@code sendPacket(...)} → {@code send(...)}（继承自 {@code ClientCommonPacketListenerImpl}）。</li>
 *   <li>{@code Text.of(String)} → {@link Component#nullToEmpty(String)}；
 *       客户端的 {@link FabricClientCommandSource#sendFeedback(Component)}
 *       仍然只收一个参数（不像服务端那样还要一个 {@code broadcastToAdmins} 布尔量）。</li>
 * </ul>
 */
public class AdvancedBackupsClientCommand {

    /**
     * 把客户端的 {@code backup} 命令注册到客户端命令分发器上。
     *
     * @param dispatcher 客户端命令分发器（来源类型为 {@link FabricClientCommandSource}）
     */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("backup").requires((runner) -> {
            // 客户端命令永远可用；真正的权限判定发生在服务端那一侧
            return true;
        }).then(ClientCommands.literal("start").executes((runner) -> {
            sendCommand("backup start");
            return 1;
         }))

         .then(ClientCommands.literal("reload-config").executes((runner) -> {
            sendCommand("backup reload-config");
            return 1;
         }))

         .then(ClientCommands.literal("reset-chain").executes((runner) -> {
            sendCommand("backup reset-chain");
            return 1;
         }))

         .then(ClientCommands.literal("snapshot").executes((runner) -> {
            sendCommand("backup snapshot");
            return 1;
         })

         .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes((runner) -> {
            // 把整行剩余内容原样取出，拼成一条服务端命令发过去
            ParseResults<FabricClientCommandSource> parseResults = dispatcher.parse(StringArgumentType.getString(runner, "name"), runner.getSource());
            String snapshotName = parseResults.getReader().getString();
            sendCommand("backup snapshot " + snapshotName);
            return 1;
         })))

         .then(ClientCommands.literal("cancel").executes((runner) -> {
            sendCommand("backup cancel");
            return 1;
         }))

         // 唯一一个真正在客户端本地执行的子命令
         .then(ClientCommands.literal("reload-client-config").executes((runner) -> {
            CoreCommandSystem.reloadClientConfig((response) -> {
                runner.getSource().sendFeedback(Component.nullToEmpty(response));
            });
            return 1;
         }))

        );
    }

    /**
     * 把一条命令字符串当作聊天命令发给当前连接的服务端。
     *
     * <p>若玩家尚未进入世界（{@code player}/{@code connection} 为 null），静默忽略，
     * 避免在标题界面误触命令导致空指针崩溃。</p>
     *
     * @param command 不含前导斜杠的命令正文，例如 {@code "backup start"}
     */
    private static void sendCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) {
            return;
        }
        client.player.connection.send(new ServerboundChatCommandPacket(command));
    }


}
