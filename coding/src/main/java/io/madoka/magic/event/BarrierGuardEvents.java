package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.MadokaWitchEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 结界维度守则：
 * <ul>
 *   <li>魔女免疫击退；</li>
 *   <li>结界中的方块不可被玩家破坏（创造模式除外）；</li>
 *   <li>结界中的爆炸只造成伤害，不破坏方块；</li>
 *   <li>生物不能在结界里搞破坏（凋零/末影人等；魔女自己的生成逻辑不受影响）；</li>
 *   <li>离水的守卫者（远古守卫者）在结界中改为空中游动，
 *       因为人鱼结界不再生成护卫水池。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class BarrierGuardEvents {
    private BarrierGuardEvents() {}

    /** 守卫者的游荡目标点（弱引用，不阻止回收）。 */
    private static final Map<Guardian, Vec3> WANDER = new WeakHashMap<>();

    private static boolean inBarrier(net.minecraft.world.entity.Entity entity) {
        return entity != null && !MadokaDimensions.barrierTheme(entity.level().dimension()).isEmpty();
    }

    /** 魔女免疫击退。 */
    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof MadokaWitchEntity) {
            event.setCanceled(true);
        }
    }

    /** 结界中的方块不可破坏（创造模式除外，方便测试调整）。 */
    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().isCreative()
                && !MadokaDimensions.barrierTheme(event.getPlayer().level().dimension()).isEmpty()) {
            event.setCanceled(true);
        }
    }

    /** 结界中的爆炸只造成伤害，不破坏方块（凋零头颅、苦力怕、TNT 等）。 */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!MadokaDimensions.barrierTheme(event.getLevel().dimension()).isEmpty()) {
            event.getExplosion().getToBlow().clear();
        }
    }

    /** 结界中禁止生物改变地形（凋零冲撞、末影人搬方块、恼鬼/雷苦力怕等）。 */
    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (inBarrier(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * 结界中离水的守卫者改为空中游动：无重力，
     * 追击时朝目标漂移，否则在附近缓慢游荡。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Guardian guardian) || guardian.level().isClientSide
                || guardian.isInWater() || !inBarrier(guardian)) {
            return;
        }
        guardian.setNoGravity(true);
        LivingEntity target = guardian.getTarget();
        Vec3 destination = target != null && target.isAlive()
                ? target.position()
                : wanderTarget(guardian);
        if (destination == null) {
            return;
        }
        Vec3 offset = destination.subtract(guardian.position());
        if (offset.lengthSqr() > 1.0D) {
            guardian.setDeltaMovement(guardian.getDeltaMovement().scale(0.8D)
                    .add(offset.normalize().scale(0.075D)));
        }
    }

    private static Vec3 wanderTarget(Guardian guardian) {
        Vec3 stored = WANDER.get(guardian);
        if (stored == null || guardian.tickCount % 100 == 0
                || stored.distanceToSqr(guardian.position()) < 4.0D) {
            var random = guardian.getRandom();
            stored = guardian.position().add((random.nextDouble() - 0.5D) * 16.0D,
                    (random.nextDouble() - 0.5D) * 8.0D,
                    (random.nextDouble() - 0.5D) * 16.0D);
            WANDER.put(guardian, stored);
        }
        return stored;
    }
}
