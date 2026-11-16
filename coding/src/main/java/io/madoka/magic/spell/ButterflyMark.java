package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.network.ButterflyMarkState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 献身系 · 蝴蝶标记。
 *
 * <p>挂在<b>敌人身上</b>的状态：普攻与主动技能命中时叠层，单目标上限 5 层，
 * 每层独立计时但"全部层数一起衰减"（最后命中的那层决定总时长）。</p>
 *
 * <p>用实体的 persistent data 存，这样怪物死亡、跨区块、存档重载都不丢。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class ButterflyMark {

    /** NBT 键。 */
    public static final String KEY = "madoka_butterfly_mark";

    /** 层数上限。 */
    public static final int MAX_LAYERS = 5;
    /** 每层的持续时间（tick）。 */
    public static final int DURATION_TICKS = 12 * 20;
    /**
     * 每层提供的额外伤害加成。
     * <p>这是层数加成的<b>唯一来源</b>：花粉等吃标记的技能都从这里取值，
     * 避免同一个数值散落在多处、改一处漏一处。</p>
     */
    public static final float DAMAGE_BONUS_PER_LAYER = 0.12F;

    private ButterflyMark() {
    }

    private static CompoundTag tag(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        CompoundTag mark = root.getCompound(KEY);
        root.put(KEY, mark);
        return mark;
    }

    /** 给敌人叠一层标记。 */
    public static void apply(LivingEntity target, UUID caster, long gameTime) {
        CompoundTag mark = tag(target);
        int previousLayers = getLayers(target, gameTime);
        long expiry = gameTime + DURATION_TICKS;
        int layers = Math.min(MAX_LAYERS, previousLayers + 1);
        mark.putInt("layers", layers);
        mark.putLong("expire", expiry);
        mark.putString("caster", caster.toString());
        ButterflyMarkState.broadcast(target, layers, DURATION_TICKS);
    }

    /**
     * 当前有效层数（过期即视为 0）。
     * <p>过期判定放在读取时做，不需要额外的心跳清理。</p>
     */
    public static int getLayers(LivingEntity target, long gameTime) {
        CompoundTag root = target.getPersistentData();
        if (!root.contains(KEY)) {
            return 0;
        }
        CompoundTag mark = root.getCompound(KEY);
        if (gameTime >= mark.getLong("expire")) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_LAYERS, mark.getInt("layers")));
    }

    static int getLayers(LivingEntity target, UUID caster, long gameTime) {
        var mark = target.getPersistentData().getCompound(KEY);
        return caster.toString().equals(mark.getString("caster")) ? getLayers(target, gameTime) : 0;
    }

    /** 标记带来的伤害加成倍率（1.0 = 无加成）。 */
    public static float getDamageMultiplier(LivingEntity target, long gameTime) {
        return 1.0F + getLayers(target, gameTime) * DAMAGE_BONUS_PER_LAYER;
    }

    /** 清空标记（目标死亡或被重置时）。 */
    public static void clear(LivingEntity target) {
        target.getPersistentData().remove(KEY);
        ButterflyMarkState.broadcast(target, 0, 0);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof LivingEntity target) {
            long now = target.level().getGameTime();
            int layers = getLayers(target, now);
            if (layers > 0) {
                long remaining = target.getPersistentData().getCompound(KEY).getLong("expire") - now;
                ButterflyMarkState.sendTo(viewer, target, layers, (int) Math.min(DURATION_TICKS, remaining));
            }
        }
    }
}
