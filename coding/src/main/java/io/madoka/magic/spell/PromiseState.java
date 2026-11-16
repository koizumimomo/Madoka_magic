package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 赎罪系 · 无法偿还的约定（C-4）。
 *
 * <p>与一名敌人立约 10 秒：</p>
 * <ul>
 *   <li>期间该目标对玩家造成的伤害，玩家<b>只承受 30%</b>，其余进入"约定池"；</li>
 *   <li>约定到期且目标还活着、还在范围内 → 把池中伤害<b>一次性</b>返还给它；</li>
 *   <li>目标死亡 / 逃走（离开 32 格）/ 玩家死亡 → 约定失败，池子清空（白挨了打）。</li>
 * </ul>
 *
 * <p><b>与罪责的关系</b>：罪之刻印给罪责定了用途——"供赎罪之刃和无法偿还的约定消费"
 * （设计 §C-1）。约定本身不扣罪责（C-4 没有写代价），但<b>立约条件</b>是
 * 目标必须欠玩家罪责（它得先打过她，才谈得上"偿还"）。这条是实现时的解读，已写进文档。</p>
 *
 * <p>状态存在玩家数据里（{@code promiseTarget / promiseEndTick / promisePool}），
 * 每次结算时现扫附近实体按 UUID 找目标，不依赖跨存档的实体引用。</p>
 */
public final class PromiseState {

    /** 约定持续时间。 */
    public static final int DURATION_TICKS = 10 * 20;
    /** 玩家实际承受的比例；其余进池。 */
    public static final float KEEP_RATIO = 0.30F;
    /** 超过这个距离视为"逃走"。 */
    public static final double MAX_DISTANCE = 32.0D;
    /** 约定池上限（防止极端情况下一次返还秒杀 Boss；设计没写上限，这里是安全阀）。 */
    public static final float POOL_CAP = 200.0F;

    private PromiseState() {
    }

    // ---------------------------------------------------------------- 立约

    /**
     * 与目标立约（替换旧约定）。调用方负责校验目标是否欠罪责。
     *
     * @return 是否成功
     */
    public static boolean start(ServerPlayer caster, LivingEntity target) {
        if (target == null || !SpellCasting.canAffect(caster, target)) {
            return false;
        }
        PlayerMadokaData data = PlayerMadokaData.of(caster);
        long now = caster.level().getGameTime();
        data.setPromiseTarget(target.getUUID().toString());
        data.setPromiseEndTick(now + DURATION_TICKS);
        data.setPromisePool(0.0F);
        data.save(caster);

        ServerLevel level = caster.serverLevel();
        level.playSound(null, caster.blockPosition(), SoundEvents.SOUL_ESCAPE,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        drawLine(caster, target);
        return true;
    }

    // ---------------------------------------------------------------- 承伤

    /**
     * 结算"约定"的减伤：目标对玩家造成伤害时，只留 30%，其余进池。
     *
     * @return 结算后的伤害量
     */
    public static float reduceIncomingDamage(ServerPlayer player, Entity attacker, float amount) {
        if (amount <= 0.0F || attacker == null) {
            return amount;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isPromised()) {
            return amount;
        }
        if (!(attacker instanceof LivingEntity living)
                || !data.getPromiseTarget().equals(living.getUUID().toString())) {
            return amount;
        }
        long now = player.level().getGameTime();
        if (now >= data.getPromiseEndTick()) {
            return amount;
        }
        float kept = amount * KEEP_RATIO;
        data.setPromisePool(Math.min(POOL_CAP, data.getPromisePool() + (amount - kept)));
        data.save(player);
        return kept;
    }

    // ---------------------------------------------------------------- 每秒结算

    /** 每秒结算一次：到期返还 / 目标消失判定 / 画约定线。 */
    public static void tick(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isPromised()) {
            return;
        }
        long now = player.level().getGameTime();
        LivingEntity target = findTarget(player, data.getPromiseTarget());

        // 目标死亡或逃出范围 → 约定失败，池子清空
        if (target == null || target.distanceTo(player) > SpellCasting.range(MAX_DISTANCE)) {
            fail(player, data);
            return;
        }
        // 到期 → 一次性返还
        if (now >= data.getPromiseEndTick()) {
            settle(player, data, target);
            return;
        }
        drawLine(player, target);
    }

    /** 玩家死亡/下线时静默清空（约定失败，池子清空）。 */
    public static void clear(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (data.isPromised()) {
            data.clearPromise();
            data.save(player);
        }
    }

    /** 剩余时长（tick），给 HUD 用。 */
    public static int remainingTicks(PlayerMadokaData data, long gameTime) {
        if (!data.isPromised()) {
            return 0;
        }
        return (int) Math.max(0L, data.getPromiseEndTick() - gameTime);
    }

    // ---------------------------------------------------------------- 内部

    private static void settle(ServerPlayer player, PlayerMadokaData data, LivingEntity target) {
        float pool = data.getPromisePool();
        data.clearPromise();
        data.save(player);

        ServerLevel level = player.serverLevel();
        if (pool > 0.5F) {
            target.hurt(level.damageSources().playerAttack(player), pool);
            level.sendParticles(ParticleTypes.END_ROD,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    30, 0.5D, 0.8D, 0.5D, 0.05D);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private static void fail(ServerPlayer player, PlayerMadokaData data) {
        data.clearPromise();
        data.save(player);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.5F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.6D, 0.6D, 0.6D, 0.02D);
    }

    /** 按 UUID 在玩家附近找目标（不依赖 cross-save 的实体引用）。 */
    private static LivingEntity findTarget(ServerPlayer player, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        var box = player.getBoundingBox().inflate(SpellCasting.range(MAX_DISTANCE) + 16.0D);
        for (LivingEntity entity : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && uuid.equals(e.getUUID().toString()))) {
            return entity;
        }
        return null;
    }

    /** 玩家与目标之间的银白色约定线。 */
    private static void drawLine(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double distance = from.distanceTo(to);
        // 每格一个点、每点 2 粒：一次 sendParticles 包发多粒，比逐点单粒省一半包
        int steps = Math.min(100, Math.max(2, (int) distance));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 point = from.lerp(to, t);
            level.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 2, 0.0D, 0.05D, 0.005D, 0.0D);
        }
    }
}
