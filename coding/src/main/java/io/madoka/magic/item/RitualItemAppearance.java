package io.madoka.magic.item;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** The existing magical-girl or witch appearance represented by a ritual item. */
public record RitualItemAppearance(String theme, boolean witch) {
    @Nullable
    public static RitualItemAppearance of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        boolean witch = stack.getItem() instanceof GriefSeedItem;
        String theme = witch ? GriefSeedItem.getOwner(stack)
                : stack.getItem() instanceof ContractItem ? ContractItem.getThemeId(stack) : "";
        return theme.isEmpty() ? null : new RitualItemAppearance(theme, witch);
    }
}
