package io.madoka.magic.item;

import io.madoka.magic.registry.ModItems;
import net.minecraft.world.item.ItemStack;

/** The weapon a transformed player receives as a real item on transformation. */
public enum ContractWeapon {
    BUTTERFLY("butterfly_blade", "butterfly_slash", 4.0D, -2.2D, 0),
    BEE("queen_stinger", "bee_sting", 3.0D, -1.7D, 0),
    THORN("thorn_greatsword", null, 6.0D, -2.8D, 0);

    public final String id;
    public final String spell;
    public final double defaultDamage;
    public final double defaultSpeed;
    public final int defaultChargeTicks;

    ContractWeapon(String id, String spell, double damage, double speed, int chargeTicks) {
        this.id = id;
        this.spell = spell;
        this.defaultDamage = damage;
        this.defaultSpeed = speed;
        this.defaultChargeTicks = chargeTicks;
    }

    public static ContractWeapon forTheme(String theme) {
        return switch (theme) {
            case "sacrifice" -> BUTTERFLY;
            case "take" -> BEE;
            case "atonement" -> THORN;
            default -> null;
        };
    }

    /** 该物品堆是否为某把契约武器。 */
    public static ContractWeapon of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (ContractWeapon weapon : values()) {
            if (ModItems.CONTRACT_WEAPONS.get(weapon).get() == stack.getItem()) {
                return weapon;
            }
        }
        return null;
    }
}
