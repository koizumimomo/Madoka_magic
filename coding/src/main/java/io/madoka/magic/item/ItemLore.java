package io.madoka.magic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.math.BigDecimal;
import java.util.List;

/** Localized flavor text and current usage information for ritual items. */
final class ItemLore {
    private ItemLore() {}

    static void contract(ItemStack stack, List<Component> tooltip) {
        String theme = ContractItem.getThemeId(stack);
        flavor(tooltip, "contract", theme, ".first");
        flavor(tooltip, "contract", theme, ".second");
        tooltip.add(Component.translatable("tooltip.madoka_magic.contract.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.madoka_magic.contract.identity").withStyle(ChatFormatting.DARK_AQUA));
    }

    static void seed(ItemStack stack, Level level, List<Component> tooltip) {
        flavor(tooltip, "grief_seed", GriefSeedItem.getOwner(stack), "");
        double purification = RitualTooltipValues.seedPurification(level);
        String amount = BigDecimal.valueOf(purification).stripTrailingZeros().toPlainString();
        tooltip.add((purification > 0 ? Component.translatable("tooltip.madoka_magic.grief_seed.use", amount)
                : Component.translatable("tooltip.madoka_magic.grief_seed.disabled"))
                .withStyle(ChatFormatting.GRAY));
        int capacity = stack.getMaxDamage();
        int remaining = Math.max(0, Math.min(capacity, capacity - stack.getDamageValue()));
        tooltip.add(Component.translatable("tooltip.madoka_magic.grief_seed.remaining", remaining, capacity)
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    static void kiss(ItemStack stack, List<Component> tooltip) {
        flavor(tooltip, "witch_kiss", WitchKissItem.getThemeId(stack), "");
        tooltip.add(Component.translatable("tooltip.madoka_magic.witch_kiss.obtain").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.madoka_magic.witch_kiss.collection").withStyle(ChatFormatting.DARK_AQUA));
    }

    private static void flavor(List<Component> tooltip, String type, String theme, String suffix) {
        String prefix = "lore.madoka_magic." + type + ".";
        String fallback = Component.translatable(prefix + "default" + suffix).getString();
        tooltip.add(Component.translatableWithFallback(prefix + theme + suffix, fallback)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
