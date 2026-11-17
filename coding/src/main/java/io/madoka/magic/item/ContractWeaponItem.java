package io.madoka.magic.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 契约武器物品：注册成剑类（{@link SwordItem}）以获得原版剑的挥动/攻击表现。
 *
 * <p>实际伤害与攻速由 {@code ContractWeaponEvents} 按配置以瞬态属性修正叠加，
 * 所以这里的剑 Tier 全部归零（0 基础伤害、0 攻速修正、0 耐久 = 不可损坏、
 * 不可修复、不提供采集等级），避免与配置数值双重叠加。</p>
 */
public class ContractWeaponItem extends SwordItem {
    private static final Tier TIER = new Tier() {
        @Override
        public int getUses() {
            return 0;
        }

        @Override
        public float getSpeed() {
            return 0.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 0.0F;
        }

        @Override
        public int getLevel() {
            return 0;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    };

    public ContractWeaponItem(Properties properties) {
        super(TIER, 0, 0.0F, properties);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }
}
