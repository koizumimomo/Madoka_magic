package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.GluttonyWitchEntity;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 赎罪系 · 荆棘之拥（C-2）。
 *
 * <p>在玩家周围生成深蓝荆棘，<b>束缚</b>范围内的敌人 6 秒：</p>
 * <ul>
 *   <li>被束缚者被定身（每 tick 清零水平速度 + 打断寻路 + 高等级缓慢）；</li>
 *   <li>每秒受到一次持续伤害；这份伤害同样记入它的罪责（"反向累积"）；
 *       于是荆棘让赎罪系可以<b>主动</b>攒罪责，而不是只能挨打；</li>
 *   <li>被束缚期间，<b>攻击它的人</b>会受到自身伤害的一部分反噬（真实伤害、有上限）。</li>
 * </ul>
 *
 * <p>反噬结算在 {@code TaintEvents#onLivingDamageThorns}；
 * 本类只负责状态与持续伤害。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class ThornTracker {

    /** 束缚持续时间。 */
    public static final int DURATION_TICKS = 6 * 20;
    /** 持续伤害的间隔。 */
    public static final int TICK_INTERVAL = 20;
    /** 每次持续伤害的基础值（走统一伤害公式）。 */
    public static final float TICK_DAMAGE = 2.0F;
    /** 反噬比例：攻击者承受自身伤害的这个比例。 */
    public static final float REFLECT_RATIO = 0.3F;
    /** 反噬上限（"有数值上限"，避免被高伤一击弹死）。 */
    public static final float REFLECT_CAP = 6.0F;
    /** 施放半径。 */
    public static final double APPLY_RADIUS = 5.0D;
    /** 单次最多束缚几个敌人。 */
    public static final int MAX_TARGETS = 8;

    /** 一次正在持续的束缚。 */
    private static final class Entry {
        final LivingEntity target;
        final UUID casterId;
        long nextTick;
        final long endTick;

        Entry(LivingEntity target, UUID casterId, long startTick) {
            this.target = target;
            this.casterId = casterId;
            this.nextTick = startTick + TICK_INTERVAL;
            this.endTick = startTick + DURATION_TICKS;
        }
    }

    private static final Map<ServerLevel, Map<LivingEntity, Entry>> ENTRIES = new HashMap<>();

    /**
     * 荆棘自己造成的伤害不触发反噬。
     * <p>否则每秒的持续伤害会把自己弹一次，玩家莫名其妙掉血。</p>
     */
    private static boolean internalDamage;
    /** 反刺结算中：防止两个被束缚的敌人互相攻击时无限递归反弹。 */
    private static boolean reflecting;

    private ThornTracker() {
    }

    /**
     * 对玩家周围范围内的敌人施加束缚。
     *
     * @return 实际束缚的数量
     */
    public static int apply(ServerPlayer caster, long gameTime) {
        ServerLevel level = caster.serverLevel();
        AABB box = caster.getBoundingBox().inflate(SpellCasting.range(APPLY_RADIUS));
        int count = 0;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> SpellCasting.canAffect(caster, e) && WitchFamiliars.isEncounterVisitor(e, caster))) {
            if (count >= MAX_TARGETS) {
                break;
            }
            ENTRIES.computeIfAbsent(level, key -> new HashMap<>()).put(mob, new Entry(mob, caster.getUUID(), gameTime));
            boolean interrupted = false;
            if (mob instanceof MadokaWitchEntity witch && witch.combat().interruptWindup(level)) {
                witch.markAssistedBy(caster);
                interrupted = true;
            } else if (mob instanceof GluttonyWitchEntity slime && slime.interruptWindup()) {
                interrupted = true;
            }
            if (interrupted) {
                caster.displayClientMessage(Component.translatable(
                        "message.madoka_magic.spell.thorn_interrupt", mob.getName()), true);
            }
            // 高等级缓慢：定身的主要手段还是每 tick 清零速度，缓慢只是加成
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DURATION_TICKS, 4, false, true));
            level.sendParticles(ParticleTypes.SOUL,
                    mob.getX(), mob.getY() + 0.4D, mob.getZ(),
                    12, 0.4D, 0.5D, 0.4D, 0.01D);
            count++;
        }
        return count;
    }

    /**
     * 每 tick 结算：定身 + 每秒持续伤害。
     * <p>按维度结算，即使该维度里最后一位签约玩家已经离场。</p>
     */
    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        var entries = ENTRIES.get(level);
        if (entries == null) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Entry entry : List.copyOf(entries.values())) {
            LivingEntity target = entry.target;
            if (entries.get(target) != entry) {
                continue;
            }
            if (!target.isAlive() || target.level() != level || gameTime >= entry.endTick) {
                entries.remove(target, entry);
                continue;
            }
            // 定身：水平速度清零 + 打断寻路；竖直方向保留（否则会浮空）
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
            Vec3 motion = target.getDeltaMovement();
            target.setDeltaMovement(0.0D, motion.y, 0.0D);
            target.hurtMarked = true;

            if (gameTime < entry.nextTick) {
                continue;
            }
            entry.nextTick += TICK_INTERVAL;

            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(entry.casterId);
            if (caster == null || caster.level() != level) {
                continue;
            }
            float damage = (float) MadokaMath.finalDamage(caster, TICK_DAMAGE);
            boolean previous = internalDamage;
            internalDamage = true;
            try {
                target.hurt(level.damageSources().playerAttack(caster), damage);
            } finally {
                internalDamage = previous;
            }
            // 荆棘的伤害也记入罪责：C 可以主动攒资源
            GuiltMark.record(target, caster.getUUID(), damage, gameTime);

            level.sendParticles(ParticleTypes.SOUL,
                    target.getX(), target.getY() + 0.6D, target.getZ(),
                    5, 0.3D, 0.3D, 0.3D, 0.01D);
        }
        if (entries.isEmpty()) {
            ENTRIES.remove(level);
        }
    }

    /** 某个实体是否正被荆棘束缚。 */
    public static boolean isBound(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        var entries = ENTRIES.get(level);
        Entry entry = entries == null ? null : entries.get(entity);
        return entry != null && entity.isAlive() && level.getGameTime() < entry.endTick;
    }

    /** 反噬伤害：自身伤害的一部分，有上限。 */
    public static float reflect(float damage) {
        return Math.min(REFLECT_CAP, Math.max(0.0F, damage) * REFLECT_RATIO);
    }

    /** 当前是否处于"荆棘自身持续伤害"的结算中（这时的伤害不反噬）。 */
    public static boolean isInternalDamage() {
        return internalDamage;
    }

    /** 当前是否正在结算反刺伤害（反刺不会再触发反刺）。 */
    public static boolean isReflecting() {
        return reflecting;
    }

    /** 标记反刺结算开始。 */
    public static void beginReflect() {
        reflecting = true;
    }

    /** 标记反刺结算结束。 */
    public static void endReflect() {
        reflecting = false;
    }

    /** 清掉某个施法者的全部束缚（死亡/退出时）。 */
    public static void clear(UUID casterId) {
        ENTRIES.values().forEach(entries -> entries.values().removeIf(entry -> entry.casterId.equals(casterId)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ENTRIES.remove(level);
        }
    }
}
