package computer.heather.advancedbackups;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;

import computer.heather.advancedbackups.core.CoreCommandSystem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/**
 * 服务端 {@code /backup} 命令注册。
 *
 * <h2>移植说明（1.21 Yarn 命名 → 26.2 Mojang 官方命名）</h2>
 * <ul>
 *   <li>{@code net.minecraft.server.command.CommandManager} → {@link Commands}</li>
 *   <li>{@code net.minecraft.server.command.ServerCommandSource} → {@link CommandSourceStack}</li>
 *   <li>{@code net.minecraft.text.Text} → {@link Component}</li>
 *   <li>{@code Text.of(String)} → {@link Component#nullToEmpty(String)}：
 *       两者语义一致（null 输入返回空文本而非抛 NPE），比 {@code Component.literal} 更忠实于原实现。</li>
 *   <li>{@code ServerCommandSource.sendFeedback(Supplier&lt;Text&gt;, boolean)}
 *       → {@link CommandSourceStack#sendSuccess(java.util.function.Supplier, boolean)}。
 *       注意 {@code sendFeedback} 这个旧名字在 26.2 已不存在，只有 {@code sendSuccess} / {@code sendFailure}。</li>
 *   <li><b>权限体系在 26.x 被整体重写</b>：{@code hasPermissionLevel(int)} 已从
 *       {@code CommandSourceStack} 上移除，取而代之的是
 *       {@link net.minecraft.server.permissions.PermissionSet} +
 *       {@link net.minecraft.server.permissions.Permission} 常量体系。
 *       原 {@code hasPermissionLevel(2)}（游戏管理员档，即 GAMEMASTERS）对应
 *       {@link Permissions#COMMANDS_GAMEMASTER}；
 *       三档管理员 {@code hasPermissionLevel(3)} 对应 {@link Permissions#COMMANDS_ADMIN}。</li>
 * </ul>
 */
public class AdvancedBackupsCommand {

    /**
     * 把一个 {@code backup} 字面量命令注册到给定的 dispatcher 上。
     *
     * @param stack 服务端命令分发器（26.2 中泛型参数为 {@link CommandSourceStack}）
     */
    public static void register(CommandDispatcher<CommandSourceStack> stack) {
        stack.register(Commands.literal("backup").requires((runner) -> {
            // 单人存档（集成服务器）不限制权限；专用服务器要求权限等级 2（游戏管理员）
            return !AdvancedBackups.server.isDedicatedServer()
                    || runner.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        }).then(Commands.literal("start").executes((runner) -> {
            // 手动触发一次备份；core 通过回调把结果文本回传，这里直接发给命令来源
            CoreCommandSystem.startBackup((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            });
            return 1;
         }))

         .then(Commands.literal("reload-config").executes((runner) -> {
            // 重新读取磁盘上的服务端配置
            CoreCommandSystem.reloadConfig((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            });
            return 1;
         }))

         .then(Commands.literal("reset-chain").executes((runner) -> {
            // 把链式备份计数器归零（下一次备份重新走 full）
            CoreCommandSystem.resetChainLength((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            });
            return 1;
         }))

         .then(Commands.literal("snapshot").executes((runner) -> {
            // 无参形式：快照名固定为 "snapshot"
            CoreCommandSystem.snapshot((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            }, "snapshot");
            return 1;
         })
         .then(Commands.argument("name", StringArgumentType.greedyString()).executes((runner) -> {
            // 有参形式：把整行剩余内容当作快照名重新解析一遍取原始字符串
            ParseResults<CommandSourceStack> parseResults = stack.parse(StringArgumentType.getString(runner, "name"), runner.getSource());
            String snapshotName = parseResults.getReader().getString();
            CoreCommandSystem.snapshot((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            }, snapshotName);
            return 1;
         })))

         .then(Commands.literal("cancel").executes((runner) -> {
            // 请求取消正在进行的备份
            CoreCommandSystem.cancelBackup((response) -> {
                runner.getSource().sendSuccess(() -> Component.nullToEmpty(response), true);
            });
            return 1;
         }))

         // 该子命令只在客户端有意义（见 client/AdvancedBackupsClientCommand），
         // 服务端保留一个占位分支以便给出友好提示
         .then(Commands.literal("reload-client-config").executes((runner) -> {
            runner.getSource().sendSuccess(() -> Component.nullToEmpty("This command can only be ran on the client!"), true);
            return 1;
         }))

        );
    }


}
