package io.madoka.magic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 旧存档的蜂蜜瓶，仍可用于祭坛祈愿。 */
public class HoneyBottleItem extends Item {

    public HoneyBottleItem(Properties properties) {
        super(properties);
    }


    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.madoka_magic.honey_bottle");
    }
}
