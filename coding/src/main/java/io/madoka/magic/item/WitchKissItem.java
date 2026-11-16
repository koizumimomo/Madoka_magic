package io.madoka.magic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import io.madoka.magic.registry.ModItems;

/** 首次击败对应魔女的收藏品。 */
public class WitchKissItem extends Item {

    public WitchKissItem(Properties properties) {
        super(properties);
    }

    public static final String THEME_TAG = "theme";

    public static ItemStack fromWitch(String themeId) {
        ItemStack stack = new ItemStack(ModItems.WITCH_KISS.get());
        stack.getOrCreateTag().putString(THEME_TAG, themeId);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String themeId = stack.getTag() != null ? stack.getTag().getString(THEME_TAG) : "";
        return Component.translatable("item.madoka_magic.witch_kiss",
                themeId.isEmpty() ? "？" : GriefSeedItem.witchName(themeId));
    }
}
