package io.madoka.magic.item;

/** The weapon a transformed player can manifest in an empty main hand. */
public enum ContractWeapon {
    BUTTERFLY("butterfly_blade", "butterfly_slash", 4.0D, -2.2D, 10),
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
}
