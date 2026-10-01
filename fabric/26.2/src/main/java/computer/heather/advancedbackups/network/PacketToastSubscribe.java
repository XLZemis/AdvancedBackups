package computer.heather.advancedbackups.network;

import computer.heather.advancedbackups.AdvancedBackups;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 → 服务端的<b>订阅/退订进度 Toast</b> 包（C2S）。
 *
 * <h2>移植说明</h2>
 * 与 {@link PacketBackupStatus} 相同的改名规则之外，这里还有两处特有改动：
 * <ul>
 *   <li>{@code net.minecraft.network.codec.PacketCodecs} → {@link ByteBufCodecs}
 *       （单复数之别：26.2 是 {@code ByteBufCodecs}）。</li>
 *   <li>{@code PacketCodec.tuple(codec, getter, factory)} → {@link StreamCodec#composite}
 *       —— 名字由 {@code tuple} 改成 {@code composite}，参数含义不变。</li>
 *   <li>{@code player.getUuidAsString()} → {@code player.getStringUUID()}
 *       （Mojang 命名；实现在 {@code net.minecraft.world.entity.Entity} 上）。</li>
 * </ul>
 */
public record PacketToastSubscribe(boolean enable) implements CustomPacketPayload {

    /** 包的唯一标识：{@code advancedbackups:toast_subscribe}。 */
    public static final Type<PacketToastSubscribe> ID = new CustomPacketPayload.Type<PacketToastSubscribe>(Identifier.parse("advancedbackups:toast_subscribe"));

    /** 显式写出规范构造器（与原实现保持一致，便于将来加校验而不用改结构）。 */
    public PacketToastSubscribe(boolean enable) {
        this.enable = enable;
    }

    /**
     * 编解码器：单字段 {@code enable}。
     * {@code ByteBufCodecs.BOOL} 的泛型上界是 {@code ByteBuf}，而这里声明的是
     * {@code FriendlyByteBuf}（其父类型），可以正常满足 {@code ? super B} 约束。
     */
    public static final StreamCodec<FriendlyByteBuf, PacketToastSubscribe> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, PacketToastSubscribe::enable, PacketToastSubscribe::new);


    /**
     * 服务端收到订阅请求后的处理：维护需要接收进度包的白名单。
     *
     * @param message 客户端发来的订阅/退订请求
     * @param context Fabric 提供的上下文，{@code context.player()} 即发件玩家
     */
    public static void handle(PacketToastSubscribe message, ServerPlayNetworking.Context context) {

        ServerPlayer player = context.player();

        // 用玩家 UUID 字符串作键，避免实体引用长期持有导致的内存泄漏
        if (message.enable() && !AdvancedBackups.players.contains(player.getStringUUID())) {
            AdvancedBackups.players.add(player.getStringUUID());
        }
        else if (!message.enable()) {
            AdvancedBackups.players.remove(player.getStringUUID());
        }

    }



    /** 返回本包的类型标识（1.21 的 {@code getId()} 在 26.2 改名为 {@code type()}）。 */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }



}
