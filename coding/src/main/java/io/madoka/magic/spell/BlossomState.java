package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.registry.ModParticles.Devotion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * 献身系 · 花期（核心机制）。
 *
 * <p>状态机：</p>
 * <pre>
 * [休眠] ──施放──> [绽放] ──魔力耗尽──> [花落] ──脱战──> [休眠]
 *                    │                     │
 *                    │ 脱战                │ 属性下降 + 污染上升
 *                    ↓                     ↓
 *              加成直接消失（无惩罚）    +15 污染，属性下降保留到睡醒
 * </pre>
 *
 * <p><b>已确认的规则</b>（技能设计文档第九章）：</p>
 * <ul>
 *   <li>花期持续到<b>战斗结束</b>为止，不再有固定上限；战斗期间加成一直涨。</li>
 *   <li>战斗结束的判定只看<b>是否受到来自生物的伤害</b>（打出伤害不算），
 *       5 秒内没挨打即脱战。</li>
 *   <li>花落后的属性下降<b>不是永久的</b>：睡一觉起床就恢复。</li>
 * </ul>
 */
public final class BlossomState {

    /** 脱战判定：5 秒内没受到来自生物的伤害即视为战斗结束。 */
    public static final int COMBAT_TIMEOUT_TICKS = PlayerMadokaData.COMBAT_TIMEOUT_TICKS;

    /** 绽放阶段每秒消耗的魔力。 */
    public static final double MANA_PER_SECOND = 6.0D;
    /** 绽放阶段每秒增加的伤害加成（比例）。 */
    public static final float BONUS_PER_SECOND = 0.03F;
    /** 伤害加成的上限。 */
    public static final float MAX_BONUS = 0.60F;
    /** 花落阶段每秒增加的污染。 */
    public static final float TAINT_PER_SECOND = 1.2F;
    /** 脱战时若已处于花落，额外增加的污染。 */
    public static final float FALL_EXIT_TAINT = 15.0F;

    /** 属性下降修饰器的 UUID（固定值，重进游戏也要能正确移除）。 */
    private static final UUID WEAKEN_ATTACK_UUID =
            UUID.fromString("b1f2c3d4-0001-4a5b-8c9d-0e1f2a3b4c5d");
    private static final UUID WEAKEN_SPEED_UUID =
            UUID.fromString("b1f2c3d4-0002-4a5b-8c9d-0e1f2a3b4c5d");
    private static final UUID WEAKEN_MANA_UUID =
            UUID.fromString("b1f2c3d4-0003-4a5b-8c9d-0e1f2a3b4c5d");

    private BlossomState() {
    }

    // ---------------------------------------------------------------- 施放

