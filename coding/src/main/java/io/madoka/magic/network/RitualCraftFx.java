package io.madoka.magic.network;

import io.madoka.magic.item.RitualItemAppearance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record RitualCraftFx(ResourceLocation dimension, BlockPos pos, RitualItemAppearance appearance) {
    public static final double VIEW_DISTANCE = 32.0D;

    public static void send(ServerLevel level, BlockPos pos, RitualItemAppearance appearance) {
        var point = PacketDistributor.TargetPoint.p(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                VIEW_DISTANCE, level.dimension());
        MadokaNetwork.CHANNEL.send(PacketDistributor.NEAR.with(point),
                new RitualCraftFx(level.dimension().location(), pos.immutable(), appearance));
    }

    static void encode(RitualCraftFx cue, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(cue.dimension());
        buffer.writeBlockPos(cue.pos());
        buffer.writeUtf(cue.appearance().theme());
        buffer.writeBoolean(cue.appearance().witch());
    }

    static RitualCraftFx decode(FriendlyByteBuf buffer) {
        return new RitualCraftFx(buffer.readResourceLocation(), buffer.readBlockPos(),
                new RitualItemAppearance(buffer.readUtf(), buffer.readBoolean()));
    }

    static void handle(RitualCraftFx cue, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.madoka.magic.client.renderer.AltarRitualVisuals.start(cue)));
        ctx.setPacketHandled(true);
    }
}
