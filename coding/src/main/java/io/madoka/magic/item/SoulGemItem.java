package io.madoka.magic.item;

import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 灵魂宝石 —— 魔法少女身份的载体。
 *
 * <p>单个物品 + NBT 记录宝石性质（{@link ContractItem#THEME_TAG}）。
 * 显示名是「{玩家名}的魔法少女宝石」。</p>
 */
public class SoulGemItem extends Item {

    public SoulGemItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        MagicalGirl theme = MagicalGirlRegistry.get(ContractItem.getThemeId(stack));
        String themeName = theme == null ? "？" : theme.getDisplayName();
        return Component.translatable("item.madoka_magic.soul_gem.named", themeName);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        MagicalGirl theme = MagicalGirlRegistry.get(ContractItem.getThemeId(stack));
        if (theme != null) {
            tooltip.add(Component.translatable("item.madoka_magic.soul_gem.theme", theme.getDisplayName()));
            tooltip.add(Component.translatable("item.madoka_magic.soul_gem.damage_dealt",
                    (int) Math.round((theme.getDamageDealt() - 1.0D) * 100.0D)));
            tooltip.add(Component.translatable("item.madoka_magic.soul_gem.damage_taken",
                    (int) Math.round((theme.getDamageTaken() - 1.0D) * 100.0D)));
            tooltip.add(Component.translatable("item.madoka_magic.soul_gem.taint_rate",
                    (int) Math.round((theme.getTaintRate() - 1.0D) * 100.0D)));
            tooltip.add(Component.translatable("item.madoka_magic.soul_gem.mana_gain",
                    (int) Math.round((theme.getManaGain() - 1.0D) * 100.0D)));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
