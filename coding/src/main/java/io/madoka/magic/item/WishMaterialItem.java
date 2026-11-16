package io.madoka.magic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class WishMaterialItem extends Item {
    private static final String WISH = "madoka_magic_wish";

    public WishMaterialItem() {
        super(new Properties());
    }

    public static void bind(ItemStack stack, Player player) {
        var wish = stack.getOrCreateTagElement(WISH);
        wish.putUUID("player", player.getUUID());
        wish.putString("name", player.getGameProfile().getName());
    }

    public static boolean hasWish(ItemStack stack) {
        var wish = stack.getTagElement(WISH);
        return stack.getItem() instanceof WishMaterialItem && wish != null
                && wish.hasUUID("player") && wish.contains("name", Tag.TAG_STRING);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flags) {
        var wish = stack.getTagElement(WISH);
        tooltip.add((wish == null ? Component.translatable("item.madoka_magic.wish.preview")
                : Component.translatable("item.madoka_magic.wish.owner", wish.getString("name")))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
