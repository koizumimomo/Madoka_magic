package io.madoka.magic.spell;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 施法逻辑（服务端权威）。
 *
 * <p><b>魔力与污染的关系</b>：正常施法消耗魔力，不产生污染；
 * 只有<b>魔力耗尽时继续施法</b>才会叠加污染。</p>
 *
 * <p>技能元数据来自 JSON，行为实现由 {@link MadokaSpellEffects} 注册到本类。</p>
 */
public final class SpellCasting {

    /** 施法结果：成功。 */
    public static final int RESULT_SUCCESS = 0;
    /** 施法结果：没有目标 / 没有可作用对象。 */
    public static final int RESULT_NO_TARGET = 1;
    /** 施法结果：前置条件不满足（暂时用不出这个招式）。 */
    public static final int RESULT_NOT_READY = 2;

    /**
     * 主动技能的效果实现。
     *
     * <p>返回值语义见 {@link #RESULT_SUCCESS} / {@link #RESULT_NO_TARGET} / {@link #RESULT_NOT_READY}；
     * 非 0 时不扣资源、不进冷却，并按结果给玩家 actionbar 反馈。</p>
     */
    @FunctionalInterface
    public interface ActiveEffect {
        int execute(ServerPlayer caster, SpellDefinition definition, DoubleConsumer manaReward);
    }

    /** 技能类型 → 效果实现。 */
    private static final Map<String, ActiveEffect> EFFECTS = new HashMap<>();

    /** 注册主动技能的效果实现（由 {@link MadokaSpellEffects#bootstrap()} 调用）。 */
    public static void registerEffect(String type, ActiveEffect effect) {
        EFFECTS.put(type, effect);
    }

    public static void registerEffect(String type, ToIntFunction<ServerPlayer> effect) {
        registerEffect(type, (caster, definition, manaReward) -> effect.applyAsInt(caster));
    }

    private static final class ManaReward implements DoubleConsumer {
        private double amount;

        @Override
        public void accept(double amount) {
            this.amount += amount;
        }
    }

    /** 玩家 → 技能 → 冷却结束的游戏时间（tick；不用墙钟，卡顿/暂停时不会失真）。 */
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Map<String, Integer>> COOLDOWN_LENGTHS = new HashMap<>();

    /**
     * 玩家当前是否按住了 Shift 施法。
     * <p>凋零用它在"消耗生命"与"消耗污染"之间切换。
     * 由网络包在收到施法请求时设置，结算完立刻清掉。</p>
     */
    private static final Map<UUID, Boolean> SHIFT_HELD = new HashMap<>();

    private SpellCasting() {
    }

