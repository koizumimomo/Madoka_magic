package io.madoka.magic.faction;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.entity.AtonementMinions;
import io.madoka.magic.entity.MadokaWitchEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class FamiliarVisibility {
    private FamiliarVisibility() {}

    public static boolean canWitchTarget(Entity candidate) {
        return candidate instanceof Player player && PlayerMadokaData.of(player).isEverContracted();
    }

    /** Used by vanilla target selection before it chooses the nearest candidate. */
    public static boolean ignoresTarget(Entity source, Entity target) {
        return target instanceof ServerPlayer player && source instanceof Mob mob
                && (!canWitchTarget(player) && Faction.isFamiliar(mob)
                        || AtonementMinions.isMinion(mob) && !WitchFamiliars.isEncounterVisitor(mob, player));
    }

    /** Used by the server tracker, so ordinary viewers never receive the entity. */
    public static boolean blocksTracking(Entity entity, ServerPlayer viewer) {
        return (entity instanceof Mob || entity instanceof Projectile || entity instanceof AreaEffectCloud)
                && blocksFamiliarTracking(responsibleEntity(entity), viewer);
    }

    private static boolean blocksFamiliarTracking(Entity entity, ServerPlayer viewer) {
        if (entity instanceof MadokaWitchEntity witch && witch.isWalpurgisNight()) {
            return false;
        }
        return MadokaConfig.FAMILIARS_HIDE_UNCONTRACTED.get() && !canWitchTarget(viewer) && Faction.isFamiliar(entity)
                || entity instanceof Mob mob && AtonementMinions.isMinion(mob) && !WitchFamiliars.isEncounterVisitor(mob, viewer);
    }

    public static boolean blocksPush(Entity first, Entity second) {
        return ignoresTarget(first, second) || ignoresTarget(second, first);
    }

    public static boolean blocksEffect(Entity victim, Entity source) {
        return victim instanceof ServerPlayer && ignoresTarget(responsibleEntity(source), victim);
    }

    private static Entity responsibleEntity(Entity entity) {
        if (entity instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != null ? owner : entity;
        }
        if (entity instanceof AreaEffectCloud cloud) {
            Entity owner = cloud.getOwner();
            return owner != null ? owner : entity;
        }
        return entity;
    }

    private static boolean hasContractedPlayerNear(ServerLevel level, double x, double y, double z) {
        double radius = MadokaConfig.FAMILIAR_CHECK_RADIUS.get();
        return level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && player.distanceToSqr(x, y, z) <= radius * radius && canWitchTarget(player));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() == MobCategory.MONSTER && event.getLevel() instanceof ServerLevel level
                && MadokaConfig.FAMILIARS_HIDE_UNCONTRACTED.get()) {
            var pos = event.getPos();
            if (!hasContractedPlayerNear(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        MobSpawnType type = event.getSpawnType();
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.PATROL || type == MobSpawnType.SPAWNER
                || type == MobSpawnType.JOCKEY || type == MobSpawnType.REINFORCEMENT || type == MobSpawnType.TRIGGERED)
                && event.getLevel() instanceof ServerLevel level && suppress(level, (Mob) event.getEntity())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnerCheck(MobSpawnEvent.PositionCheck event) {
        // A spawner with custom entity NBT can bypass FinalizeSpawn.
        if (event.getSpawnType() == MobSpawnType.SPAWNER && event.getLevel() instanceof ServerLevel level
                && suppress(level, (Mob) event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean suppress(ServerLevel level, Mob mob) {
        return MadokaConfig.FAMILIARS_HIDE_UNCONTRACTED.get() && Faction.isFamiliar(mob)
                && !hasContractedPlayerNear(level, mob.getX(), mob.getY(), mob.getZ());
    }

    @SubscribeEvent
    public static void onPhantoms(PlayerSpawnPhantomsEvent event) {
        if (MadokaConfig.FAMILIARS_HIDE_UNCONTRACTED.get() && !canWitchTarget(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (ignoresTarget(event.getEntity(), event.getNewTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || mob.tickCount % 10 != 0) {
            return;
        }
        if (ignoresTarget(mob, mob.getTarget())) {
            mob.setTarget(null);
        }
        var memory = mob.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET);
        if (memory != null && ignoresTarget(mob, memory.orElse(null))) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        var source = event.getSource();
        Entity attacker = responsibleEntity(source.getEntity() != null ? source.getEntity() : source.getDirectEntity());
        Entity victim = event.getEntity();
        if (ignoresTarget(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        Entity owner = attacker instanceof OwnableEntity owned ? owned.getOwner() : attacker;
        if (owner instanceof ServerPlayer player && !canWitchTarget(player) && Faction.isFamiliar(victim)) {
            event.setCanceled(true);
        }
    }
}
