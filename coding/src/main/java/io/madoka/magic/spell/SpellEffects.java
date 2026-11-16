package io.madoka.magic.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 技能**效果实现**的注册表。
 *
 * <p>JSON 里写 {@code "type": "madoka_magic:xxx"}，对应这里注册的一个实现。
 * 行为（伤害、控制、位移……）必须用代码写；元数据（名字、图标、冷却、消耗、解锁等级）在 JSON 里。</p>
 */
public final class SpellEffects {

    /** 主动技能的效果实现。 */
    public interface ActiveEffect {
        /**
         * 执行技能效果。
         *
         * @return 是否真正产生了效果（false = 前置条件不满足，施法方不扣资源也不进冷却）
         */
        boolean execute(ServerPlayer caster);
    }

    /**
     * 被动技能的效果实现。
     *
     * <p>被动分两种：</p>
     * <ul>
     *   <li><b>常驻型</b>：签约时 {@link #apply} 一次，之后一直生效
     *       （例如"受到伤害时额外积累污染"）。</li>
     *   <li><b>事件型</b>：在特定时机由代码回调
     *       （例如"命中敌人时挂蝴蝶标记""击杀时叠一层执念"）。</li>
     * </ul>
     */
    public interface PassiveEffect {
        /** 获得被动时调用一次。 */
        default void apply(ServerPlayer player) {
        }

        /** 失去被动时调用一次。 */
        default void remove(ServerPlayer player) {
        }

        /**
         * 玩家造成伤害时回调（每次伤害结算都会触发）。
         *
         * @param caster 造成伤害的玩家
         * @param target 被击中的目标
         */
        default void onDealDamage(ServerPlayer caster, LivingEntity target) {
        }

        /**
         * 玩家击杀敌人时回调。
         */
        default void onKill(ServerPlayer caster, LivingEntity target) {
        }

        /**
         * 玩家<b>受到</b>生物伤害时回调（伤害已结算后触发）。
         *
         * <p>赎罪系的「罪之刻印」用它记账：谁打了她、打了多少，都记在施暴者身上。</p>
         *
         * @param victim   挨打的玩家
         * @param attacker 造成伤害的生物（敌人）
         * @param amount   实际伤害量
         */
        default void onTakeDamage(ServerPlayer victim, LivingEntity attacker, float amount) {
        }
    }

    private static final java.util.Map<String, ActiveEffect> ACTIVE = new java.util.HashMap<>();
    private static final java.util.Map<String, PassiveEffect> PASSIVE = new java.util.HashMap<>();

    private SpellEffects() {
    }

    public static void registerActive(String type, ActiveEffect effect) {
        ACTIVE.put(type, effect);
    }

    public static void registerPassive(String type, PassiveEffect effect) {
        PASSIVE.put(type, effect);
    }

    public static ActiveEffect active(String type) {
        return ACTIVE.get(type);
    }

    public static PassiveEffect passive(String type) {
        return PASSIVE.get(type);
    }

    public static boolean hasActive(String type) {
        return ACTIVE.containsKey(type);
    }

    public static boolean hasPassive(String type) {
        return PASSIVE.containsKey(type);
    }
}
