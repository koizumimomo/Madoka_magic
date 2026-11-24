package io.madoka.magic.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

public final class ClientProgression {
    private static CompoundTag state = new CompoundTag();
    private static long receivedAt;

    private ClientProgression() {}

    public static void accept(CompoundTag packet) {
        state = packet.copy();
        ClientCurseStacks.accept(packet.getCompound("curseStacks"));
        ClientCurseDebt.accept(packet.getCompound("curseDebt"));
        io.madoka.magic.data.OriginalCurseDefinitions.acceptClient(packet.getCompound("originalCurseDefs"));
        io.madoka.magic.item.RitualTooltipValues.accept(packet.contains("seedPurification", Tag.TAG_ANY_NUMERIC)
                ? packet.getDouble("seedPurification") : Double.NaN);
        var level = Minecraft.getInstance().level;
        receivedAt = level == null ? 0 : level.getGameTime();
    }

    public static CompoundTag state() {
        return state;
    }

    public static List<CompoundTag> nodes() {
        return state.getList("nodes", Tag.TAG_COMPOUND).stream().map(value -> (CompoundTag) value).toList();
    }

    public static double cooldown(String id, float partialTick) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0;
        long remaining = state.getCompound("cooldowns").getCompound(id).getLong("remaining");
        return Math.max(0, remaining - Math.max(0, level.getGameTime() - receivedAt) - partialTick);
    }

    public static int cooldownLength(String id) {
        return Math.max(1, state.getCompound("cooldowns").getCompound(id).getInt("total"));
    }

    public static void reset() {
        state = new CompoundTag();
        ClientCurseStacks.reset();
        ClientCurseDebt.reset();
        io.madoka.magic.data.OriginalCurseDefinitions.acceptClient(new CompoundTag());
        io.madoka.magic.item.RitualTooltipValues.accept(Double.NaN);
        receivedAt = 0;
    }
}