    /** 开启花期。返回是否成功。 */
    public static boolean start(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return false;
        }
        if (data.getBlossomPhase() != PlayerMadokaData.BlossomPhase.NONE) {
            return false;
        }
        data.setBlossomPhase(PlayerMadokaData.BlossomPhase.BLOOM);
        data.setBlossomStartTick(player.level().getGameTime());
        data.save(player);
        return true;
    }

    /** 结束花期（死亡时调用）。 */
    public static void stop(Player player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        data.setBlossomPhase(PlayerMadokaData.BlossomPhase.NONE);
        data.setBlossomStartTick(-1L);
        data.save(player);
    }

    // ---------------------------------------------------------------- 每秒结算

    /** 每秒结算一次花期。由玩家 tick 事件调用。 */
    public static void tick(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return;
        }
        long gameTime = player.level().getGameTime();

        switch (data.getBlossomPhase()) {
            case BLOOM -> tickBloom(player, data, gameTime);
            case FALL -> tickFall(player, data, gameTime);
            default -> {
                // 休眠：确保属性下降修饰器与数据状态一致
                if (!data.isWeakened()) {
                    applyWeaken(player, false);
                }
            }
        }
    }

    /** 绽放阶段：消耗魔力；魔力耗尽转花落；脱战则无惩罚地结束。 */
    private static void tickBloom(ServerPlayer player, PlayerMadokaData data, long gameTime) {
        // 脱战判定：5 秒内没挨打 → 绽放结束（未花落，无惩罚）
        if (!data.isInCombat(gameTime)) {
            data.setBlossomPhase(PlayerMadokaData.BlossomPhase.NONE);
            data.setBlossomStartTick(-1L);
            data.save(player);
            DevotionVisuals.blossom(player, Devotion.CLOSE);
            return;
        }
        // 魔力耗尽 → 立刻进入花落
        if (data.getMana() < MANA_PER_SECOND) {
            enterFall(player, data);
            return;
        }
        data.spendMana(MANA_PER_SECOND);
        data.save(player);
        DevotionVisuals.blossomTick(player, false);
    }

    /** 花落阶段：属性下降 + 污染上升，直到脱战。 */
    private static void tickFall(ServerPlayer player, PlayerMadokaData data, long gameTime) {
        if (!data.isInCombat(gameTime)) {
            // 脱战：已处于花落 → 额外 +15 污染，属性下降保留到睡醒
            data.setBlossomPhase(PlayerMadokaData.BlossomPhase.NONE);
            data.setBlossomStartTick(-1L);
            data.setWeakened(true);
            data.addTaint(FALL_EXIT_TAINT);
            // 战斗结束 → 清掉战斗状态（下一场战斗从零开始计时）
            data.clearCombat();
            data.save(player);
            applyWeaken(player, true);
            return;
        }
        // 花落期间污染持续上升
        float taint = io.madoka.magic.util.MadokaMath.taintRate(player) * TAINT_PER_SECOND;
        if (taint > 0.0F) {
            data.addTaint(taint);
            data.save(player);
        }
        DevotionVisuals.blossomTick(player, true);
    }

    /** 进入花落。 */
    private static void enterFall(ServerPlayer player, PlayerMadokaData data) {
        data.setBlossomPhase(PlayerMadokaData.BlossomPhase.FALL);
        data.setWeakened(true);
        data.save(player);
        applyWeaken(player, true);
        DevotionVisuals.blossom(player, Devotion.FALL);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.4F, 1.8F);
    }

    // ---------------------------------------------------------------- 属性下降

    /**
     * 应用/移除属性下降修饰器。
     * <p>用属性修饰器而不是临时效果实例，这样恢复时能干净地还原。</p>
     */
    public static void applyWeaken(LivingEntity entity, boolean weakened) {
        applyModifier(entity, Attributes.ATTACK_DAMAGE, WEAKEN_ATTACK_UUID,
                "madoka_magic.weaken_attack", -0.15D, weakened);
        applyModifier(entity, Attributes.MOVEMENT_SPEED, WEAKEN_SPEED_UUID,
                "madoka_magic.weaken_speed", -0.10D, weakened);
        applyModifier(entity, io.madoka.magic.registry.MadokaAttributes.MAGIC_MANA.get(),
                WEAKEN_MANA_UUID, "madoka_magic.weaken_mana", -0.20D, weakened);
    }

    private static void applyModifier(LivingEntity entity,
                                      net.minecraft.world.entity.ai.attributes.Attribute attribute,
                                      UUID uuid, String name, double amount, boolean add) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (add) {
            instance.removeModifier(uuid);
            instance.addTransientModifier(new AttributeModifier(uuid, name, amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (instance.getModifier(uuid) != null) {
            // 只在修饰器确实存在时才移除：避免休眠期每秒空移除触发无谓的属性重算
            instance.removeModifier(uuid);
        }
    }

    /** 睡一觉起床 → 恢复属性。 */
    public static void onWakeUp(Player entity) {
        applyWeaken(entity, false);
        PlayerMadokaData data = PlayerMadokaData.of(entity);
        if (data.isWeakened()) {
            data.setWeakened(false);
            data.save(entity);
        }
    }

    /** 死亡/重生 → 清掉所有花期状态。 */
    public static void onDeath(LivingEntity entity) {
        applyWeaken(entity, false);
    }

    // ---------------------------------------------------------------- 加成查询

    /**
     * 当前花期提供的伤害加成倍率（1.0 = 无加成）。
     * <p>绽放阶段随战斗持续时间线性增长；花落阶段立即归零。</p>
     */
    public static float getDamageMultiplier(PlayerMadokaData data, long gameTime) {
        if (data.getBlossomPhase() != PlayerMadokaData.BlossomPhase.BLOOM) {
            return 1.0F;
        }
        long combatTicks = data.getCombatTicks(gameTime);
        float seconds = combatTicks / 20.0F;
        float bonus = Math.min(MAX_BONUS, seconds * BONUS_PER_SECOND);
        return 1.0F + bonus;
    }

    /** 当前花期的状态描述（给 HUD / 提示用）。 */
    public static String describe(PlayerMadokaData data, long gameTime) {
        switch (data.getBlossomPhase()) {
            case BLOOM -> {
                float seconds = data.getCombatTicks(gameTime) / 20.0F;
                return String.format("绽放 %+.0f%%（战斗 %.1f 秒）",
                        (getDamageMultiplier(data, gameTime) - 1.0F) * 100.0F, seconds);
            }
            case FALL -> {
                return "花落";
            }
            default -> {
                return "";
            }
        }
    }
}
