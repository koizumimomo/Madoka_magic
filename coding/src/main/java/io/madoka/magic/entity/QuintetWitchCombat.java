package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEffects;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class QuintetWitchCombat {
    private static final int ARROW_WAVE_INTERVAL = 4;
    private static final int ARROW_FALL_HEIGHT = 11;
    private static final int LIGHT_FLASH_TICKS = 12;
    private static final int CROSS_FLASH_TICKS = 16;
    private static final int SALVATION_ATTACKS = 4;
    private static final int MERCY_WARNING_TICKS = 20;
    private static final int MERCY_WAVE_TICKS = 48;
    private static final double MERCY_RANGE_MULTIPLIER = 1.20D;
    private static final double DASH_HALF_WIDTH = 1.25D;
    private static final double DASH_STEP = 0.5D;
    private static final double SHELTER_RADIUS = 2.65D;
    private static final int PROCESSION_ROWS = 3;
    private static final int PROCESSION_REST = 42;
    private static final double PROCESSION_SPEED = 0.66D;
    private static final int[] WHEEL_BEATS = {0b10101, 0b01010, 0b11001};
    private static final int SWEEP_FOLLOW_THROUGH = 24;
    private static final double MERMAID_CHORUS_HEIGHT = 1.05D;
    private static final double MERMAID_CHORUS_MUZZLE = 0.9D;

    private final MadokaWitchEntity witch;
    private final Set<UUID> rowHits = new HashSet<>();
    private WitchCast cast;
    private long readyAt;
    private int salvationTurn;
    private boolean mercyUsed;
    private int processionRow;
    private long curseResumesAt;
    private long nextCurse;
    private int curseTurn;
    private int mermaidBeat;
    private boolean mermaidChorusNext;
    private boolean decoysNext;

    QuintetWitchCombat(MadokaWitchEntity witch) {
        this.witch = witch;
    }

    boolean interruptWindup(long now) {
        if (cast == null || now >= cast.strike() || cast.kind() == WitchCast.Kind.MERCY_WAVE) {
            return false;
        }
        int recovery = switch (cast.kind()) {
            case HEAVEN, REDEMPTION, SALVATION_CROSS -> MadokaConfig.SALVATION_CAST_INTERVAL.get();
            case PROCESSION -> MadokaConfig.NUTCRACKER_PROCESSION_INTERVAL.get();
            case WHEEL_WAVE, MERMAID_SWEEP, MERMAID_NOTES -> MadokaConfig.MERMAID_RECOVERY.get();
            case OPHELIA_DASH -> MadokaConfig.OPHELIA_REST.get();
            case ARROW_RAIN, ATONEMENT_SUMMON, ATONEMENT_LINE, NIGHT_LIGHTNING, MERCY_WAVE,
                    CANDELORO_CAKES, CANDELORO_CUTLERY, CANDELORO_TEA -> 0;
        };
        endCast(now, recovery);
        return true;
    }

    private void endCast(long now, int recovery) {
        updateCast(null);
        rowHits.clear();
        readyAt = now + recovery;
    }

    void tickSalvation(ServerLevel level) {
        long now = level.getGameTime();
        if (cast != null) {
            if (cast.kind() == WitchCast.Kind.MERCY_WAVE) {
                tickMercy(level, now);
                return;
            }
            if (now >= cast.end()) {
                updateCast(null);
            } else {
                switch (cast.kind()) {
                    case ARROW_RAIN -> {
                        if ((now - cast.start()) % ARROW_WAVE_INTERVAL == 0) {
                            rainWave(level);
                        }
                    }
                    case HEAVEN -> tickHeaven(level, now);
                    case REDEMPTION -> {
                        if (now == cast.strike()) {
                            strikeBeams(level);
                        }
                    }
                    case SALVATION_CROSS -> {
                        if (now == cast.strike()) {
                            fireCross(level);
                        }
                    }
                    default -> { }
                }
            }
            return;
        }

        BlockPos center = center(level);
        if (!mercyUsed && witch.getHealth() <= witch.getMaxHealth() * 0.5F
                && !targets(level, center, WitchCast.MERCY_WAVE_RADIUS, WitchCast.BEAM_HEIGHT).isEmpty()) {
            mercyUsed = true;
            rowHits.clear();
            long strike = now + MERCY_WARNING_TICKS;
            updateCast(new WitchCast(WitchCast.Kind.MERCY_WAVE, center, WitchCast.MERCY_WAVE_RADIUS, 0,
                    now, strike, strike + MERCY_WAVE_TICKS, List.of()));
            witch.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.45F, 0.65F);
            return;
        }
        double range = Math.max(MadokaConfig.SALVATION_BEAM_RANGE.get(),
                Math.max(MadokaConfig.SALVATION_ARROW_RADIUS.get(), MadokaConfig.SALVATION_HEAVEN_RADIUS.get()));
        if (now < readyAt || ThornTracker.isBound(witch) || salvationTargets(level, center, range, WitchCast.BEAM_HEIGHT).isEmpty()) {
            return;
        }
        if (readyAt == 0) {
            readyAt = now + MadokaConfig.SALVATION_CAST_INTERVAL.get();
            return;
        }
        double arrowRadius = MadokaConfig.SALVATION_ARROW_RADIUS.get();
        updateCast(switch (salvationTurn) {
            case 0 -> new WitchCast(WitchCast.Kind.ARROW_RAIN, center,
                    arrowRadius * mercyAreaScale(level, center, arrowRadius, ARROW_FALL_HEIGHT),
                    0.0D, now, now, now + MadokaConfig.SALVATION_ARROW_TICKS.get(), List.of());
            case 1 -> heaven(level, center, now);
            case 2 -> redemption(level, center, now);
            default -> startCross(level, center, now);
        });
        salvationTurn = (salvationTurn + 1) % SALVATION_ATTACKS;
        readyAt = cast.end() + MadokaConfig.SALVATION_CAST_INTERVAL.get();
        witch.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F,
                cast.kind() == WitchCast.Kind.ARROW_RAIN ? 1.35F : 0.85F);
        if (cast.kind() == WitchCast.Kind.ARROW_RAIN) {
            rainWave(level);
        }
    }

    private void tickMercy(ServerLevel level, long now) {
        if (now >= cast.strike() && now <= cast.end()) {
            if (now == cast.strike()) {
                level.playSound(null, cast.center(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.7F, 1.35F);
            }
            double previous = now == cast.strike() ? -1.0D : cast.mercyRadiusAt(now - 1);
            double radius = cast.mercyRadiusAt(now);
            double y = cast.origin().y;
            for (var player : targets(level, cast.center(), radius, WitchCast.MERCY_WAVE_HEIGHT + 2.0D)) {
                double distanceSqr = distanceSqr(cast.center(), player.getX(), player.getZ());
                if ((previous >= 0 && distanceSqr <= previous * previous)
                        || player.getY() > y + WitchCast.MERCY_WAVE_HEIGHT || player.getBoundingBox().maxY < y
                        || !rowHits.add(player.getUUID())) {
                    continue;
                }
                int boon = MadokaConfig.SALVATION_MERCY_BOON_TICKS.get();
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, boon, 0), witch);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, boon, 0), witch);
                player.addEffect(new MobEffectInstance(ModEffects.MERCY_MARK.get(),
                        MadokaConfig.SALVATION_MERCY_MARK_TICKS.get(), 0, false, false, true), witch);
            }
        }
        if (now >= cast.end()) {
            endCast(now, MadokaConfig.SALVATION_CAST_INTERVAL.get());
        }
    }

    private List<ServerPlayer> salvationTargets(ServerLevel level, BlockPos center, double radius, double height) {
        return targets(level, center, radius * MERCY_RANGE_MULTIPLIER, height).stream()
                .filter(player -> hasMercyMark(player) || inside(center, player, radius)).toList();
    }

    private double mercyAreaScale(ServerLevel level, BlockPos center, double radius, double height) {
        return targets(level, center, radius * MERCY_RANGE_MULTIPLIER, height).stream()
                .anyMatch(QuintetWitchCombat::hasMercyMark) ? MERCY_RANGE_MULTIPLIER : 1.0D;
    }

    private static boolean hasMercyMark(ServerPlayer player) {
        return player.hasEffect(ModEffects.MERCY_MARK.get());
    }

    private WitchCast startCross(ServerLevel level, BlockPos center, long now) {
        var target = salvationTargets(level, center, MadokaConfig.SALVATION_BEAM_RANGE.get(), WitchCast.BEAM_HEIGHT).stream()
                .min(Comparator.comparingDouble(witch::distanceToSqr));
        BlockPos mark = target.map(player -> findFloor(level, player.blockPosition(), player.getBlockY())).orElse(center);
        double scale = target.filter(QuintetWitchCombat::hasMercyMark).isPresent() ? MERCY_RANGE_MULTIPLIER : 1.0D;
        int yaw = yawTo(Vec3.atBottomCenterOf(center), Vec3.atBottomCenterOf(mark), witch.getYRot());
        long strike = now + MadokaConfig.SALVATION_CROSS_WARNING.get();
        return new WitchCast(WitchCast.Kind.SALVATION_CROSS, mark, MadokaConfig.SALVATION_CROSS_REACH.get() * scale,
                MadokaConfig.SALVATION_CROSS_HALF_WIDTH.get() * scale, now, strike, strike + CROSS_FLASH_TICKS, List.of(), yaw, -1);
    }

    private void fireCross(ServerLevel level) {
        level.playSound(null, cast.center(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.5F, 1.2F);
        double reach = cast.radius() + cast.markerRadius() + 1.0D;
        Vec3 origin = cast.origin().add(0, WitchCast.CROSS_BEAM_Y, 0);
        for (var player : targets(level, cast.center(), reach, 5)) {
            if (cast.hitsCross(level, witch, player.getBoundingBox()) && clearBeamPath(level, origin, player)) {
                player.hurt(witch.damageSources().indirectMagic(witch, witch),
                        MadokaConfig.SALVATION_CROSS_DAMAGE.get().floatValue());
            }
        }
    }

    private boolean clearBeamPath(ServerLevel level, Vec3 origin, ServerPlayer player) {
        Vec3 hit = player.position().add(0, WitchCast.CROSS_BEAM_Y, 0);
        return level.clip(new ClipContext(origin, hit, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, witch))
                .getType() == HitResult.Type.MISS;
    }

    void tickOphelia(ServerLevel level) {
        long now = level.getGameTime();
        if (cast != null) {
            followDash(level, now);
            return;
        }
        if (now < readyAt || ThornTracker.isBound(witch) || !witch.onGround()) {
            return;
        }
        double range = MadokaConfig.OPHELIA_DASH_RANGE.get();
        var target = targets(level, witch.blockPosition(), range, 3).stream()
                .filter(player -> witch.distanceToSqr(player) > 9.0D && witch.hasLineOfSight(player))
                .min(Comparator.comparingDouble(witch::distanceToSqr)).orElse(null);
        int rest = MadokaConfig.OPHELIA_REST.get();
        if (target == null || readyAt == 0) {
            readyAt = now + (target == null ? 10 : rest / 2);
            return;
        }

        int yaw = yawTo(witch.position(), target.position(), witch.getYRot());
        Vec3 forward = Vec3.directionFromRotation(0, yaw);
        double length = clearDashLength(level, forward, range);
        if (length < 3.0D) {
            readyAt = now + rest / 2;
            return;
        }
        int decoys = 0;
        if (decoysNext) {
            Vec3 side = Vec3.directionFromRotation(0, yaw + 90.0F).scale(WitchCast.OPHELIA_DECOY_OFFSET);
            if (clearDashLength(level, forward, length, side) >= length) decoys |= 1;
            if (clearDashLength(level, forward, length, side.reverse()) >= length) decoys |= 2;
        }
        long strike = now + MadokaConfig.OPHELIA_DASH_WARNING.get();
        long end = strike + (long) Math.ceil(length / MadokaConfig.OPHELIA_DASH_SPEED.get());
        updateCast(new WitchCast(WitchCast.Kind.OPHELIA_DASH, witch.blockPosition(), length, DASH_HALF_WIDTH,
                now, strike, end, List.of(), yaw, -1, decoys, witch.position()));
        readyAt = end + rest;
        decoysNext = !decoysNext;
        rowHits.clear();
        lockCastFacing();
        witch.playSound(SoundEvents.HORSE_ANGRY, 1.2F, decoys == 0 ? 0.85F : 0.65F);
    }

    private double clearDashLength(ServerLevel level, Vec3 forward, double range) {
        return clearDashLength(level, forward, range, Vec3.ZERO);
    }

    private double clearDashLength(ServerLevel level, Vec3 forward, double range, Vec3 offset) {
        AABB box = witch.getBoundingBox().move(offset);
        for (double distance = 0.0D; ; distance = Math.min(range, distance + DASH_STEP)) {
            AABB next = box.move(forward.scale(distance));
            if (!level.hasChunkAt(BlockPos.containing(next.getCenter())) || !level.getWorldBorder().isWithinBounds(next)
                    || level.getBlockCollisions(witch, next.deflate(0.01D)).iterator().hasNext()
                    || !level.getBlockCollisions(witch, floorProbe(next)).iterator().hasNext()) {
                return Math.max(0.0D, distance - DASH_STEP);
            }
            if (distance >= range) {
                return range;
            }
        }
    }

    private static AABB floorProbe(AABB box) {
        return new AABB(box.minX, box.minY - 0.25D, box.minZ, box.maxX, box.minY, box.maxZ).deflate(0.01D);
    }

    private void followDash(ServerLevel level, long now) {
        lockCastFacing();
        if (now < cast.strike()) {
            return;
        }
        if (now == cast.strike()) {
            witch.playSound(SoundEvents.HORSE_GALLOP, 1.45F, 0.8F);
        }
        Vec3 from = witch.position();
        Vec3 expected = cast.origin().add(cast.castPoint(0, 0, cast.dashDistance(now)));
        Vec3 step = expected.subtract(from).multiply(1, 0, 1);
        if (Math.abs(from.y - expected.y) > 0.25D || step.horizontalDistanceSqr() > 4.0D
                || !level.getBlockCollisions(witch, floorProbe(witch.getBoundingBox().move(step))).iterator().hasNext()) {
            updateCast(null);
            return;
        }
        witch.move(MoverType.SELF, step.add(0, -0.06D, 0));
        Vec3 to = witch.position();
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(from, to).inflate(cast.markerRadius() + 1, 3, cast.markerRadius() + 1), this::allows)) {
            AABB hit = player.getBoundingBox().inflate(cast.markerRadius(), 0.4D, cast.markerRadius());
            Vec3 mid = from.add(0, 0.85D, 0);
            Vec3 tip = to.add(0, 0.85D, 0);
            if ((hit.contains(mid) || hit.contains(tip) || hit.clip(mid, tip).isPresent())
                    && witch.hasLineOfSight(player) && rowHits.add(player.getUUID())
                    && player.hurt(witch.damageSources().mobAttack(witch), MadokaConfig.OPHELIA_DASH_DAMAGE.get().floatValue())) {
                Vec3 forward = cast.castPoint(0, 0, 1);
                player.knockback(0.72D, -forward.x, -forward.z);
            }
        }
        if (now >= cast.end() || to.subtract(expected).horizontalDistanceSqr() > 0.05D) {
            updateCast(null);
        }
    }

    private void lockCastFacing() {
        witch.getNavigation().stop();
        witch.setYRot(cast.direction());
        witch.setYHeadRot(cast.direction());
        witch.yBodyRot = cast.direction();
        witch.setDeltaMovement(witch.getDeltaMovement().multiply(0, 1, 0));
    }

    private static int yawTo(Vec3 from, Vec3 toward, float fallback) {
        Vec3 offset = toward.subtract(from);
        return offset.horizontalDistanceSqr() < 0.01D ? Math.round(fallback)
                : (int) Math.round(-Mth.atan2(offset.x, offset.z) * Mth.RAD_TO_DEG);
    }

    private void rainWave(ServerLevel level) {
        var center = cast.center();
        for (int i = 0; i < MadokaConfig.SALVATION_ARROWS_PER_WAVE.get(); i++) {
            BlockPos column = randomColumn(center, cast.radius());
            spawnArrow(level, column, column.getX() + 0.5D, column.getZ() + 0.5D);
        }
        var players = targets(level, center, cast.radius(), ARROW_FALL_HEIGHT);
        if (!players.isEmpty()) {
            var player = players.get(witch.getRandom().nextInt(players.size()));
            double x = player.getX() + (witch.getRandom().nextDouble() - 0.5D) * 3.5D;
            double z = player.getZ() + (witch.getRandom().nextDouble() - 0.5D) * 3.5D;
            if (distanceSqr(center, x, z) <= cast.radius() * cast.radius()) {
                spawnArrow(level, BlockPos.containing(x, center.getY(), z), x, z);
            }
        }
    }

    private void spawnArrow(ServerLevel level, BlockPos column, double x, double z) {
        BlockPos ground = findFloor(level, column, column.getY());
        if (ground == null || distanceSqr(cast.center(), x, z) > cast.radius() * cast.radius()) {
            return;
        }
        int y = Math.min(ground.getY() + ARROW_FALL_HEIGHT, level.getMaxBuildHeight() - 1);
        if (y > ground.getY()) {
            LightArrowEntity.fall(level, witch, new Vec3(x, y, z), MadokaConfig.SALVATION_ARROW_DAMAGE.get());
        }
    }

    private WitchCast heaven(ServerLevel level, BlockPos center, long now) {
        double radius = MadokaConfig.SALVATION_HEAVEN_RADIUS.get();
        radius *= mercyAreaScale(level, center, radius, WitchCast.BEAM_HEIGHT);
        long strike = now + MadokaConfig.SALVATION_HEAVEN_WARNING.get();
        double offset = radius * 0.63D;
        var shelters = List.of(center.offset(0, 0, (int) offset), center.offset((int) offset, 0, 0),
                center.offset(0, 0, -(int) offset), center.offset(-(int) offset, 0, 0)).stream()
                .map(pos -> {
                    var floor = findFloor(level, pos, center.getY());
                    return floor == null ? pos : floor;
                }).toList();
        return new WitchCast(WitchCast.Kind.HEAVEN, center, radius, SHELTER_RADIUS, now, strike,
                strike + LIGHT_FLASH_TICKS, shelters);
    }

    private void tickHeaven(ServerLevel level, long now) {
        if (now > cast.strike() || now < cast.strike() && (now - cast.start()) % 4 != 0) {
            return;
        }
        if (now == cast.strike()) {
            witch.playSound(SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.85F);
        }
        for (var player : targets(level, cast.center(), cast.radius(), WitchCast.BEAM_HEIGHT)) {
            if (cast.markers().stream().anyMatch(pos -> inside(pos, player, cast.markerRadius())
                    && Math.abs(player.getY() - pos.getY()) <= 3.0D)) {
                continue;
            }
            if (now == cast.strike()) {
                player.hurt(witch.damageSources().indirectMagic(witch, witch),
                        MadokaConfig.SALVATION_HEAVEN_DAMAGE.get().floatValue());
            } else {
                Vec3 pull = Vec3.atBottomCenterOf(cast.center()).subtract(player.position()).multiply(1, 0, 1);
                if (pull.horizontalDistanceSqr() > 1.0D) {
                    pull = pull.normalize().scale(0.11D);
                    player.push(pull.x, 0, pull.z);
                    player.hurtMarked = true;
                }
            }
        }
    }

    private WitchCast redemption(ServerLevel level, BlockPos center, long now) {
        double baseRange = MadokaConfig.SALVATION_BEAM_RANGE.get();
        double scale = mercyAreaScale(level, center, baseRange, WitchCast.BEAM_HEIGHT);
        double range = baseRange * scale;
        Set<BlockPos> marks = new LinkedHashSet<>();
        for (var player : salvationTargets(level, center, baseRange, WitchCast.BEAM_HEIGHT)) {
            BlockPos feet = player.blockPosition();
            BlockPos floor = findFloor(level, feet, feet.getY());
            marks.add(floor == null ? feet : floor);
        }
        int desired = marks.size() + MadokaConfig.SALVATION_BEAM_RANDOM_MARKS.get();
        for (int i = 0; i < MadokaConfig.SALVATION_BEAM_RANDOM_MARKS.get() * 4 && marks.size() < desired; i++) {
            BlockPos column = randomColumn(center, range);
            BlockPos floor = findFloor(level, column, center.getY());
            if (floor != null && distanceSqr(center, floor.getX() + 0.5D, floor.getZ() + 0.5D) <= range * range) {
                marks.add(floor);
            }
        }
        long strike = now + MadokaConfig.SALVATION_BEAM_WARNING.get();
        return new WitchCast(WitchCast.Kind.REDEMPTION, center, range, MadokaConfig.SALVATION_BEAM_RADIUS.get() * scale,
                now, strike, strike + LIGHT_FLASH_TICKS, List.copyOf(marks));
    }

    private void strikeBeams(ServerLevel level) {
        witch.playSound(SoundEvents.BEACON_ACTIVATE, 1.5F, 1.1F);
        for (var player : targets(level, cast.center(), cast.radius() + cast.markerRadius(), 2 * WitchCast.BEAM_HEIGHT)) {
            if (cast.markers().stream().anyMatch(pos -> inside(pos, player, cast.markerRadius())
                    && player.getBoundingBox().maxY >= pos.getY() && player.getY() <= pos.getY() + WitchCast.BEAM_HEIGHT)) {
                player.hurt(witch.damageSources().indirectMagic(witch, witch),
                        MadokaConfig.SALVATION_BEAM_DAMAGE.get().floatValue());
            }
        }
    }

    void tickNutcracker(ServerLevel level) {
        long now = level.getGameTime();
        tickCurse(level, now);
        if (cast != null) {
            if (now > cast.end()) {
                if (++processionRow >= PROCESSION_ROWS || ThornTracker.isBound(witch)) {
                    endCast(now, MadokaConfig.NUTCRACKER_PROCESSION_INTERVAL.get());
                    curseResumesAt = now + PROCESSION_REST;
                    witch.setCurseRadius(0);
                } else {
                    int side = witch.getRandom().nextBoolean() ? 1 : -1;
                    int gap = cast.gap() + side;
                    startRow(cast.center(), cast.radius(), cast.direction(), gap < 0 || gap >= WitchCast.LANES
                            ? cast.gap() - side : gap, now);
                }
            } else if (now >= cast.strike()) {
                if (now == cast.strike()) {
                    witch.playSound(SoundEvents.CHAIN_BREAK, 1.4F, 0.64F);
                }
                for (var player : targets(level, cast.center(), cast.radius() * Math.sqrt(2.0D) + 1, 4)) {
                    if (cast.hitsRow(player.position(), now) && rowHits.add(player.getUUID())) {
                        player.hurt(witch.damageSources().indirectMagic(witch, witch),
                                MadokaConfig.NUTCRACKER_PROCESSION_DAMAGE.get().floatValue());
                    }
                }
            }
            return;
        }
        BlockPos center = center(level);
        double radius = MadokaConfig.NUTCRACKER_PROCESSION_RADIUS.get();
        if (now < readyAt || ThornTracker.isBound(witch)
                || targets(level, center, radius * Math.sqrt(2.0D), 4).isEmpty()) {
            return;
        }
        if (readyAt == 0) {
            readyAt = now + MadokaConfig.NUTCRACKER_PROCESSION_INTERVAL.get() / 2;
            return;
        }
        processionRow = 0;
        startRow(center, radius, witch.getRandom().nextInt(4), witch.getRandom().nextInt(WitchCast.LANES), now);
    }

    private void startRow(BlockPos center, double radius, int direction, int gap, long now) {
        rowHits.clear();
        long strike = now + MadokaConfig.NUTCRACKER_PROCESSION_WARNING.get();
        long end = strike + (long) Math.ceil(radius * 2 / PROCESSION_SPEED);
        updateCast(new WitchCast(WitchCast.Kind.PROCESSION, center, radius, 0.0D, now, strike, end,
                List.of(), direction, gap));
        witch.playSound(SoundEvents.CHAIN_PLACE, 1.25F, 0.6F);
    }

    private void tickCurse(ServerLevel level, long now) {
        double radius = MadokaConfig.NUTCRACKER_CURSE_RADIUS.get();
        if (now < curseResumesAt || ThornTracker.isBound(witch)
                || targets(level, witch.blockPosition(), radius + 8, 6).isEmpty()) {
            witch.setCurseRadius(0);
            nextCurse = 0;
            return;
        }
        witch.setCurseRadius((float) radius);
        if (nextCurse == 0) {
            nextCurse = now + MadokaConfig.NUTCRACKER_CURSE_INTERVAL.get();
        }
        if (now < nextCurse) {
            return;
        }
        var effect = switch (curseTurn) {
            case 0 -> MobEffects.MOVEMENT_SLOWDOWN;
            case 1 -> MobEffects.POISON;
            default -> MobEffects.WITHER;
        };
        var players = level.getPlayers(player -> allows(player) && Math.abs(player.getY() - witch.getY()) <= 3
                && player.position().subtract(witch.position()).horizontalDistanceSqr() <= radius * radius);
        if (!players.isEmpty()) {
            for (var player : players) {
                player.addEffect(new MobEffectInstance(effect, MadokaConfig.NUTCRACKER_CURSE_DURATION.get()), witch);
            }
            curseTurn = (curseTurn + 1) % 3;
            witch.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.55F + curseTurn * 0.08F);
        }
        nextCurse = now + MadokaConfig.NUTCRACKER_CURSE_INTERVAL.get();
    }

    void tickMermaid(ServerLevel level) {
        long now = level.getGameTime();
        if (cast == null) {
            if (now < readyAt || ThornTracker.isBound(witch)) {
                return;
            }
            double radius = MadokaConfig.MERMAID_WHEEL_RANGE.get();
            BlockPos arena = center(level);
            if (targets(level, arena, radius * Math.sqrt(2.0D), 4).isEmpty()) {
                return;
            }
            if (readyAt == 0) {
                readyAt = now + MadokaConfig.MERMAID_RECOVERY.get() / 2;
                return;
            }
            mermaidBeat = 0;
            witch.getNavigation().stop();
            if (mermaidChorusNext) {
                startChorus(level, now);
            } else {
                startWheels(arena, radius, witch.getRandom().nextInt(4), now);
            }
            mermaidChorusNext = !mermaidChorusNext;
            return;
        }
        if (cast.kind() == WitchCast.Kind.MERMAID_NOTES) {
            tickChorus(level, now);
        } else if (cast.kind() == WitchCast.Kind.WHEEL_WAVE) {
            if (now > cast.end()) {
                if (ThornTracker.isBound(witch)) {
                    endCast(now, MadokaConfig.MERMAID_RECOVERY.get());
                } else if (++mermaidBeat == WHEEL_BEATS.length) {
                    startSwordSweep(level, now);
                } else {
                    startWheels(cast.center(), cast.radius(), cast.direction(), now);
                }
            } else if (now >= cast.strike()) {
                if (now == cast.strike()) {
                    witch.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.4F, 0.85F + 0.18F * mermaidBeat);
                }
                for (var player : targets(level, cast.center(), cast.radius() * Math.sqrt(2.0D) + 1, 2)) {
                    if (cast.hitsWheel(player.getBoundingBox(), now) && rowHits.add(player.getUUID())) {
                        player.hurt(witch.damageSources().mobAttack(witch), MadokaConfig.MERMAID_WHEEL_DAMAGE.get().floatValue());
                    }
                }
            }
        } else {
            lockCastFacing();
            if (now >= cast.end()) {
                endCast(now, MadokaConfig.MERMAID_RECOVERY.get());
            } else if (now == cast.strike()) {
                witch.swing(InteractionHand.MAIN_HAND);
                witch.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5F, 0.68F);
                for (var player : targets(level, cast.center(), cast.radius(), 2.2D)) {
                    if (cast.hitsSweep(player.position())) {
                        player.hurt(witch.damageSources().mobAttack(witch), MadokaConfig.MERMAID_SWEEP_DAMAGE.get().floatValue());
                    }
                }
            }
        }
    }

    private void startChorus(ServerLevel level, long now) {
        Vec3 origin = witch.position();
        var target = level.players().stream().filter(this::allows)
                .min(Comparator.comparingDouble(witch::distanceToSqr)).orElse(null);
        int yaw = target == null ? Math.round(witch.getYRot()) : yawTo(origin, target.position(), witch.getYRot());
        long strike = now + MadokaConfig.MERMAID_CHORUS_WARNING.get();
        updateCast(new WitchCast(WitchCast.Kind.MERMAID_NOTES, witch.blockPosition(), 5.0D, 0,
                now, strike, strike + MadokaConfig.MERMAID_CHORUS_DURATION.get(), List.of(), yaw, -1, 0, origin));
        lockCastFacing();
        witch.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.35F, 0.68F);
    }

    private void tickChorus(ServerLevel level, long now) {
        lockCastFacing();
        if (now >= cast.end()) {
            endCast(now, MadokaConfig.MERMAID_RECOVERY.get());
            return;
        }
        int interval = MadokaConfig.MERMAID_CHORUS_INTERVAL.get();
        if (now < cast.strike() || (now - cast.strike()) % interval != 0) {
            return;
        }
        Vec3 origin = cast.origin().add(0, MERMAID_CHORUS_HEIGHT, 0);
        float damage = MadokaConfig.MERMAID_CHORUS_DAMAGE.get().floatValue();
        for (int lane = 0; lane < WitchCast.MERMAID_NOTE_DIRECTIONS; lane++) {
            Vec3 direction = WitchCast.noteDirection(lane);
            MermaidNoteEntity.launch(level, witch, origin.add(direction.scale(MERMAID_CHORUS_MUZZLE)), direction, damage);
        }
        int beat = (int) ((now - cast.strike()) / interval);
        witch.playSound(SoundEvents.NOTE_BLOCK_HARP.value(), 1.2F, (beat & 1) == 0 ? 0.89F : 1.25F);
    }

    private void startWheels(BlockPos arena, double radius, int direction, long now) {
        rowHits.clear();
        long strike = now + MadokaConfig.MERMAID_WHEEL_WARNING.get();
        long end = strike + (long) Math.ceil(radius * 2 / MadokaConfig.MERMAID_WHEEL_SPEED.get());
        updateCast(new WitchCast(WitchCast.Kind.WHEEL_WAVE, arena, radius, 0.0D, now, strike, end,
                List.of(), direction, -1, WHEEL_BEATS[mermaidBeat]));
        witch.playSound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.9F, 0.55F + 0.15F * mermaidBeat);
    }

    private void startSwordSweep(ServerLevel level, long now) {
        BlockPos center = witch.blockPosition();
        double radius = MadokaConfig.MERMAID_SWEEP_RANGE.get();
        var target = targets(level, center, Math.max(cast.radius() * 2, radius), 4).stream()
                .min(Comparator.comparingDouble(witch::distanceToSqr));
        int yaw = target.map(player -> {
            Vec3 offset = player.position().subtract(Vec3.atBottomCenterOf(center));
            return (int) Math.round(-Mth.atan2(offset.x, offset.z) * Mth.RAD_TO_DEG);
        }).orElse(Math.round(witch.getYRot()));
        long strike = now + MadokaConfig.MERMAID_SWEEP_WARNING.get();
        updateCast(new WitchCast(WitchCast.Kind.MERMAID_SWEEP, center, radius, 0.0D, now, strike,
                strike + SWEEP_FOLLOW_THROUGH, List.of(), yaw, -1));
        lockCastFacing();
        witch.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.15F, 0.65F);
    }

    private BlockPos center(ServerLevel level) {
        BlockPos anchor = WitchArenaAttack.encounterAnchor(level, witch);
        return anchor == null ? witch.blockPosition() : anchor;
    }

    private List<ServerPlayer> targets(ServerLevel level, BlockPos center, double radius, double height) {
        return level.getPlayers(player -> allows(player) && Math.abs(player.getY() - center.getY()) <= height
                && inside(center, player, radius));
    }

    private boolean allows(ServerPlayer player) {
        return player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player);
    }

    private static boolean inside(BlockPos center, ServerPlayer player, double radius) {
        return distanceSqr(center, player.getX(), player.getZ()) <= radius * radius;
    }

    private static double distanceSqr(BlockPos center, double x, double z) {
        double dx = x - center.getX() - 0.5D;
        double dz = z - center.getZ() - 0.5D;
        return dx * dx + dz * dz;
    }

    private BlockPos randomColumn(BlockPos center, double radius) {
        double angle = witch.getRandom().nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(witch.getRandom().nextDouble()) * radius;
        return BlockPos.containing(center.getX() + 0.5D + Math.cos(angle) * distance, center.getY(),
                center.getZ() + 0.5D + Math.sin(angle) * distance);
    }

    private static BlockPos findFloor(ServerLevel level, BlockPos column, int referenceY) {
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) {
            return null;
        }
        var pos = new BlockPos.MutableBlockPos();
        for (int y = referenceY; y >= Math.max(referenceY - 10, level.getMinBuildHeight() + 1); y--) {
            pos.set(column.getX(), y - 1, column.getZ());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.above();
            }
        }
        return null;
    }

    private void updateCast(WitchCast next) {
        cast = next;
        witch.setQuintetCast(next);
    }

    void save(CompoundTag tag) {
        long resume = cast == null ? readyAt : Math.max(readyAt, cast.end() + PROCESSION_REST);
        tag.putLong("ReadyAt", resume);
        tag.putInt("SalvationTurn", salvationTurn);
        tag.putBoolean("MercyUsed", mercyUsed);
        if (cast != null && cast.kind() == WitchCast.Kind.MERCY_WAVE) {
            tag.put("MercyWave", cast.toTag());
            var touched = new ListTag();
            rowHits.forEach(id -> touched.add(NbtUtils.createUUID(id)));
            tag.put("MercyTouched", touched);
        }
        tag.putBoolean("OpheliaDecoysNext", decoysNext);
        tag.putBoolean("MermaidChorusNext", mermaidChorusNext);
    }

    void load(CompoundTag tag) {
        readyAt = tag.getLong("ReadyAt");
        salvationTurn = Math.floorMod(tag.getInt("SalvationTurn"), SALVATION_ATTACKS);
        mercyUsed = tag.getBoolean("MercyUsed");
        if (tag.contains("MercyWave", Tag.TAG_COMPOUND)) {
            updateCast(WitchCast.fromTag(tag.getCompound("MercyWave")));
            rowHits.clear();
            tag.getList("MercyTouched", Tag.TAG_INT_ARRAY).forEach(id -> rowHits.add(NbtUtils.loadUUID(id)));
        }
        decoysNext = tag.getBoolean("OpheliaDecoysNext");
        mermaidChorusNext = tag.getBoolean("MermaidChorusNext");
    }
}
