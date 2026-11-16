package io.madoka.magic.entity;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.registry.ModBlocks;
import io.madoka.magic.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class MourningWitchCombat {
    public static final int FOOTPRINT_WARNING_TICKS = 22;
    private static final int FOOTPRINT_INTERVAL = 5;
    private static final int FOOTPRINT_AGE = 40;
    private static final int MAX_FOOTPRINTS = 5;
    private static final int BLOOM_INTERVAL = 5;
    private static final double BLOOM_RADIUS = 0.95D;
    private static final double MIN_FOOTPRINT_SPACING = 0.9D;
    private static final double ARENA_REACH_MARGIN = 8.0D;
    private static final int ROSE_COUNT = 8;
    private static final int ROSE_SCATTER_RADIUS = 22;
    private static final String BLUE_SKULL_READY = "madoka_blue_skull_ready";
    private static final String ROSES_PLANTED = "madoka_mourning_roses_planted";
    private static final Map<WitherBoss, MourningWitchCombat> COMBAT = new WeakHashMap<>();

    private record Footprint(Vec3 position, long time) {}

    private final Deque<Footprint> trail = new ArrayDeque<>();
    private final Set<UUID> struckPlayers = new HashSet<>();
    private List<Footprint> cast = List.of();
    private UUID trackedPlayer;
    private long castStart;
    private long nextCast;
    private long nextSkulls;
    private long skullsEnd;

    private MourningWitchCombat() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !inMourningBarrier(level)) {
            return;
        }
        if (event.getEntity() instanceof WitherBoss witch) {
            witch.setCustomName(GriefSeedItem.witchName(WitchTypes.WITHER));
            witch.goalSelector.removeAllGoals(goal -> goal.getClass() == RangedAttackGoal.class);
            // Vanilla central-head goal parameters, with the encounter's own firing interval.
            witch.goalSelector.addGoal(2, new RangedAttackGoal(witch, 1.0D,
                    MadokaConfig.MOURNING_SKULL_INTERVAL.get(), 20.0F));
            var state = witch.getPersistentData();
            if (!state.contains(BLUE_SKULL_READY)) {
                state.putLong(BLUE_SKULL_READY, level.getGameTime() + MadokaConfig.MOURNING_BLUE_SKULL_COOLDOWN.get());
            }
        } else if (!event.loadedFromDisk() && event.getEntity() instanceof WitherSkull skull
                && skull.getOwner() instanceof WitherBoss witch) {
            var state = witch.getPersistentData();
            long now = level.getGameTime();
            boolean blue = now >= state.getLong(BLUE_SKULL_READY);
            skull.setDangerous(blue);
            if (blue) {
                state.putLong(BLUE_SKULL_READY, now + MadokaConfig.MOURNING_BLUE_SKULL_COOLDOWN.get());
            }
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof WitherBoss witch && witch.level() instanceof ServerLevel level
                && witch.isAlive() && !witch.isNoAi() && witch.getInvulnerableTicks() == 0 && inMourningBarrier(level)) {
            COMBAT.computeIfAbsent(witch, ignored -> new MourningWitchCombat()).tick(level, witch);
        }
    }

    @SubscribeEvent
    public static void onWitherEffect(MobEffectEvent.Applicable event) {
        var entity = event.getEntity();
        if (event.getEffectInstance().getEffect() == MobEffects.WITHER && entity.getMobType() == MobType.UNDEAD
                && entity.level() instanceof ServerLevel level && inMourningBarrier(level)) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean inMourningBarrier(ServerLevel level) {
        return level.dimension().equals(MadokaDimensions.witchLevel(WitchTypes.WITHER));
    }

    private void tick(ServerLevel level, WitherBoss witch) {
        long now = level.getGameTime();
        var state = witch.getPersistentData();
        var encounter = state.hasUUID(WitchFamiliars.ENCOUNTER_TAG)
                ? MadokaSavedData.get(level).getEncounter(state.getUUID(WitchFamiliars.ENCOUNTER_TAG)) : null;
        if (state.hasUUID(WitchFamiliars.ENCOUNTER_TAG) && (encounter == null || encounter.defeated())) {
            return;
        }
        Vec3 center = encounter == null ? witch.position() : Vec3.atBottomCenterOf(encounter.arena());
        double reach = MadokaConfig.WITCH_ARENA_RADIUS.get() + ARENA_REACH_MARGIN;
        var nearby = level.getPlayers(player -> player.isAlive() && !player.isSpectator()
                && player.position().subtract(center).horizontalDistanceSqr() <= reach * reach
                && WitchFamiliars.isEncounterVisitor(witch, player));
        if (encounter != null && !nearby.isEmpty() && !state.getBoolean(ROSES_PLANTED)) {
            plantRoses(level, witch, encounter.arena());
            state.putBoolean(ROSES_PLANTED, true);
        }
        var visitors = nearby.stream().filter(player -> !player.isCreative()).toList();
        if (visitors.isEmpty()) {
            trail.clear();
            cast = List.of();
            return;
        }

        ServerPlayer target = witch.getTarget() instanceof ServerPlayer player && visitors.contains(player) ? player
                : visitors.stream().min(Comparator.comparingDouble(witch::distanceToSqr)).orElseThrow();
        if (!target.getUUID().equals(trackedPlayer)) {
            trail.clear();
            trackedPlayer = target.getUUID();
        }
        if (now % FOOTPRINT_INTERVAL == 0) {
            recordFootprint(target, now);
        }

        if (nextCast == 0) {
            nextCast = now + MadokaConfig.MOURNING_PATH_COOLDOWN.get();
            nextSkulls = now + MadokaConfig.MOURNING_ORBIT_COOLDOWN.get();
        } else if (!cast.isEmpty()) {
            tickCast(level, witch, visitors, now);
        } else if (now >= skullsEnd) {
            if (now >= nextSkulls && MourningSkullOrbitEntity.launch(level, witch, target)) {
                skullsEnd = now + MourningSkullOrbitEntity.TOTAL_TICKS;
                nextSkulls = now + MadokaConfig.MOURNING_ORBIT_COOLDOWN.get();
            } else if (now >= nextCast && !trail.isEmpty()) {
                startCast(witch, now);
                tickCast(level, witch, visitors, now);
            }
        }
    }

    private void recordFootprint(ServerPlayer target, long now) {
        trail.removeIf(mark -> now - mark.time() > FOOTPRINT_AGE);
        Vec3 point = target.position();
        if (!target.onGround() || !target.level().getFluidState(target.blockPosition()).isEmpty()
                || trail.stream().anyMatch(mark -> mark.position().distanceToSqr(point)
                        < MIN_FOOTPRINT_SPACING * MIN_FOOTPRINT_SPACING)) {
            return;
        }
        trail.addLast(new Footprint(point, now));
        if (trail.size() > MAX_FOOTPRINTS) {
            trail.removeFirst();
        }
    }

    private void startCast(WitherBoss witch, long now) {
        cast = List.copyOf(trail);
        castStart = now;
        struckPlayers.clear();
        nextCast = now + MadokaConfig.MOURNING_PATH_COOLDOWN.get();
        witch.playSound(SoundEvents.SOUL_ESCAPE, 1.25F, 0.63F);
    }

    private void tickCast(ServerLevel level, WitherBoss witch, List<ServerPlayer> visitors, long now) {
        long age = now - castStart;
        for (int i = 0; i < cast.size(); i++) {
            long warning = i * BLOOM_INTERVAL;
            Vec3 point = cast.get(i).position();
            if (age == warning) {
                level.sendParticles(ModParticles.MOURNING_FOOTPRINT.get(), point.x, point.y + 0.035D, point.z,
                        0, BLOOM_RADIUS, 0.0D, 0.0D, 1.0D);
            } else if (age == warning + FOOTPRINT_WARNING_TICKS) {
                bloom(level, witch, visitors, point);
            }
        }
        if (age >= FOOTPRINT_WARNING_TICKS + BLOOM_INTERVAL * (cast.size() - 1)) {
            cast = List.of();
        }
    }

    private void bloom(ServerLevel level, WitherBoss witch, List<ServerPlayer> visitors, Vec3 point) {
        level.sendParticles(ModParticles.MOURNING_ROSE.get(), point.x, point.y + 0.6D, point.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL, point.x, point.y + 0.35D, point.z, 3, 0.27D, 0.12D, 0.27D, 0.015D);
        for (ServerPlayer player : visitors) {
            Vec3 offset = player.position().subtract(point);
            if (Math.abs(offset.y) <= 1.1D && offset.horizontalDistanceSqr() <= BLOOM_RADIUS * BLOOM_RADIUS
                    && !struckPlayers.contains(player.getUUID())
                    && player.hurt(level.damageSources().indirectMagic(witch, witch),
                            MadokaConfig.MOURNING_PATH_DAMAGE.get().floatValue())) {
                struckPlayers.add(player.getUUID());
            }
        }
    }

    private static void plantRoses(ServerLevel level, WitherBoss witch, BlockPos anchor) {
        var floor = ModBlocks.BARRIER_FLOORS.get(WitchTypes.WITHER).block().get();
        var random = witch.getRandom();
        var planted = new ArrayList<BlockPos>(ROSE_COUNT);
        BlockPos arrival = anchor.offset(0, 0, -16);
        for (int i = 0; i < ROSE_COUNT * 10 && planted.size() < ROSE_COUNT; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 7.0D + random.nextDouble() * (ROSE_SCATTER_RADIUS - 7.0D);
            BlockPos column = anchor.offset((int) Math.round(Math.cos(angle) * radius), 0,
                    (int) Math.round(Math.sin(angle) * radius));
            if (column.distSqr(arrival) < 25.0D || !level.hasChunkAt(column)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            BlockPos flower = column.atY(y);
            if (!level.isEmptyBlock(flower) || !level.getBlockState(flower.below()).is(floor)
                    || planted.stream().anyMatch(other -> other.distSqr(flower) < 25.0D)) {
                continue;
            }
            level.setBlock(flower.below(), Blocks.SOUL_SOIL.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(flower, Blocks.WITHER_ROSE.defaultBlockState(), Block.UPDATE_ALL);
            planted.add(flower);
        }
    }
}
