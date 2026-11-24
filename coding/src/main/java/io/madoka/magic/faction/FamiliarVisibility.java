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
import net.minecraft.world.entity.monster.Enemy;
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
        if (!(candidate instanceof Player player)) {
            return false;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        // 取消契约回到普通人后不再视作"可被魔女针对"：不刷怪、看不见眷属
        return data.isContracted() || data.isWitchPath() || data.isPendingWitchChoice();
    }

    /** Used by vanilla target selection before it chooses the nearest candidate. */
    public static boolean ignoresTarget(Entity source, Entity target) {
        if (!(target instanceof ServerPlayer player) || !(source instanceof Mob mob)) {
            return false;
        }
        // 魔女形态玩家与所有敌对生物同阵营，怪物不再攻击它们；
        // 半魔女化仍是魔法少女阵营，照常被攻击。
        if (source instanceof Enemy && isWitchForm(player)) {
            return true;
        }
        return !canWitchTarget(player) && Faction.isFamiliar(mob)
                || AtonementMinions.isMinion(mob) && !WitchFamiliars.isEncounterVisitor(mob, player);
    }

    /** 处于魔女形态的玩家（半魔女化不算）。 */
    public static boolean isWitchForm(Player player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        return data.isWitchPath() && data.isTransformed();
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
        ServerLevel level = event.getLevel().getLevel();
        if (suppress(level, event.getEntity())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof Mob mob)
                || mob.getPersistentData().hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
            return;
        }
        if (suppress(level, mob)) {
            event.setCanceled(true);
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

    /**
     * 无契约者附近时抑制刷怪：眷属以及一切敌对生物（原版怪、刷怪笼、巡逻队、突袭等）。
     *
     * <p>直接加入世界的新实体也受此规则约束，已存盘实体沿用追踪隐藏规则。</p>
     */
    private static boolean suppress(ServerLevel level, Mob mob) {
        return MadokaConfig.FAMILIARS_HIDE_UNCONTRACTED.get()
                && (Faction.isFamiliar(mob) || mob instanceof Enemy)
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
        // 猪灵/监守者等用 ANGRY_AT 记仇，形态切换后要一并清掉，否则会绕开目标选择继续追打。
        var angry = mob.getBrain().getMemoryInternal(MemoryModuleType.ANGRY_AT);
        if (angry != null && angry.isPresent() && mob.getServer() != null) {
            ServerPlayer angryAt = mob.getServer().getPlayerList().getPlayer(angry.get());
            if (angryAt != null && isWitchForm(angryAt)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            }
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
