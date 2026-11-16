package io.madoka.magic.network;

import io.madoka.magic.data.PlayerMadokaData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** The current visible form, including for viewers who begin tracking after the transformation. */
public record PlayerFormState(int entityId, UUID playerId, String theme, boolean transformed, boolean witch, boolean halfWitch) {
    private static PlayerFormState of(ServerPlayer player) {
        var data = PlayerMadokaData.of(player);
        return new PlayerFormState(player.getId(), player.getUUID(), data.getThemeId(), data.isTransformed(),
                data.isWitchPath(), data.isHalfWitch());
    }

    public static void broadcast(ServerPlayer player) {
        if (player.connection != null && player.connection.connection.channel() != null) {
            MadokaNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), of(player));
        }
    }

    public static void sendTo(ServerPlayer viewer, ServerPlayer subject) {
        if (viewer.connection != null && viewer.connection.connection.channel() != null) {
            MadokaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), of(subject));
        }
    }

    static void encode(PlayerFormState state, FriendlyByteBuf buffer) {
        buffer.writeVarInt(state.entityId());
        buffer.writeUUID(state.playerId());
        buffer.writeUtf(state.theme());
        buffer.writeBoolean(state.transformed());
        buffer.writeBoolean(state.witch());
        buffer.writeBoolean(state.halfWitch());
    }

    static PlayerFormState decode(FriendlyByteBuf buffer) {
        return new PlayerFormState(buffer.readVarInt(), buffer.readUUID(), buffer.readUtf(),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    static void handle(PlayerFormState state, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.madoka.magic.client.renderer.TransformationVisuals.updateForm(state)));
        ctx.setPacketHandled(true);
    }
}
