package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 花粉的持续伤害。
 *
 * <p>花粉在施放时对目标造成一跳伤害，之后每 0.5 秒再跳一次，共 8 秒。
 * 各维度独立结算。</p>
 *
 * <p>每跳的伤害在施放时算好（按当时的蝴蝶标记层数），
 * 所以即使标记中途过期，持续伤害也不会突然变弱。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class PollenTracker {

    /** 一次正在持续的伤害。 */
    private static class Entry {
        final LivingEntity target;
        final UUID casterId;
        final float damage;
        long nextTick;
        long endTick;

        Entry(LivingEntity target, UUID casterId, float damage, long startTick) {
            this.target = target;
            this.casterId = casterId;
            this.damage = damage;
            this.nextTick = startTick + MadokaSpellEffects.POLLEN_INTERVAL_TICKS;
            this.endTick = startTick + MadokaSpellEffects.POLLEN_DURATION_TICKS;
        }
    }

    private static final Map<ServerLevel, Map<LivingEntity, Entry>> ENTRIES = new HashMap<>();

    private PollenTracker() {
    }

    /**
     * 登记一次持续伤害。
     * <p>同一目标身上的旧花粉先被替换：重复施放是"刷新"而不是叠加多条并行 DoT
     * （与 {@link ThornTracker} 的行为一致）。</p>
     */
    public static void add(ServerPlayer caster, LivingEntity target, float damage, long gameTime) {
        ENTRIES.computeIfAbsent(caster.serverLevel(), ignored -> new HashMap<>())
                .put(target, new Entry(target, caster.getUUID(), damage, gameTime));
    }

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
            if (entries.get(entry.target) != entry) {
                continue;
            }
            if (gameTime >= entry.endTick || !entry.target.isAlive() || entry.target.level() != level) {
                entries.remove(entry.target, entry);
                continue;
            }
            if (gameTime < entry.nextTick) {
                continue;
            }
            entry.nextTick += MadokaSpellEffects.POLLEN_INTERVAL_TICKS;

            // 找到施法者，用他的当前属性结算
            var caster = level.getServer().getPlayerList().getPlayer(entry.casterId);
            if (caster == null || caster.level() != level || !PlayerMadokaData.of(caster).isTransformed()) {
                entries.remove(entry.target, entry);
                continue;
            }
            if (entry.target.hurt(level.damageSources().playerAttack(caster),
                    (float) MadokaMath.finalDamage(caster, entry.damage))) {
                DevotionVisuals.pollen(entry.target);
            }
        }
        if (entries.isEmpty()) {
            ENTRIES.remove(level);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ENTRIES.remove(level);
        }
    }

    /**
     * 清理某个玩家的持续伤害（玩家死亡 / 退出时调用）。
     * <p>施法者没了，持续伤害不该继续结算。</p>
     */
    public static void clear(java.util.UUID casterId) {
        ENTRIES.values().forEach(entries -> entries.values().removeIf(entry -> entry.casterId.equals(casterId)));
    }
}
