package computer.heather.advancedbackups.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 服务端 → 客户端的<b>备份状态</b>包（S2C，用于驱动客户端的进度 Toast）。
 *
 * <h2>移植说明（这一处是 26.2 改动最密集的网络 API）</h2>
 * <ul>
 *   <li>{@code net.minecraft.network.PacketByteBuf} → {@link FriendlyByteBuf}</li>
 *   <li>{@code net.minecraft.util.Identifier} → {@link Identifier}：
 *       <b>注意</b> 26.x 官方命名里这个类叫 {@code Identifier}（不是 1.21 时代的
 *       {@code ResourceLocation}，后者在 26.2 的 jar 中已不存在）。
 *       {@code Identifier.of(String)} 这个 Yarn 工厂方法也没有了，
 *       带命名空间的字符串改用 {@link Identifier#parse(String)}。</li>
 *   <li>{@code net.minecraft.network.packet.CustomPayload}
 *       → {@link CustomPacketPayload}（包名 {@code net.minecraft.network.protocol.common.custom}）</li>
 *   <li>{@code CustomPayload.Id&lt;T&gt;} → {@link CustomPacketPayload.Type}&lt;T&gt;；
 *       接口方法 {@code getId()} → {@code type()}。</li>
 *   <li>{@code net.minecraft.network.codec.PacketCodec}
 *       → {@link StreamCodec}（{@code net.minecraft.network.codec}）</li>
 *   <li><b>编码器参数顺序翻转</b>：Yarn 的
 *       {@code PacketCodec.of(encoder, decoder)} 里 encoder 是
 *       {@code (value, buf)}；Mojang 的 {@link StreamCodec#of} 用的是
 *       {@code StreamEncoder<O, T>.encode(O, T)}，即 <b>(buf, value)</b>。
 *       所以下面的 lambda 参数顺序与 1.21 版本相反，这是必须改的地方。</li>
 * </ul>
 */
public record PacketBackupStatus(boolean starting, boolean started, boolean failed, boolean finished, boolean cancelled, int progress, int max) implements CustomPacketPayload {

    /** 包的唯一标识：{@code advancedbackups:backup_status}（类型由 Id 改名为 Type）。 */
    public static final Type<PacketBackupStatus> ID = new CustomPacketPayload.Type<PacketBackupStatus>(Identifier.parse("advancedbackups:backup_status"));

    /**
     * 编解码器。
     *
     * <p>26.2 的编码 lambda 参数顺序是 {@code (buf, packet)}（缓冲在前）——
     * 与 1.21 Yarn 的 {@code (packet, buf)} 正好相反，移植时勿照抄旧顺序。</p>
     */
    public static final StreamCodec<FriendlyByteBuf, PacketBackupStatus> CODEC = StreamCodec.of((buf, packet) -> {
        buf.writeBoolean(packet.starting);
        buf.writeBoolean(packet.started);
        buf.writeBoolean(packet.failed);
        buf.writeBoolean(packet.finished);
        buf.writeBoolean(packet.cancelled);
        buf.writeInt(packet.progress);
        buf.writeInt(packet.max);
        },

        buf -> new PacketBackupStatus(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readInt(),
            buf.readInt()
        ));


    /**
     * 返回本包的类型标识。
     * 1.21 里这个方法叫 {@code getId()}，26.2 改名为 {@code type()}。
     */
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }


}