    /** 客户端点了轮盘里的某个技能。 */
    public static void castFromClient(ServerPlayer player, String spellId, boolean shiftHeld) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!player.isAlive() || player.isSpectator() || !data.isTransformed() || !data.hasSpell(spellId)) {
            return;
        }
        SpellDefinition def = SpellRegistry.get(spellId);
        if (def == null || !def.active()) {
            return;
        }
        // 必须是<b>当前角色</b>的技能（防止换契约后残留的旧技能）
        var theme = data.getTheme();
        if (theme == null || !theme.getSkills(data.isWitchPath()).contains(spellId)) {
            return;
        }
        SHIFT_HELD.put(player.getUUID(), shiftHeld);
        try {
            cast(player, def);
        } finally {
            SHIFT_HELD.remove(player.getUUID());
        }
    }

    /** 施放时是否按住了 Shift。 */
    public static boolean isShiftHeld(ServerPlayer player) {
        return Boolean.TRUE.equals(SHIFT_HELD.get(player.getUUID()));
    }

    /** 测试假人（盔甲架）也算有效目标，便于在创造模式里调技能数值。 */
    static boolean canAffect(ServerPlayer caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator() && !target.isInvulnerable()
                && (!(target instanceof Player player) || caster.canHarmPlayer(player) && !player.getAbilities().invulnerable);
    }

    static LivingEntity aimedMob(ServerPlayer caster, double baseRange, Predicate<LivingEntity> eligible) {
        return aimedTarget(caster, baseRange, e -> !(e instanceof Player) && eligible.test(e));
    }

    static LivingEntity aimedTarget(ServerPlayer caster, double baseRange, Predicate<LivingEntity> eligible) {
        var level = caster.serverLevel();
        Vec3 eye = caster.getEyePosition();
        Vec3 end = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(range(baseRange))),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
        double nearest = Double.POSITIVE_INFINITY;
        LivingEntity selected = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(0.75D),
                e -> canAffect(caster, e) && eligible.test(e))) {
            var bounds = candidate.getBoundingBox().inflate(Math.max(0.35D, candidate.getPickRadius()));
            var hit = bounds.contains(eye) ? java.util.Optional.of(eye) : bounds.clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest && caster.hasLineOfSight(candidate)) {
                nearest = eye.distanceToSqr(hit.get());
                selected = candidate;
            }
        }
        return selected;
    }

    static double range(double base) {
        return base * MadokaConfig.SPELL_RANGE_MULTIPLIER.get();
    }

    /**
     * 实际施放。返回是否成功。
     *
     * <p>顺序是<b>先执行效果、后扣资源</b>：效果前置失败（没有目标、条件不满足）
     * 时玩家不该白耗魔力/污染，也不该进冷却。</p>
     */
    public static boolean cast(ServerPlayer player, SpellDefinition def) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return false;
        }
        if (ThornTracker.isBound(player)) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.spell.thorn_bound"), true);
            return false;
        }
        if (isOnCooldown(player, def.id())) {
            player.displayClientMessage(
                    Component.translatable("message.madoka_magic.spell.cooldown"), true);
            return false;
        }

        double cost = def.manaCost();
        boolean canPay = data.getMana() >= cost;
        if (!canPay && data.isWitchPath()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.spell.no_mana"), true);
            return false;
        }
        int cooldown = effectiveCooldownTicks(player, def);
        ManaReward manaReward = new ManaReward();
        ActiveEffect effect = EFFECTS.get(def.type());
        int result;
        if (effect == null) {
            SpellRegistry.LOGGER.warn("No active effect registered for spell type '{}' (skill {})", def.type(), def.id());
            result = RESULT_NOT_READY;
        } else {
            result = effect.execute(player, def, manaReward);
        }
        if (result == RESULT_NO_TARGET) {
            player.displayClientMessage(
                    Component.translatable("message.madoka_magic.spell.no_target"), true);
            return false;
        }
        if (result != RESULT_SUCCESS) {
            player.displayClientMessage(
                    Component.translatable("message.madoka_magic.spell.not_ready"), true);
            return false;
        }

        setCooldown(player, def.id(), cooldown);

        // 魔力够 → 消耗魔力；魔力不够 → 技能照放，但叠加污染
        if (canPay) {
            data.spendMana(cost);
        } else {
            // 污染用小数累加（0.3 这类小增量用 int 会被抹成 0）
            float taint = def.taintOnCast() * MadokaMath.taintRate(player);
            data.addTaint(taint);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 0.6F);
        }
        if (manaReward.amount > 0.0D) {
            double room = MadokaMath.manaMax(player) - data.getMana();
            data.restoreMana(Math.min(room, manaReward.amount));
        }
        data.save(player);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 1.6F);
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 30, 0, false, false));

        MadokaNetwork.syncTo(player);
        return true;
    }

    public static float taintCost(ServerPlayer player, SpellDefinition definition) {
        return definition.taintOnCast() * MadokaMath.taintRate(player)
                * (float) Math.max(0, 1.0D - io.madoka.magic.data.PassiveTree.bonus(player, "taint"));
    }

    public static int effectiveCooldownTicks(ServerPlayer player, SpellDefinition definition) {
        int base = definition.cooldownTicks();
        if (base == 0) {
            return 0;
        }
        double reduction = Math.min(Math.max(0, MadokaMath.power(player))
                + io.madoka.magic.data.PassiveTree.bonus(player, "cooldown"), MadokaConfig.SPELL_MAX_COOLDOWN_REDUCTION.get());
        return Math.max(1, (int) Math.round(base * (1.0D - reduction)));
    }

    public static boolean isOnCooldown(ServerPlayer player, String spellId) {
        return remainingCooldownTicks(player, spellId) > 0;
    }

    /** 剩余冷却（tick）。 */
    public static long remainingCooldownTicks(ServerPlayer player, String spellId) {
        Map<String, Long> map = COOLDOWNS.get(player.getUUID());
        if (map == null) {
            return 0L;
        }
        Long until = map.get(spellId);
        if (until == null) {
            return 0L;
        }
        return Math.max(0L, until - player.level().getGameTime());
    }

    private static void setCooldown(ServerPlayer player, String spellId, int ticks) {
        Map<String, Long> map = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        map.put(spellId, player.level().getGameTime() + ticks);
        COOLDOWN_LENGTHS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(spellId, ticks);
    }

    public static net.minecraft.nbt.CompoundTag cooldownSnapshot(ServerPlayer player) {
        var snapshot = new net.minecraft.nbt.CompoundTag();
        var data = PlayerMadokaData.of(player);
        var theme = data.getTheme();
        if (theme == null) return snapshot;
        for (String id : theme.getSkills(data.isWitchPath())) {
            long remaining = remainingCooldownTicks(player, id);
            if (remaining <= 0 || !data.hasSpell(id)) continue;
            var value = new net.minecraft.nbt.CompoundTag();
            value.putLong("remaining", remaining);
            value.putInt("total", COOLDOWN_LENGTHS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(id, (int) remaining));
            snapshot.put(id, value);
        }
        return snapshot;
    }

    /** 玩家死亡时清空冷却。 */
    public static void clearCooldowns(UUID uuid) {
        COOLDOWNS.remove(uuid);
        COOLDOWN_LENGTHS.remove(uuid);
        SHIFT_HELD.remove(uuid);
    }
}
