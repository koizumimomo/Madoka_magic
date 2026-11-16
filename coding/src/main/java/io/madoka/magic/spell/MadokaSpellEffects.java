package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.registry.ModParticles.Devotion;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * 内置技能的**效果实现**（代码侧）。
 *
 * <p>元数据（名字 / 图标 / 冷却 / 魔力消耗 / 解锁等级）在
 * {@code data/madoka_magic/spells/*.json}；这里只写行为。</p>
 *
 * <p>要加新技能：写一份 JSON，再在这里注册一个实现即可。
 * 新行为类型用 {@code SpellCasting.registerEffect("madoka_magic:xxx", ...)} 注册，
 * JSON 里用 {@code "type": "madoka_magic:xxx"} 指向它。</p>
 *
 * <p>主动效果返回 {@link SpellCasting#RESULT_SUCCESS}/{@link SpellCasting#RESULT_NO_TARGET}/
 * {@link SpellCasting#RESULT_NOT_READY}：非 0 时施法方不扣资源、不进冷却。</p>
 */
public final class MadokaSpellEffects {

    /** 前缀：JSON 里的 type 默认带这个前缀。 */
    public static final String TYPE_PREFIX = "madoka_magic:";

    /** 花粉：每 0.5 秒一跳的持续伤害，共 8 秒。 */
    public static final int POLLEN_INTERVAL_TICKS = 10;
    public static final int POLLEN_DURATION_TICKS = 160;
    static final double POLLEN_INITIAL_DAMAGE = 2.0D;
    static final float POLLEN_TICK_DAMAGE = 1.0F;
    static final double POLLEN_RANGE = 6.0D;
    static final double TULIP_RADIUS = 4.0D;
    static final double BEE_STING_RANGE = 6.0D;
    static final double PROMISE_RANGE = 6.0D;
    static final double TULIP_DAMAGE = 5.0D;
    static final double BEE_STING_DAMAGE = 2.0D;

    /** 凋零：范围半径与基础伤害。 */
    public static final double WITHER_RADIUS = 4.0D;
    public static final double WITHER_BASE_DAMAGE = 10.0D;
    /** 凋零：消耗当前生命的比例。 */
    public static final float WITHER_HEALTH_COST_RATIO = 0.15F;
    /** 凋零：改为消耗污染时的点数。 */
    public static final float WITHER_TAINT_COST = 10.0F;

    /** 赎罪之刃：锁定罪责最高敌人的搜索半径。 */
    public static final double REDEMPTION_RANGE = 16.0D;
    /** 赎罪之刃：基础伤害。 */
    public static final double REDEMPTION_BASE_DAMAGE = 6.0D;
    /** 赎罪之刃：每点罪责追加的伤害（"倍率随消耗的罪责量提升"）。 */
    public static final double REDEMPTION_DAMAGE_PER_GUILT = 1.0D;
    /** 赎罪之刃：低于这个罪责的目标不构成"处刑"条件。 */
    public static final float REDEMPTION_MIN_GUILT = 1.0F;
    /** 赎罪之刃：反噬比例（消耗罪责的 20%）。 */
    public static final float REDEMPTION_RECOIL_RATIO = 0.2F;

    private MadokaSpellEffects() {
    }

    /** 注册全部内置效果。 */
    public static void bootstrap() {
        WitchPlayerSpells.register();
        SpellCasting.registerEffect(TYPE_PREFIX + "butterfly_slash", MadokaSpellEffects::butterflySlash);
        SpellCasting.registerEffect(TYPE_PREFIX + "tulip_burst", MadokaSpellEffects::tulipBurst);
        SpellCasting.registerEffect(TYPE_PREFIX + "pollen", MadokaSpellEffects::pollen);
        SpellCasting.registerEffect(TYPE_PREFIX + "blossom", MadokaSpellEffects::blossom);
        SpellCasting.registerEffect(TYPE_PREFIX + "wither", MadokaSpellEffects::wither);
        SpellCasting.registerEffect(TYPE_PREFIX + "bee_sting", MadokaSpellEffects::beeSting);
        TakeSpells.registerEffects();
        SpellCasting.registerEffect(TYPE_PREFIX + "thorn_embrace", MadokaSpellEffects::thornEmbrace);
        SpellCasting.registerEffect(TYPE_PREFIX + "redemption_blade", MadokaSpellEffects::redemptionBlade);
        SpellCasting.registerEffect(TYPE_PREFIX + "unpayable_promise", MadokaSpellEffects::unpayablePromise);

        SpellEffects.registerPassive(TYPE_PREFIX + "devotion_wing", MadokaPassives.DEVOTION_WING);
        SpellEffects.registerPassive(TYPE_PREFIX + "obsession", MadokaPassives.OBSESSION);
        SpellEffects.registerPassive(TYPE_PREFIX + "ember", MadokaPassives.EMBER);
        SpellEffects.registerPassive(TYPE_PREFIX + "sin_mark", AtonementPassives.SIN_MARK);
    }

    // ------------------------------------------------------------ 献身系 · 主动技能

    /**
     * 蝶翼斩：向前横扫，命中时回复少量生命。
     * <p>命中后由「献身之翼」被动挂上蝴蝶标记。</p>
     */
    private static int butterflySlash(ServerPlayer caster, SpellDefinition definition, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        double range = SpellCasting.range(definition.parameter("range"));
        double halfAngle = Math.toRadians(Math.min(180.0D, definition.parameter("angle_degrees")) / 2.0D);
        double vertical = SpellCasting.range(definition.parameter("vertical_reach"));
        double yaw = Math.toRadians(caster.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double cosine = Math.cos(halfAngle);
        float damage = (float) MadokaMath.finalDamage(caster, definition.parameter("damage"));
        boolean hit = false;
        AABB bounds = caster.getBoundingBox().inflate(range, vertical, range);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
                e -> !(e instanceof Player) && SpellCasting.canAffect(caster, e))) {
            Vec3 offset = target.position().subtract(caster.position()).multiply(1.0D, 0.0D, 1.0D);
            double distance = offset.length();
            if (distance <= range && forward.dot(offset) >= distance * cosine && caster.hasLineOfSight(target)) {
                hit |= target.hurt(level.damageSources().playerAttack(caster), damage);
            }
        }
        if (hit) {
            caster.heal(1.0F);
        }
        DevotionVisuals.slash(caster, forward, range, halfAngle);
        level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        return SpellCasting.RESULT_SUCCESS;
    }

    /**
     * 郁金香爆：范围伤害 + 范围挂标记。
     */
    private static int tulipBurst(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        AABB box = caster.getBoundingBox().inflate(SpellCasting.range(TULIP_RADIUS));
        boolean hitAny = false;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> Faction.isFamiliar(e) && SpellCasting.canAffect(caster, e))) {
            mob.hurt(level.damageSources().playerAttack(caster), (float) MadokaMath.finalDamage(caster, TULIP_DAMAGE));
            hitAny = true;
        }
        if (!hitAny) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.7F, 1.6F);
        DevotionVisuals.tulips(caster);
        return SpellCasting.RESULT_SUCCESS;
    }

    /**
     * 花粉：对<b>带蝴蝶标记</b>的敌人造成持续伤害，每层标记提高伤害。
     *
     * <p>这是 A 的收割技能：必须先挂标记才能吃到收益。
     * 没有标记的敌人完全不受影响。</p>
     */
    private static int pollen(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        long gameTime = level.getGameTime();
        boolean hitAny = false;
        for (LivingEntity mob : pollenTargets(caster, gameTime)) {
            hitAny = true;
            // 层数加成的唯一来源在 ButterflyMark（每层 +12%）
            float multiplier = ButterflyMark.getDamageMultiplier(mob, gameTime);
            // 立即一跳
            if (mob.hurt(level.damageSources().playerAttack(caster),
                    (float) MadokaMath.finalDamage(caster, POLLEN_INITIAL_DAMAGE * multiplier))) {
                DevotionVisuals.pollen(mob);
            }
            // 后续的连续跳伤害由 PollenTracker 结算
            PollenTracker.add(caster, mob, POLLEN_TICK_DAMAGE * multiplier, gameTime);
        }
        if (!hitAny) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7F, 1.3F);
        return SpellCasting.RESULT_SUCCESS;
    }

    static List<LivingEntity> pollenTargets(ServerPlayer caster, long gameTime) {
        return caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SpellCasting.range(POLLEN_RANGE)),
                mob -> !(mob instanceof Player) && SpellCasting.canAffect(caster, mob)
                        && ButterflyMark.getLayers(mob, gameTime) > 0);
    }

    /**
     * 花期：开启绽放状态。
     * <p>真正的结算在 {@link BlossomState#tick(ServerPlayer)}，这里只负责开启。</p>
     */
    private static int blossom(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        if (!BlossomState.start(caster)) {
            return SpellCasting.RESULT_NOT_READY;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        DevotionVisuals.blossom(caster, Devotion.BLOOM);
        return SpellCasting.RESULT_SUCCESS;
    }

    /**
     * 凋零：以自身生命或污染为代价，换一次爆发伤害。
     *
     * <p>代价二选一：默认消耗生命（当前生命的 15%）；
     * 若按住 Shift 施法则改为消耗污染（+10 点）。
     * 生命不足以支付时自动改为支付污染。</p>
     */
    private static int wither(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        PlayerMadokaData data = PlayerMadokaData.of(caster);

        // 决定代价
        float health = caster.getHealth();
        float cost = witherHealthCost(caster);
        boolean payTaint = SpellCasting.isShiftHeld(caster) || health - cost <= 0.0F;
        if (payTaint) {
            data.addTaint(WITHER_TAINT_COST);
            data.save(caster);
            level.playSound(null, caster.blockPosition(), SoundEvents.WITHER_SPAWN,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        } else {
            caster.setHealth(health - cost);
            level.playSound(null, caster.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.9F, 0.6F);
        }

        // 伤害：已经花落时倍率更高
        boolean fallen = data.getBlossomPhase() == PlayerMadokaData.BlossomPhase.FALL;
        AABB box = caster.getBoundingBox().inflate(SpellCasting.range(WITHER_RADIUS));
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> Faction.isFamiliar(e) && SpellCasting.canAffect(caster, e))) {
            mob.hurt(level.damageSources().playerAttack(caster),
                    (float) MadokaMath.finalDamage(caster, witherDamage(data)));
        }
        DevotionVisuals.wither(caster, payTaint, fallen);
        return SpellCasting.RESULT_SUCCESS;
    }

    static float witherHealthCost(ServerPlayer caster) {
        return caster.getHealth() * WITHER_HEALTH_COST_RATIO;
    }

    static double witherDamage(PlayerMadokaData data) {
        return WITHER_BASE_DAMAGE * (data.getBlossomPhase() == PlayerMadokaData.BlossomPhase.FALL ? 1.5D : 1.0D);
    }

    // ------------------------------------------------------------ 赎罪系 · 主动技能

    /**
     * 荆棘之拥：在周围生成深蓝荆棘，束缚范围内的敌人。
     * <p>定身、每秒持续伤害与反刺由 {@link ThornTracker} 结算。</p>
     */
    private static int thornEmbrace(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        long gameTime = level.getGameTime();
        int count = ThornTracker.apply(caster, gameTime);
        if (count <= 0) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL,
                caster.getX(), caster.getY() + 0.6D, caster.getZ(),
                50, SpellCasting.range(1.6D), SpellCasting.range(0.6D), SpellCasting.range(1.6D), 0.03D);
        return SpellCasting.RESULT_SUCCESS;
    }

    /**
     * 赎罪之刃：锁定<b>罪责最高</b>的敌人，消费它的罪责打出一次爆发。
     *
     * <p>设计要点：</p>
     * <ul>
     *   <li>目标自动锁定"欠她最多的那个"（{@link GuiltMark#findHighest}）。</li>
     *   <li>伤害倍率随消费的罪责量提升。</li>
     *   <li><b>处刑</b>：无视同阵营 50% 减免（跨阵营结算），用 {@link DamageContext} 标记。</li>
     *   <li><b>反噬</b>：玩家自身承受消费量 20% 的真实伤害（"用罪责攻击也要付代价"）。</li>
     * </ul>
     */
    private static int redemptionBlade(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        LivingEntity target = redemptionTarget(caster);
        if (target == null) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        float guilt = GuiltMark.consume(target, caster.getUUID());
        if (guilt <= 0.0F) {
            return SpellCasting.RESULT_NOT_READY;
        }
        float damage = (float) MadokaMath.finalDamage(caster, redemptionDamage(guilt));

        // 处刑：无视同阵营减免（目标现在基本都是眷属，本就跨阵营；这是给未来留的语义）
        DamageContext.runBypassingSameFaction(() ->
                target.hurt(level.damageSources().playerAttack(caster), damage));

        // 银白色刀刃自下而上贯穿
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + 0.2D, target.getZ(),
                8, 0.1D, 0.1D, 0.1D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD,
                target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                24, SpellCasting.range(0.25D), SpellCasting.range(0.6D), SpellCasting.range(0.25D), 0.04D);
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 1.0F, 0.7F);

        // 反噬：真的会掉血
        float recoil = guilt * REDEMPTION_RECOIL_RATIO;
        if (recoil > 0.0F) {
            caster.hurt(level.damageSources().magic(), recoil);
        }
        return SpellCasting.RESULT_SUCCESS;
    }

    static double redemptionDamage(float guilt) {
        return REDEMPTION_BASE_DAMAGE + guilt * REDEMPTION_DAMAGE_PER_GUILT;
    }

    static LivingEntity redemptionTarget(ServerPlayer caster) {
        return GuiltMark.findHighest(caster, SpellCasting.range(REDEMPTION_RANGE), REDEMPTION_MIN_GUILT);
    }

    /**
     * 无法偿还的约定：与一名"欠你罪责"的眷属立约，期间只吃它 30% 伤害，
     * 到期把攒下的伤害一次性还给它。
     *
     * <p>实现见 {@link PromiseState}；这里只负责选中目标与前置校验：
     * 目标必须是<b>眷属</b>（不能对玩家用，规则已在第九章拍板），
     * 且<b>必须已经欠你罪责</b>（否则谈不上"偿还"）。</p>
     */
    private static int unpayablePromise(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        LivingEntity target = SpellCasting.aimedMob(caster, PROMISE_RANGE, e -> true);
        if (target == null || !SpellCasting.canAffect(caster, target)) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        if (GuiltMark.getAmount(target, caster.getUUID()) <= 0.0F) {
            // 它还没打过她：没有罪责，就没有约定
            level.playSound(null, caster.blockPosition(), SoundEvents.VILLAGER_NO,
                    SoundSource.PLAYERS, 0.6F, 1.0F);
            return SpellCasting.RESULT_NOT_READY;
        }
        if (!PromiseState.start(caster, target)) {
            return SpellCasting.RESULT_NOT_READY;
        }
        level.sendParticles(ParticleTypes.END_ROD,
                caster.getX(), caster.getY() + 1.0D, caster.getZ(),
                30, SpellCasting.range(0.7D), SpellCasting.range(0.9D), SpellCasting.range(0.7D), 0.02D);
        return SpellCasting.RESULT_SUCCESS;
    }

    // ------------------------------------------------------------ 其它系的主动技能

    /** 蜂刺：伤害 + 中毒。 */
    private static int beeSting(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        LivingEntity target = SpellCasting.aimedMob(caster, BEE_STING_RANGE,
                entity -> entity instanceof Enemy || Faction.isFamiliar(entity));
        if (target == null) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        target.hurt(level.damageSources().playerAttack(caster),
                (float) MadokaMath.finalDamage(caster, BEE_STING_DAMAGE));
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        level.playSound(null, caster.blockPosition(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS, 0.6F, 1.8F);
        TakeVisuals.sting(caster, target);
        return SpellCasting.RESULT_SUCCESS;
    }

}
