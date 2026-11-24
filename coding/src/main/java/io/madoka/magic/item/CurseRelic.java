package io.madoka.magic.item;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Common-side identity without loading the optional Curios API. */
public interface CurseRelic {
    String LEVEL_TAG = "madoka_curse_level";

    String theme();

    static int level(ItemStack stack) {
        return stack.hasTag() ? Mth.clamp(stack.getTag().getInt(LEVEL_TAG), 1, 3) : 1;
    }
}
