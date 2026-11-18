package io.madoka.magic.util;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.BlossomState;
import io.madoka.magic.spell.MadokaPassives;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

/**
 * 魔法少女的数值计算。
 *
 * <p>伤害公式（设计提案 §属性）：</p>
 * <pre>
 * 最终伤害 = (基础伤害 × 魔力值增幅 × 因果值增幅) × (1 + 魔法强度)
 * </pre>
 *
 * <ul>
 *   <li><b>魔力值增幅</b>：由魔力值换算出的等级决定（等级越高增幅越大）。</li>
 *   <li><b>因果值增幅</b>：因果值只占少量权重（最高 20%），成神门槛是 500。</li>
 *   <li><b>魔法强度</b>：直接加在最终结果上。</li>
 * </ul>
 *
 * <p>宝石的「概念性质」再乘一层造成/受到伤害倍率。</p>
 */
public final class MadokaMath {

    /** 纯度百分比上限。 */
    public static final int PURITY_MAX = 100;
    /** 因果值达到这个数时，因果增幅拉满（20%）。 */
    public static final double CAUSALITY_CAP = 500.0D;
    /** 因果增幅最多占 20%。 */
    public static final double CAUSALITY_MAX_BONUS = 0.20D;

    private MadokaMath() {
    }

    public static double attribute(LivingEntity entity, Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    /** 灵魂宝石纯度：击杀积累，决定等级。 */
    public static double purity(LivingEntity entity) {
        return attribute(entity, MadokaAttributes.GEM_PURITY.get());
    }

    public static double progression(LivingEntity entity) {
        if (entity instanceof Player player) {
            var data = PlayerMadokaData.of(player);
            if (data.isWitchPath()) {
                return data.getDespair() >= 0.0D ? data.getDespair() : Math.max(data.getLevel(), purity(player));
            }
        }
        return purity(entity);
    }

    public static void addProgression(Player player, double amount) {
        var data = PlayerMadokaData.of(player);
        if (data.isWitchPath()) {
            data.setDespair(progression(player) + amount);
            return;
        }
        var purity = player.getAttribute(MadokaAttributes.GEM_PURITY.get());
        if (purity != null) {
            purity.setBaseValue(Math.min(PURITY_MAX, purity.getBaseValue() + amount));
        }
    }

    /** 魔力值上限：施法资源的总容量，每位魔法少女不同。 */
    public static double manaMax(LivingEntity entity) {
        return attribute(entity, MadokaAttributes.MAGIC_MANA.get());
    }

    /** 因果值。 */
    public static double causality(LivingEntity entity) {
        return attribute(entity, MadokaAttributes.MAGIC_CAUSALITY.get());
    }

    /** 魔法强度（0.15 = +15%）。 */
    public static double power(LivingEntity entity) {
        return attribute(entity, MadokaAttributes.MAGIC_POWER.get())
                + (entity instanceof Player player ? io.madoka.magic.data.PassiveTree.bonus(player, "power") : 0);
    }

    /** 等级：纯度百分比直接向下取整（纯度本身就是 0–100 的百分比）。 */
    public static int levelOf(LivingEntity entity) {
        return levelOfPurity(progression(entity));
    }

    public static int levelOfPurity(double purity) {
        return Math.max(0, Math.min(PURITY_MAX, (int) Math.floor(purity)));
    }

    public static int growthSteps(Player player, PlayerMadokaData data) {
        return data.isTransformed() && data.getTheme() != null ? levelOf(player) / MadokaConfig.GROWTH_STEP_PERCENT.get() : 0;
    }

    public static MagicalGirl.Growth growthBias(PlayerMadokaData data) {
        var theme = data.getTheme();
        return theme == null || data.isWitchPath() ? MagicalGirl.Growth.NONE : theme.getGrowth();
    }

    public static double manaRegen(Player player, PlayerMadokaData data) {
        return MadokaConfig.MANA_REGEN_PER_SECOND.get()
                * (1.0D + growthSteps(player, data) * growthBias(data).manaRegenPerStep());
    }

    /** 魔力值带来的伤害增幅。 */
    public static double manaBoost(LivingEntity entity) {
        return 1.0D + 0.01D * (levelOf(entity) - 1);
    }

    /** 因果值带来的伤害增幅，最多 +20%。 */
    public static double causalityBoost(LivingEntity entity) {
        double ratio = Math.min(1.0D, causality(entity) / CAUSALITY_CAP);
        return 1.0D + CAUSALITY_MAX_BONUS * ratio;
    }

    /** 魔法强度带来的额外加成，0.15 → ×1.15。 */
    public static double powerBoost(LivingEntity entity) {
        return 1.0D + power(entity);
    }

    /** 最终伤害：基础伤害经过魔力、因果、魔法强度三层。 */
    public static double finalDamage(LivingEntity entity, double baseDamage) {
        return baseDamage * manaBoost(entity) * causalityBoost(entity) * powerBoost(entity);
    }

    /** 造成伤害的最终倍率（含宝石性质）。 */
    public static double damageDealtMultiplier(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerMadokaData data = PlayerMadokaData.of(player);
            MagicalGirl theme = data.getTheme();
            if (theme != null) {
                return theme.getDamageDealt();
            }
        }
        return 1.0D;
    }

    public static double outgoingDamageMultiplier(Player player) {
        var data = PlayerMadokaData.of(player);
        if (data.isWitchPath()) {
            return damageDealtMultiplier(player);
        }
        long now = player.level().getGameTime();
        return damageDealtMultiplier(player) * BlossomState.getDamageMultiplier(data, now)
                * MadokaPassives.obsessionMultiplier(data, now) * MadokaPassives.emberMultiplier(data)
                * (data.isHalfWitch() ? MadokaConfig.HALF_WITCH_DAMAGE_DEALT.get() : 1.0D);
    }

    /** Attacker-side result, before the target's damage modifiers and armor. */
    public static double outgoingDamage(Player player, double amount, double multiplier) {
        var data = PlayerMadokaData.of(player);
        double growth = amount > 0 ? growthSteps(player, data) * MadokaConfig.GROWTH_FINAL_DAMAGE.get() : 0;
        if (data.isHalfWitch()) {
            growth *= MadokaConfig.HALF_WITCH_DAMAGE_DEALT.get();
        }
        return amount * multiplier + growth;
    }

    /** 受到伤害的最终倍率（含宝石性质）。 */
    public static double damageTakenMultiplier(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerMadokaData data = PlayerMadokaData.of(player);
            MagicalGirl theme = data.getTheme();
            if (theme != null) {
                return theme.getDamageTaken() * (data.isHalfWitch() ? MadokaConfig.HALF_WITCH_DAMAGE_TAKEN.get() : 1.0D);
            }
        }
        return 1.0D;
    }

    /** 污染累积速度倍率（含宝石性质）。 */
    public static float taintRate(LivingEntity entity) {
        float rate = 1.0F;
        if (entity instanceof Player player) {
            PlayerMadokaData data = PlayerMadokaData.of(player);
            if (data.isWitchPath() || data.isHalfWitch()) {
                return 0.0F;
            }
            MagicalGirl theme = data.getTheme();
            if (theme != null) {
                rate = theme.taintRateFloat();
            }
        }
        return rate;
    }

    /** 魔力值获取倍率（含宝石性质）。 */
    public static double manaGainMultiplier(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerMadokaData data = PlayerMadokaData.of(player);
            MagicalGirl theme = data.getTheme();
            if (theme != null) {
                return theme.getManaGain();
            }
        }
        return 1.0D;
    }
}
