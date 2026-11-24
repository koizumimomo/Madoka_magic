package io.madoka.magic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 赎罪系（C · 蓝刺芹系）的被动实现。
 *
 * <p>设计见 {@code docs/魔法少女技能设计.md} §三：</p>
 * <ul>
 *   <li><b>罪之刻印</b>：敌人打她 → 在敌人身上记下罪责（见 {@link GuiltMark}）。
 *       这是 C 的资源来源，也是"无法原谅自己"的机制化。</li>
 *   <li><b>执念</b> / <b>余晖</b>：与献身系共用（{@link MadokaPassives}）。</li>
 * </ul>
 */
public final class AtonementPassives {

    private AtonementPassives() {
    }

    /**
     * 罪之刻印：任何敌人对玩家造成伤害时，给它记一笔罪责。
     *
     * <p>视觉上在施暴者身上浮现银白色刻印——这里用末地烛粒子近似，
     * 数量随它已欠的罪责变多而增加（"纹路随罪责值变亮"）。</p>
     */
    public static final SpellEffects.PassiveEffect SIN_MARK = new SpellEffects.PassiveEffect() {
        @Override
        public void onTakeDamage(ServerPlayer victim, LivingEntity attacker, float amount) {
            if (attacker == null || amount <= 0.0F) {
                return;
            }
            long gameTime = victim.level().getGameTime();
            GuiltMark.record(attacker, victim.getUUID(), amount
                    * (float) (1.0D + io.madoka.magic.data.PassiveTree.bonus(victim, "sin_mark")), gameTime);

            if (victim.level() instanceof ServerLevel level) {
                float guilt = GuiltMark.getAmount(attacker, victim.getUUID());
                float ratio = Math.min(1.0F, guilt / Math.max(1.0F, attacker.getMaxHealth() * GuiltMark.CAP_RATIO));
                int count = 2 + (int) (ratio * 6.0F);
                level.sendParticles(ParticleTypes.END_ROD,
                        attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6D, attacker.getZ(),
                        count, 0.35D, 0.5D, 0.35D, 0.01D);
            }
        }
    };

    /** 「赎偿」每次造成伤害回复魔力的比例。 */
    public static final double MANA_PER_DAMAGE = 0.05D;

    /** 赎偿：造成伤害时，按实际伤害的 5% 缓慢回复魔力（普攻与技能都算）。 */
    public static final SpellEffects.PassiveEffect REPAY = new SpellEffects.PassiveEffect() {
        @Override
        public void onDealDamage(ServerPlayer caster, LivingEntity target, float amount) {
            if (amount <= 0.0F) {
                return;
            }
            io.madoka.magic.data.PlayerMadokaData data = io.madoka.magic.data.PlayerMadokaData.of(caster);
            double room = io.madoka.magic.util.MadokaMath.manaMax(caster) - data.getMana();
            if (room <= 0.0D) {
                return;
            }
            data.restoreMana(Math.min(room, amount * MANA_PER_DAMAGE));
        }
    };
}
