package io.madoka.magic.entity;

import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;

public final class WitchTypes {
    public static final String WITHER = "wither";
    public static final String NIGHT = "walpurgisnacht";
    public static final String GLUTTONY = "gluttony";
    public static final String SALVATION = "kriemhild_gretchen";
    public static final String NUTCRACKER = "homulilly";
    public static final String MERMAID = "oktavia";
    public static final String OPHELIA = "ophelia";
    public static final String GLUTTONY_BAR_TITLE = "entity.madoka_magic.gluttony_witch.boss_bar";

    private WitchTypes() {}

    public static String bossTheme(Entity entity) {
        String compat = CataclysmCompat.barrierTheme(entity);
        if (!compat.isEmpty()) {
            return compat;
        }
        if (entity instanceof WitherBoss) {
            return WITHER;
        }
        if (entity instanceof GluttonyWitchEntity witch && !witch.isFragment()) {
            return GLUTTONY;
        }
        if (entity instanceof MadokaWitchEntity witch && entity.getType() == ModEntities.WITCH.get()) {
            return witch.getThemeId();
        }
        return "";
    }

    public static ServerPlayer finalAttacker(DamageSource damage) {
        Entity source = damage.getEntity();
        if (source instanceof ServerPlayer player) {
            return player;
        }
        return source instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer owner ? owner : null;
    }
}
