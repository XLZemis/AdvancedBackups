package computer.heather.advancedbackups.client;

import computer.heather.advancedbackups.AdvancedBackups;
import computer.heather.advancedbackups.core.ABCore;
import computer.heather.advancedbackups.core.config.ClientConfigManager;
import computer.heather.advancedbackups.network.PacketBackupStatus;
import computer.heather.advancedbackups.network.PacketToastSubscribe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * 客户端入口（{@code fabric.mod.json} 中的 {@code client} 入口点）。
 *
 * <h2>移植说明</h2>
 * <ul>
 *   <li>{@code net.minecraft.client.MinecraftClient} → {@link Minecraft}
 *       （26.x 起官方命名，类名里没有 {@code Client} 后缀）。</li>
 *   <li>{@code ClientPlayConnectionEvents.JOIN} 与
 *       {@code ClientLifecycleEvents.CLIENT_STARTED} 的包路径与回调签名<b>均未变</b>，
 *       仅第三个参数由 {@code MinecraftClient} 换成 {@link Minecraft}。</li>
 *   <li>{@code packet.getId()} → {@code packet.type()}（见 {@link PacketBackupStatus}）。</li>
 *   <li>{@code MinecraftClient.getInstance().getToastManager().add(...)}
 *       → {@code Minecraft.getInstance().gui.toastManager().addToast(...)}：
 *       26.2 起 {@code ToastManager} 不再挂在 {@code Minecraft} 上，
 *       而是挪到了 {@code Minecraft.gui}（{@code net.minecraft.client.gui.Gui}）下的
 *       {@code toastManager()} 访问器；添加方法也由 {@code add(Toast)} 改名为
 *       {@code addToast(Toast)}。</li>
 * </ul>
 */
public class ClientWrapper implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // 客户端也要拿到日志出口（core 的客户端逻辑会写日志）
        ABCore.infoLogger = AdvancedBackups.infoLogger;
        ABCore.warningLogger = AdvancedBackups.warningLogger;
        ABCore.errorLogger = AdvancedBackups.errorLogger;

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            // 客户端配置与 core 的客户端配置都在此时读盘
            ClientConfigManager.loadOrCreateConfig();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 进入服务器时，按本地配置决定是否订阅进度 Toast
            PacketToastSubscribe packet = new PacketToastSubscribe(ClientConfigManager.showProgress.get());
            if (ClientPlayNetworking.canSend(packet.type())) {
                //Make sure a server can receive the packet before trying to send!
                ClientPlayNetworking.send(packet);
            }
            else {
                ABCore.warningLogger.accept("Refusing to send packet " + packet + " as the server cannot accept it!");
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            AdvancedBackupsClientCommand.register(dispatcher);

        });


        ClientPlayNetworking.registerGlobalReceiver(PacketBackupStatus.ID, ClientWrapper::handle);


    }



    /**
     * 收到服务端推来的备份状态包后的处理。
     *
     * <p>包是在网络线程上收到的，必须切回客户端主线程再动 GUI 状态和 Toast 管理器。</p>
     */
    public static void handle(PacketBackupStatus message, ClientPlayNetworking.Context context) {

        Minecraft.getInstance().execute(() -> {
            BackupToast.starting = message.starting();
            BackupToast.started = message.started();
            BackupToast.failed = message.failed();
            BackupToast.finished = message.finished();
            BackupToast.cancelled = message.cancelled();

            BackupToast.progress = message.progress();
            BackupToast.max = message.max();

            // 只在还没有 Toast 时新建，避免同一时间堆叠出多条
            if (!BackupToast.exists) {
                BackupToast.exists = true;
                Minecraft.getInstance().gui.toastManager().addToast(new BackupToast());
            }

        });


    }

}
