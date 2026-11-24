package io.madoka.magic.client;

import net.minecraft.nbt.CompoundTag;

public final class ClientCurseDebt {
    private static String item = "";
    private static double amount;

    private ClientCurseDebt() {}

    public static void accept(CompoundTag tag) {
        item = tag.getString("item");
        double value = tag.getDouble("amount");
        amount = Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public static boolean visible() { return !item.isEmpty(); }
    public static String item() { return item; }
    public static double amount() { return amount; }
    public static void reset() { item = ""; amount = 0; }
}
