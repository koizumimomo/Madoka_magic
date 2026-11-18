package io.madoka.magic.compat;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class AlexCavesEncounterEvents {
    private static final Map<Mob, ServerBossEvent> BARS = new HashMap<>();

    private AlexCavesEncounterEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        String theme = AlexCavesCompat.barrierTheme(mob);
        if (theme.isEmpty() || !theme.equals(MadokaDimensions.barrierTheme(level.dimension()))) return;
        BARS.computeIfAbsent(mob, key -> new ServerBossEvent(GriefSeedItem.witchName(theme),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS));
        if (theme.equals("annegret")) {
            mob.goalSelector.removeAllGoals(goal -> goal.getClass().getSimpleName().equals("LicowitchUseCrucibleGoal")
                    || goal.getClass().getSimpleName().equals("RandomlyTeleportGoal") || goal instanceof AnnegretCombat);
            mob.goalSelector.addGoal(0, new AnnegretCombat(mob));
            mob.targetSelector.removeAllGoals(goal -> true);
            mob.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, 10, true, false,
                    target -> target instanceof ServerPlayer player && AnnegretCombat.eligible(mob, player)));
        }
    }

    @SubscribeEvent
    public static void shield(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob witch)
                || !BARS.containsKey(witch) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (witch.goalSelector.getRunningGoals().map(WrappedGoal::getGoal)
                .anyMatch(goal -> goal instanceof AnnegretCombat combat && combat.shielded())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !"annegret".equals(AlexCavesCompat.barrierTheme(mob))
                || !"annegret".equals(MadokaDimensions.barrierTheme(mob.level().dimension()))) return;
        if (event.getNewTarget() != null && (!(event.getNewTarget() instanceof ServerPlayer player) || !AnnegretCombat.eligible(mob, player))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Iterator<Map.Entry<Mob, ServerBossEvent>> iterator = BARS.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Mob mob = entry.getKey();
            ServerBossEvent bar = entry.getValue();
            String theme = AlexCavesCompat.barrierTheme(mob);
            if (!mob.isAlive() || mob.isRemoved() || !theme.equals(MadokaDimensions.barrierTheme(mob.level().dimension()))) {
                bar.removeAllPlayers();
                iterator.remove();
                continue;
            }
            bar.setProgress(Math.max(0, Math.min(1, mob.getHealth() / mob.getMaxHealth())));
            if (mob.tickCount % 10 != 0) continue;
            var players = ((ServerLevel) mob.level()).players();
            for (ServerPlayer viewer : java.util.List.copyOf(bar.getPlayers())) {
                if (!players.contains(viewer) || !AnnegretCombat.eligible(mob, viewer) || mob.distanceToSqr(viewer) > 96 * 96) bar.removePlayer(viewer);
            }
            for (ServerPlayer player : players) {
                if (AnnegretCombat.eligible(mob, player) && mob.distanceToSqr(player) <= 96 * 96) bar.addPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }
}
