package io.madoka.magic.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;

import java.util.UUID;

/**
 * 武器铭刻：让魔法少女/魔女玩家能正常使用伴生武器之外的武器。
 *
 * <p>流程：手持紫水晶碎片右击紫水晶簇方块，得到刻有玩家名字的紫水晶簇；
 * 把武器与该水晶簇放进铁砧，合成出铭刻武器（见 {@code EngravingEvents}）。
 * 变身态玩家用未铭刻的其它武器攻击时只能造成 1 点强制伤害，
 * 伴生武器、铭刻武器与技能伤害不受影响（见 {@code ContractWeaponEvents}）。</p>
 */
public final class WeaponEngraving {
    /** 铭刻标记（物品 NBT）。 */
    public static final String TAG = "madoka_engraved";
    /** 铭刻者 UUID。 */
    public static final String OWNER_TAG = "madoka_engraved_owner";
    /** 铭刻者名字，用于 tooltip。 */
    public static final String OWNER_NAME_TAG = "madoka_engraved_name";

    private WeaponEngraving() {}

    public static boolean isEngraved(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().getBoolean(TAG);
    }

    public static String ownerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(OWNER_NAME_TAG);
    }

    public static void engrave(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TAG, true);
        tag.putUUID(OWNER_TAG, player.getUUID());
        tag.putString(OWNER_NAME_TAG, player.getName().getString());
    }

    /** 刻有玩家名字的紫水晶簇（原版紫水晶簇物品 + NBT）。 */
    public static ItemStack markedCluster(Player player) {
        ItemStack cluster = new ItemStack(Items.AMETHYST_CLUSTER);
        CompoundTag tag = cluster.getOrCreateTag();
        tag.putUUID(OWNER_TAG, player.getUUID());
        tag.putString(OWNER_NAME_TAG, player.getName().getString());
        cluster.setHoverName(Component.translatable("item.madoka_magic.marked_cluster", player.getName()));
        return cluster;
    }

    public static boolean isMarkedCluster(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.AMETHYST_CLUSTER) && stack.getTag() != null
                && stack.getTag().hasUUID(OWNER_TAG);
    }

    public static UUID ownerId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
    }

    /** 是否为可铭刻的武器类物品（剑/斧/镐等工具武器、弓弩、三叉戟）。 */
    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof TieredItem
                || stack.getItem() instanceof ProjectileWeaponItem
                || stack.getItem() instanceof TridentItem);
    }
}
