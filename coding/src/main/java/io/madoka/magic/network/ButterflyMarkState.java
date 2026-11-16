package io.madoka.magic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

public record ButterflyMarkState(int entityId, UUID uuid, int layers, int remaining) {
    public static void broadcast(LivingEntity target, int layers, int remaining) {
        if (!target.level().isClientSide) {
            MadokaNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                    new ButterflyMarkState(target.getId(), target.getUUID(), layers, remaining));
        }
    }

    public static void sendTo(ServerPlayer viewer, LivingEntity target, int layers, int remaining) {
        if (viewer.connection != null && viewer.connection.connection.channel() != null) {
            MadokaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new ButterflyMarkState(target.getId(), target.getUUID(), layers, remaining));
        }
    }

    static void encode(ButterflyMarkState state, FriendlyByteBuf buffer) {
        buffer.writeVarInt(state.entityId);
        buffer.writeUUID(state.uuid);
        buffer.writeVarInt(state.layers);
        buffer.writeVarInt(state.remaining);
    }

    static ButterflyMarkState decode(FriendlyByteBuf buffer) {
        return new ButterflyMarkState(buffer.readVarInt(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt());
    }

    static void handle(ButterflyMarkState state, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.madoka.magic.client.renderer.ButterflyMarkVisuals.accept(state)));
        ctx.setPacketHandled(true);
    }
}
