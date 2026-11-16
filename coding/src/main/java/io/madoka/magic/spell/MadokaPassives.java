package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 献身系（A · 蝴蝶系）的被动实现。
 *
 * <p>三个被动对应技能设计文档 §一：</p>
 * <ul>
 *   <li><b>献身之翼</b>：命中敌人时叠蝴蝶标记 —— 标记是 A 的"燃料"，
 *       花粉与花期都要吃它。</li>
 *   <li><b>执念</b>：击杀敌人后叠层，每层提升伤害 —— 越杀越强。</li>
 *   <li><b>余晖</b>：污染值越高，造成的伤害越高 —— 献身系的"燃烧自己换力量"。</li>
 * </ul>
 */
public final class MadokaPassives {

    /** 执念：每层提供的伤害加成。 */
    public static final float OBSESSION_BONUS_PER_STACK = 0.08F;
    /** 执念：层数上限。 */
    public static final int OBSESSION_MAX_STACKS = 5;
    /** 执念：层数持续时间（tick），超时清空。 */
    public static final int OBSESSION_DURATION_TICKS = 20 * 20;

    /** 余晖：每 10 点污染提供的伤害加成。 */
    public static final float EMBER_BONUS_PER_10_TAINT = 0.02F;

    private MadokaPassives() {
    }

    /**
     * 献身之翼：命中敌人时挂蝴蝶标记。
     * <p>标记不区分伤害来源，普攻与主动技能都会挂。</p>
     */
    public static final SpellEffects.PassiveEffect DEVOTION_WING = new SpellEffects.PassiveEffect() {
        @Override
        public void onDealDamage(ServerPlayer caster, LivingEntity target) {
            ButterflyMark.apply(target, caster.getUUID(), caster.level().getGameTime());
        }
    };

    /**
     * 执念：击杀敌人时叠层，每层 +8% 伤害，20 秒不吃击杀就清空。
     */
    public static final SpellEffects.PassiveEffect OBSESSION = new SpellEffects.PassiveEffect() {
        @Override
        public void onKill(ServerPlayer caster, LivingEntity target) {
            PlayerMadokaData data = PlayerMadokaData.of(caster);
            int stacks = Math.min(OBSESSION_MAX_STACKS, data.getObsessionStacks() + 1);
            data.setObsessionStacks(stacks);
            data.setLastKillTick(caster.level().getGameTime());
            data.save(caster);
        }
    };

    /**
     * 余晖：污染值越高伤害越高。
     * <p>献身系的污染速度是 +20%，配合这个被动形成"越危险越强"的循环。</p>
     */
    public static final SpellEffects.PassiveEffect EMBER = new SpellEffects.PassiveEffect() {
    };

    /** 执念的当前伤害加成倍率（1.0 = 无加成）。 */
    public static float obsessionMultiplier(PlayerMadokaData data, long gameTime) {
        if (data.getObsessionStacks() <= 0) {
            return 1.0F;
        }
        long lastKill = data.getLastKillTick();
        if (lastKill < 0L || gameTime - lastKill > OBSESSION_DURATION_TICKS) {
            return 1.0F;
        }
        return 1.0F + data.getObsessionStacks() * OBSESSION_BONUS_PER_STACK;
    }

    /** 余晖的当前伤害加成倍率（1.0 = 无加成）。 */
    public static float emberMultiplier(PlayerMadokaData data) {
        return 1.0F + (data.getTaint() / 10.0F) * EMBER_BONUS_PER_10_TAINT;
    }

    /** 每秒清理过期的执念层数。 */
    public static void tick(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (data.getObsessionStacks() <= 0) {
            return;
        }
        long lastKill = data.getLastKillTick();
        if (lastKill < 0L || player.level().getGameTime() - lastKill > OBSESSION_DURATION_TICKS) {
            data.setObsessionStacks(0);
            data.save(player);
        }
    }
}
