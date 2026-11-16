package io.madoka.magic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;
import java.util.function.Supplier;

/** A single visual cue. The server owns the outcome and the client draws its motion. */
public record TransformationFx(int entityId, UUID playerId, Vec3 origin, float height, String theme, Kind kind) {
    public enum Kind { TRANSFORM, WITCH, HALF_WITCH }

    public static void send(ServerPlayer player, String theme, Kind kind) {
        if (player.connection != null && player.connection.connection.channel() != null) {
            var effect = new TransformationFx(player.getId(), player.getUUID(), player.position(),
                    player.getBbHeight(), theme, kind);
            MadokaNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), effect);
        }
    }

    static void encode(TransformationFx effect, FriendlyByteBuf buffer) {
        buffer.writeVarInt(effect.entityId());
        buffer.writeUUID(effect.playerId());
        buffer.writeDouble(effect.origin().x);
        buffer.writeDouble(effect.origin().y);
        buffer.writeDouble(effect.origin().z);
        buffer.writeFloat(effect.height());
        buffer.writeUtf(effect.theme());
        buffer.writeEnum(effect.kind());
    }

    static TransformationFx decode(FriendlyByteBuf buffer) {
        return new TransformationFx(buffer.readVarInt(), buffer.readUUID(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                buffer.readFloat(), buffer.readUtf(), buffer.readEnum(Kind.class));
    }

    static void handle(TransformationFx effect, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.madoka.magic.client.renderer.TransformationVisuals.start(effect)));
        ctx.setPacketHandled(true);
    }
}
