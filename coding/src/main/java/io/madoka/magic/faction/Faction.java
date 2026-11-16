package io.madoka.magic.faction;

import io.madoka.magic.data.PlayerMadokaData;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * 阵营。
 *
 * <p>设计见 {@code docs/魔法少女技能设计.md} §5：</p>
 * <table>
 *   <tr><th>阵营</th><th>成员</th></tr>
 *   <tr><td>{@link #WITCH} 魔女侧</td>
 *       <td>魔女实体、半魔女化玩家（化身）、<b>所有原版敌对生物（眷属）</b>、魔女召唤物</td></tr>
 *   <tr><td>{@link #MAGICAL_GIRL} 魔法少女侧</td>
 *       <td>魔法少女玩家、她们的召唤物与投射物</td></tr>
 *   <tr><td>{@link #NEUTRAL} 中立</td><td>其余一切（动物、村民、未签约玩家……）</td></tr>
 * </table>
 *
 * <p><b>判定是实时推导的，不存盘</b>。依据三样东西：实体类型标签 →
 * {@link EntityType#getCategory()} → 玩家数据。</p>
 *
 * <p>标签优先于分类：未来半魔女化身、魔女召唤物、以及其他 mod 显式声明归属时，
 * 用标签覆盖即可，不必改代码。</p>
 */
public enum Faction {

    /** 魔女侧。 */
    WITCH,
    /** 魔法少女侧。 */
    MAGICAL_GIRL,
    /** 中立：不参与任何阵营修正。 */
    NEUTRAL;

    /** 魔女侧实体类型标签。 */
    public static final TagKey<EntityType<?>> FACTION_WITCH = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("madoka_magic", "faction_witch"));

    /** 魔法少女侧实体类型标签。 */
    public static final TagKey<EntityType<?>> FACTION_MAGICAL_GIRL = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("madoka_magic", "faction_magical_girl"));

    /**
     * 同阵营伤害减免系数。
     * <p>默认 0.5（减半），可在配置里调 —— 魔法少女打自家眷属会吃到这个减免，
     * 手感偏弱时可以调高。</p>
     */
    public static float sameFactionDamageMultiplier() {
        return io.madoka.magic.config.MadokaConfig.SAME_FACTION_DAMAGE_MULTIPLIER.get().floatValue();
    }

    /**
     * 判定一个实体的阵营。
     *
     * @param entity 目标实体（玩家会走 {@link #of(Player)} 的逻辑）
     */
    public static Faction of(Entity entity) {
        if (entity == null) {
            return NEUTRAL;
        }
        if (entity instanceof Player player) {
            return of(player);
        }
        EntityType<?> type = entity.getType();
        // 1. 标签优先
        if (type.builtInRegistryHolder().is(FACTION_WITCH)) {
            return WITCH;
        }
        if (type.builtInRegistryHolder().is(FACTION_MAGICAL_GIRL)) {
            return MAGICAL_GIRL;
        }
        // 2. 兜底：所有敌对生物都是魔女眷属
        if (type.getCategory() == MobCategory.MONSTER
                || entity instanceof Mob mob && !WitchFamiliars.themeOf(mob).isEmpty()) {
            return WITCH;
        }
        return NEUTRAL;
    }

    /** Human forms are neutral. Familiar access is tracked separately by contract history. */
    public static Faction of(Player player) {
        if (player == null) {
            return NEUTRAL;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return NEUTRAL;
        }
        return data.isWitchPath() ? WITCH : MAGICAL_GIRL;
    }

    /**
     * 这个实体是不是「魔女眷属」（所有敌对生物）。
     * <p>眷属的可见性与掉落权限按这个判定。</p>
     */
    public static boolean isFamiliar(Entity entity) {
        return !(entity instanceof Player) && of(entity) == WITCH;
    }

    /**
     * 攻击方与受击方是否同阵营。
     * <p>中立阵营不参与减免（两边都是 NEUTRAL 时也不算同阵营）。</p>
     */
    public static boolean isSameFaction(Entity attacker, Entity victim) {
        Faction a = of(attacker);
        Faction v = of(victim);
        return a != NEUTRAL && a == v;
    }
}
