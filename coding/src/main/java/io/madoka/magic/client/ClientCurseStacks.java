package io.madoka.magic.client;

import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Keeps transition timestamps separate from repeated network snapshots. */
public final class ClientCurseStacks {
    public record View(String identity, String item, String theme, String nameKey, int level, int stacks, int max) {
        public boolean ready() { return stacks >= max; }
    }

    private static View view;
    private static boolean hiding;
    private static long hiddenAt, gainedAt = -10000, completedAt = -10000, spentAt = -10000;

    private ClientCurseStacks() {}

    public static void accept(CompoundTag tag) {
        long now = Util.getMillis();
        if (tag.isEmpty() || tag.getInt("max") < 1) {
            if (view != null && !hiding) { hiding = true; hiddenAt = now; }
            return;
        }
        int max = Mth.clamp(tag.getInt("max"), 1, 8);
        var next = new View(tag.hasUUID("identity") ? tag.getUUID("identity").toString() : tag.getString("item"),
                tag.getString("item"), tag.getString("theme"), tag.getString("nameKey"),
                Mth.clamp(tag.getInt("level"), 1, 3), Mth.clamp(tag.getInt("stacks"), 0, max), max);
        if (view == null || hiding || !view.identity().equals(next.identity()) || view.level() != next.level()) {
            gainedAt = completedAt = spentAt = -10000;
        } else {
            if (next.stacks() > view.stacks()) gainedAt = now;
            if (next.ready() && !view.ready()) completedAt = now;
            if (view.ready() && next.stacks() < view.stacks()) spentAt = now;
        }
        view = next;
        hiding = false;
    }

    public static View view() {
        return view != null && opacity() > 0 ? view : null;
    }

    public static float opacity() {
        return hiding ? Mth.clamp(1 - (Util.getMillis() - hiddenAt) / 180f, 0, 1) : 1;
    }

    public static float gainAge() { return (Util.getMillis() - gainedAt) / 1000f; }
    public static float completeAge() { return (Util.getMillis() - completedAt) / 1000f; }
    public static float spentAge() { return (Util.getMillis() - spentAt) / 1000f; }

    public static void reset() {
        view = null;
        hiding = false;
        gainedAt = completedAt = spentAt = -10000;
    }
}
