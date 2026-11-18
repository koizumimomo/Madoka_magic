package io.madoka.magic.network;

import io.madoka.magic.data.PassiveTree;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.spell.SpellCasting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record ProgressionState(CompoundTag state) {
    public static void send(ServerPlayer player) {
        var data = PlayerMadokaData.of(player);
        if (PassiveTree.award(player, data)) data.save(player);
        CompoundTag tag = new CompoundTag();
        tag.putString("theme", data.getThemeId());
        tag.putInt("points", data.getTalentPoints());
        tag.putInt("earned", data.getEarnedTalentPoints());
        tag.putInt("interval", PassiveTree.causalityPerPoint());
        tag.putDouble("seedPurification", io.madoka.magic.config.MadokaConfig.SEED_TAINT_PER_DURABILITY.get());
        var nodes = new ListTag();
        for (var node : PassiveTree.nodes()) {
            if (!node.matches(data)) continue;
            var value = new CompoundTag();
            value.putString("id", node.id());
            value.putString("effect", node.effect());
            value.putString("passive", node.passive());
            var requires = new CompoundTag();
            node.requires().forEach(requires::putInt);
            value.put("requires", requires);
            value.putInt("rank", data.getTalentRank(node.id()));
            value.putInt("max", node.maxRank());
            value.putDouble("perRank", node.perRank());
            value.putDouble("x", node.x());
            value.putDouble("y", node.y());
            value.putString("nameKey", node.nameKey());
            value.putBoolean("canLearn", PassiveTree.canLearn(data, node));
            nodes.add(value);
        }
        tag.put("nodes", nodes);
        tag.put("cooldowns", SpellCasting.cooldownSnapshot(player));
        MadokaNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ProgressionState(tag));
    }

    public static void encode(ProgressionState value, FriendlyByteBuf buffer) {
        buffer.writeNbt(value.state);
    }

    public static ProgressionState decode(FriendlyByteBuf buffer) {
        var tag = buffer.readNbt();
        return new ProgressionState(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ProgressionState packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> io.madoka.magic.client.ClientProgression.accept(packet.state)));
        ctx.setPacketHandled(true);
    }

    public record LearnTalent(String id) {
        public static void encode(LearnTalent value, FriendlyByteBuf buffer) {
            buffer.writeUtf(value.id, 128);
        }

        public static LearnTalent decode(FriendlyByteBuf buffer) {
            return new LearnTalent(buffer.readUtf(128));
        }

        public static void handle(LearnTalent packet, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                var player = ctx.getSender();
                if (player != null && player.isAlive() && !player.isSpectator()) PassiveTree.learn(player, packet.id);
            });
            ctx.setPacketHandled(true);
        }
    }
}
