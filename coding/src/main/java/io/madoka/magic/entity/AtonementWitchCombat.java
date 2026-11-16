package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AtonementWitchCombat {
    private enum Attack { RAIN, SUMMON, LINE }

    private static final Attack[] ATTACKS = Attack.values();
    private static final int RAIN_NODES = 3;
    private static final int RAIN_PULSES = 4;
    private static final int RAIN_PULSE_INTERVAL = 10;
    private static final int SPELL_FLASH_TICKS = 12;
    private static final double RAIN_NODE_RADIUS = 5.0D;
    private static final double SUMMON_MARK_RADIUS = 0.95D;
    private static final double BATTLE_HEIGHT = 8.0D;

    private final MadokaWitchEntity witch;
    private WitchCast cast;
    private long specialReadyAt;
    private long shardReadyAt;
    private int nextAttack;
    private long rainReadyAt;
    private long rainChargeEnd;
    private int rainNodes;
    private int remainingNodes;
    private float storedDamage;
    private float rainDamage;
    private int pulsesLeft;
    private long nextPulse;

    AtonementWitchCombat(MadokaWitchEntity witch) {
        this.witch = witch;
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        BlockPos anchor = WitchArenaAttack.encounterAnchor(level, witch);
        Vec3 origin = anchor == null ? witch.position() : Vec3.atBottomCenterOf(anchor);
        double radius = anchor == null ? MadokaConfig.WITCH_RAIN_RADIUS.get()
                : Math.max(MadokaConfig.WITCH_ARENA_RADIUS.get(), MadokaConfig.WITCH_RAIN_RADIUS.get());
        witch.setRainCenter(anchor);
        if (rainChargeEnd != 0 || pulsesLeft != 0) {
            holdPosition();
            tickRain(level, now, origin, radius);
            return;
        }
        witch.setRainVisual(0);
        if (cast != null) {
            tickCast(level, now);
            return;
        }
        if (ThornTracker.isBound(witch)) {
            return;
        }
        double search = Math.max(radius, Math.max(MadokaConfig.ATONEMENT_LINE_REACH.get(), MadokaConfig.ATONEMENT_SHARD_RANGE.get()));
        ServerPlayer target = targets(level, origin, search + 5.0D).stream()
                .min(Comparator.comparingDouble(witch::distanceToSqr)).orElse(null);
        if (target == null) {
            return;
        }
        if (specialReadyAt == 0) {
            specialReadyAt = now + MadokaConfig.ATONEMENT_REST.get();
        }
        if (now >= specialReadyAt && startSpecial(level, target, now, origin, radius)) {
            holdPosition();
            return;
        }
        face(yawTo(target));
        double range = MadokaConfig.ATONEMENT_SHARD_RANGE.get();
        if (witch.distanceToSqr(target) > range * range || !witch.hasLineOfSight(target)) {
            witch.getNavigation().moveTo(target, 1.0D);
        } else {
            witch.getNavigation().stop();
            if (now >= shardReadyAt && FrameShardEntity.fire(level, witch, target)) {
                shardReadyAt = now + MadokaConfig.ATONEMENT_SHARD_INTERVAL.get();
                witch.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.72F, 1.55F);
            }
        }
    }

    private boolean startSpecial(ServerLevel level, ServerPlayer target, long now, Vec3 origin, double radius) {
        for (int i = 0; i < ATTACKS.length; i++) {
            Attack attack = ATTACKS[nextAttack];
            nextAttack = (nextAttack + 1) % ATTACKS.length;
            switch (attack) {
                case RAIN -> {
                    if (now >= rainReadyAt && !targets(level, origin, radius).isEmpty()) {
                        startRain(level, now, origin, radius);
                        return true;
                    }
                }
                case SUMMON -> {
                    List<BlockPos> markers = summonMarks(level, target, origin, radius);
                    if (markers.size() >= 2) {
                        long strike = now + MadokaConfig.ATONEMENT_SUMMON_WARNING.get();
                        setCast(new WitchCast(WitchCast.Kind.ATONEMENT_SUMMON, BlockPos.containing(origin), radius,
                                SUMMON_MARK_RADIUS, now, strike, strike + SPELL_FLASH_TICKS, markers));
                        witch.playSound(SoundEvents.SOUL_ESCAPE, 1.1F, 0.62F);
                        return true;
                    }
                }
                case LINE -> {
                    int yaw = yawTo(target);
                    long strike = now + MadokaConfig.ATONEMENT_LINE_WARNING.get();
                    Vec3 point = witch.position();
                    setCast(new WitchCast(WitchCast.Kind.ATONEMENT_LINE, witch.blockPosition(),
                            MadokaConfig.ATONEMENT_LINE_REACH.get(), MadokaConfig.ATONEMENT_LINE_HALF_WIDTH.get(),
                            now, strike, strike + SPELL_FLASH_TICKS, List.of(), yaw, -1, 0, point));
                    face(yaw);
                    witch.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.55F);
                    return true;
                }
            }
        }
        return false;
    }

    private List<BlockPos> summonMarks(ServerLevel level, ServerPlayer target, Vec3 origin, double radius) {
        if (!"atonement".equals(MadokaDimensions.barrierTheme(level.dimension()))) {
            return List.of();
        }
        int living = level.getEntitiesOfClass(Mob.class, new AABB(origin, origin).inflate(radius + 16.0D),
                mob -> mob.isAlive() && AtonementMinions.summonedBy(mob, witch)).size();
        int count = Math.min(2 + witch.getRandom().nextInt(2), MadokaConfig.ATONEMENT_SUMMON_CAP.get() - living);
        if (count < 2) {
            return List.of();
        }
        List<BlockPos> points = new ArrayList<>(count);
        for (int attempt = 0; attempt < 24 && points.size() < count; attempt++) {
            double angle = witch.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 3.5D + witch.getRandom().nextDouble() * 3.5D;
            BlockPos column = BlockPos.containing(target.getX() + Math.cos(angle) * distance, target.getY(),
                    target.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(column) || Vec3.atBottomCenterOf(column).subtract(origin).horizontalDistanceSqr() > radius * radius) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            var mark = column.atY(y);
            if (Math.abs(y - origin.y) <= 3.0D && witch.distanceToSqr(Vec3.atBottomCenterOf(mark)) >= 9.0D
                    && points.stream().noneMatch(other -> other.distSqr(mark) < 6.25D) && hasFloor(level, mark)) {
                points.add(mark);
            }
        }
        return points;
    }

    private static boolean hasFloor(ServerLevel level, BlockPos mark) {
        Vec3 point = Vec3.atBottomCenterOf(mark);
        AABB bounds = new AABB(point.x - 0.31D, point.y, point.z - 0.31D,
                point.x + 0.31D, point.y + 2.0D, point.z + 0.31D);
        return level.getBlockState(mark.below()).isFaceSturdy(level, mark.below(), Direction.UP)
                && level.noCollision(bounds) && !level.containsAnyLiquid(bounds);
    }

    private void tickCast(ServerLevel level, long now) {
        holdPosition();
        if (cast.kind() == WitchCast.Kind.ATONEMENT_LINE) {
            face(cast.direction());
        }
        if (now >= cast.end()) {
            finish(now);
        } else if (now == cast.strike()) {
            if (cast.kind() == WitchCast.Kind.ATONEMENT_SUMMON) {
                if (!targets(level, cast.origin(), cast.radius() + 4.0D).isEmpty()) {
                    spawnMinions(level);
                }
            } else {
                witch.playSound(SoundEvents.GLASS_BREAK, 1.45F, 0.68F);
                for (ServerPlayer player : targets(level, cast.origin(), cast.radius() + cast.markerRadius() + 1.0D)) {
                    if (cast.hitsAtonementLine(level, witch, player.getBoundingBox())) {
                        player.hurt(witch.damageSources().indirectMagic(witch, witch),
                                MadokaConfig.ATONEMENT_LINE_DAMAGE.get().floatValue());
                    }
                }
            }
        }
    }

    private void spawnMinions(ServerLevel level) {
        boolean skeleton = witch.getRandom().nextBoolean();
        for (BlockPos mark : cast.markers()) {
            if (level.hasChunkAt(mark) && hasFloor(level, mark) && AtonementMinions.spawn(level, witch, mark, skeleton)) {
                Vec3 at = Vec3.atBottomCenterOf(mark);
                level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.5D, at.z, 17, 0.28D, 0.52D, 0.28D, 0.025D);
            }
            skeleton = !skeleton;
        }
        witch.playSound(SoundEvents.SOUL_ESCAPE, 1.15F, 1.1F);
    }

    boolean interrupt(long now) {
        if (cast != null && now < cast.strike()) {
            finish(now);
            return true;
        }
        if (rainChargeEnd <= now) {
            return false;
        }
        rainChargeEnd = 0;
        storedDamage = 0;
        rainNodes = 0;
        remainingNodes = 0;
        rainReadyAt = now + MadokaConfig.WITCH_RAIN_COOLDOWN.get();
        witch.setRainVisual(0);
        finish(now);
        return true;
    }

    private void tickRain(ServerLevel level, long now, Vec3 origin, double radius) {
        if (rainChargeEnd != 0) {
            if (now < rainChargeEnd) {
                witch.setRainVisual((float) radius);
                return;
            }
            double mitigation = rainNodes == 0 ? 1.0D : 0.25D + 0.75D * remainingNodes / rainNodes;
            rainDamage = (float) ((MadokaConfig.WITCH_RAIN_PULSE_DAMAGE.get() + storedDamage / RAIN_PULSES) * mitigation);
            rainChargeEnd = 0;
            pulsesLeft = RAIN_PULSES;
            nextPulse = now;
        }
        witch.setRainVisual((float) -radius);
        if (now < nextPulse) {
            return;
        }
        level.sendParticles(ParticleTypes.FALLING_WATER, origin.x, origin.y + 4.6D, origin.z,
                48, radius * 0.39D, 0.32D, radius * 0.39D, 0.0D);
        for (ServerPlayer player : targets(level, origin, radius)) {
            level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.1D, player.getZ(),
                    7, 0.64D, 0.8D, 0.64D, 0.03D);
            player.hurt(witch.damageSources().indirectMagic(witch, witch), rainDamage);
        }
        nextPulse = now + RAIN_PULSE_INTERVAL;
        if (--pulsesLeft == 0) {
            rainReadyAt = now + MadokaConfig.WITCH_RAIN_COOLDOWN.get();
            finish(now);
        }
    }

    private void startRain(ServerLevel level, long now, Vec3 origin, double radius) {
        rainChargeEnd = now + MadokaConfig.WITCH_RAIN_CHARGE.get();
        storedDamage = 0;
        rainNodes = 0;
        BlockPos center = BlockPos.containing(origin);
        for (int i = 0; i < RAIN_NODES; i++) {
            double angle = i * Math.PI * 2.0D / RAIN_NODES;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * RAIN_NODE_RADIUS);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * RAIN_NODE_RADIUS);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (RainThornEntity.spawn(level, new BlockPos(x, y, z), witch.getUUID(), rainChargeEnd)) {
                rainNodes++;
            }
        }
        remainingNodes = rainNodes;
        witch.setRainVisual((float) radius);
        witch.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1F, 0.52F);
    }

    void recordDamage(long now, float damage) {
        if (rainChargeEnd > now) {
            storedDamage = Math.min(MadokaConfig.WITCH_RAIN_BANK_CAP.get().floatValue(),
                    storedDamage + Math.min(witch.getHealth(), damage));
        }
    }

    boolean breakRainNode(ServerPlayer player, long castEnd) {
        if (!hasRainNode(castEnd) || !witch.combat().allowsVisitor(player) || remainingNodes <= 0) {
            return false;
        }
        remainingNodes--;
        witch.markAssistedBy(player);
        return true;
    }

    boolean hasRainNode(long castEnd) {
        return witch.isAlive() && rainChargeEnd == castEnd && castEnd > witch.level().getGameTime();
    }

    private List<ServerPlayer> targets(ServerLevel level, Vec3 origin, double radius) {
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(radius, BATTLE_HEIGHT, radius),
                player -> player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player)
                        && player.position().subtract(origin).horizontalDistanceSqr() <= radius * radius);
    }

    private int yawTo(ServerPlayer target) {
        double x = target.getX() - witch.getX(), z = target.getZ() - witch.getZ();
        return x * x + z * z < 0.01D ? Math.round(witch.getYRot()) : (int) Math.round(-Mth.atan2(x, z) * Mth.RAD_TO_DEG);
    }

    private void face(int yaw) {
        witch.setYRot(yaw);
        witch.setYHeadRot(yaw);
        witch.yBodyRot = yaw;
    }

    private void holdPosition() {
        witch.getNavigation().stop();
        witch.setDeltaMovement(witch.getDeltaMovement().multiply(0, 1, 0));
    }

    private void setCast(WitchCast next) {
        cast = next;
        witch.setQuintetCast(next);
    }

    private void finish(long now) {
        setCast(null);
        specialReadyAt = now + MadokaConfig.ATONEMENT_REST.get();
        shardReadyAt = now + MadokaConfig.ATONEMENT_SHARD_INTERVAL.get();
    }

    void save(CompoundTag tag) {
        tag.putLong("AtonementReadyAt", cast == null ? specialReadyAt : cast.end() + MadokaConfig.ATONEMENT_REST.get());
        tag.putInt("AtonementNextAttack", nextAttack);
        tag.putLong("RainReadyAt", rainReadyAt);
        tag.putLong("RainChargeEnd", rainChargeEnd);
        tag.putInt("RainNodes", rainNodes);
        tag.putInt("RemainingNodes", remainingNodes);
        tag.putFloat("StoredDamage", storedDamage);
        tag.putFloat("RainDamage", rainDamage);
        tag.putInt("PulsesLeft", pulsesLeft);
        tag.putLong("NextPulse", nextPulse);
    }

    void load(CompoundTag tag) {
        specialReadyAt = tag.getLong("AtonementReadyAt");
        nextAttack = Math.floorMod(tag.getInt("AtonementNextAttack"), ATTACKS.length);
        rainReadyAt = tag.getLong("RainReadyAt");
        rainChargeEnd = tag.getLong("RainChargeEnd");
        rainNodes = tag.getInt("RainNodes");
        remainingNodes = tag.getInt("RemainingNodes");
        storedDamage = tag.getFloat("StoredDamage");
        rainDamage = tag.getFloat("RainDamage");
        pulsesLeft = tag.getInt("PulsesLeft");
        nextPulse = tag.getLong("NextPulse");
    }
}
