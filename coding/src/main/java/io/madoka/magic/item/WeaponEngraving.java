package io.madoka.magic.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * 物品铭刻：让魔法少女/魔女玩家能把任意一件东西变成自己趁手的武器。
 *
 * <p>流程：手持紫水晶碎片右击紫水晶簇方块，得到刻有玩家名字的紫水晶簇；
 * 把要用的物品与该水晶簇放进铁砧，合成出铭刻物品（见 {@code EngravingEvents}）。
 * 不限制物品种类，木棍、石头、别的模组的道具都行。
 * 变身态玩家用未铭刻的物品攻击时只能造成 1 点强制伤害，
 * 伴生武器、铭刻物品与技能伤害不受影响（见 {@code ContractWeaponEvents}）。</p>
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

    /**
     * 可铭刻的物品：不限制种类，任意非空物品都行。
     *
     * <p>变身态的伤害规则是「不是伴生武器、也没铭刻就固定 1 点」，
     * 所以铭刻只是给玩家一个把这件东西变成趁手武器的机会，不必再筛武器类型。
     * 木棍、石头、别的模组的道具，只要愿意花一颗紫水晶簇就能刻。</p>
     */
    public static boolean canEngrave(ItemStack stack) {
        return !stack.isEmpty();
    }
}
