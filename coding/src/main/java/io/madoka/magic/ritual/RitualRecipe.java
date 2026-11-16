package io.madoka.magic.ritual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * 一条祭坛仪式合成配方。
 *
 * @param id     配方 id（json 文件路径）
 * @param inputs 需要的材料（无序集合匹配）
 * @param output 产出物品
 */
public record RitualRecipe(ResourceLocation id, Set<Item> inputs, ItemStack output) {
}
