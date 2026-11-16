package io.madoka.magic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;

/** A fixed telegraph shared by the server's hit checks and the world renderer. */
public record WitchCast(Kind kind, BlockPos center, double radius, double markerRadius,
                        long start, long strike, long end, List<BlockPos> markers, int direction, int gap,
                        int wheelLanes, Vec3 origin) {
    public enum Kind {
        ARROW_RAIN, HEAVEN, REDEMPTION, PROCESSION, WHEEL_WAVE, MERMAID_SWEEP, SALVATION_CROSS, OPHELIA_DASH,
        ATONEMENT_SUMMON, ATONEMENT_LINE, NIGHT_LIGHTNING, MERMAID_NOTES, MERCY_WAVE,
        CANDELORO_CAKES, CANDELORO_CUTLERY, CANDELORO_TEA
    }

    public static final int LANES = 5;
    public static final double BEAM_HEIGHT = 16.0D;
    public static final double PROCESSION_DEPTH = 0.85D;
    public static final double PROCESSION_HEIGHT = 2.4D;
    public static final double WHEEL_RADIUS = 0.49D;
    public static final double WHEEL_HALF_WIDTH = 0.15D;
    public static final float MERMAID_SWEEP_HALF_ANGLE = 67.5F;
    public static final int MERMAID_NOTE_DIRECTIONS = 8;
    public static final double CROSS_BEAM_Y = 0.85D;
    public static final double MERCY_WAVE_RADIUS = 32.0D;
    public static final double MERCY_WAVE_HEIGHT = 2.6D;
    public static final int CANDELORO_FORKS = 5;
    public static final int CANDELORO_KNIFE_DELAY = 14;
    public static final double CANDELORO_MUZZLE = 1.25D;
    public static final double OPHELIA_DECOY_OFFSET = 4.0D;

    public WitchCast {
        markers = List.copyOf(markers);
    }

    public WitchCast(Kind kind, BlockPos center, double radius, double markerRadius,
                     long start, long strike, long end, List<BlockPos> markers) {
        this(kind, center, radius, markerRadius, start, strike, end, markers, 0, -1);
    }

    public WitchCast(Kind kind, BlockPos center, double radius, double markerRadius,
                     long start, long strike, long end, List<BlockPos> markers, int direction, int gap) {
        this(kind, center, radius, markerRadius, start, strike, end, markers, direction, gap, 0);
    }

    public WitchCast(Kind kind, BlockPos center, double radius, double markerRadius,
                     long start, long strike, long end, List<BlockPos> markers, int direction, int gap, int wheelLanes) {
        this(kind, center, radius, markerRadius, start, strike, end, markers, direction, gap, wheelLanes,
                Vec3.atBottomCenterOf(center));
    }

    public Vec3 castPoint(double across, double y, double forward) {
        return sweepPoint(90, across).add(0, y, 0).add(sweepPoint(0, forward));
    }

    public static Vec3 noteDirection(int lane) {
        return Vec3.directionFromRotation(0, lane * (360.0F / MERMAID_NOTE_DIRECTIONS));
    }

    public double mercyRadiusAt(double time) {
        return radius * Mth.clamp((time - strike) / (end - strike), 0.0D, 1.0D);
    }

    public Vec3 cutleryDirection(int lane, boolean knife) {
        return sweepPoint(knife ? 0 : (lane - (CANDELORO_FORKS - 1) * 0.5F) * 16.0F, 1);
    }

    public Vec3 cutleryStart(Vec3 direction) {
        return origin.add(0, 0.92D, 0).add(direction.scale(CANDELORO_MUZZLE));
    }

    public double dashDistance(double time) {
        return radius * Mth.clamp((time - strike) / (end - strike), 0.0D, 1.0D);
    }

    public boolean hasDecoy(int side) {
        return (wheelLanes & (side > 0 ? 1 : 2)) != 0;
    }

    public Vec3 crossEnd(Level level, Entity caster, int arm) {
        Vec3 start = rayOrigin();
        return level.clip(new ClipContext(start, start.add(sweepPoint(arm * 90.0F, radius)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
    }

    public Vec3 rayOrigin() {
        return origin.add(0, CROSS_BEAM_Y, 0);
    }

    boolean hitsAtonementLine(Level level, Entity caster, AABB target) {
        Vec3 center = rayOrigin();
        Vec3 forward = sweepPoint(0, 1);
        Vec3 side = sweepPoint(90, 1);
        Vec3 offset = target.getCenter().subtract(center);
        double half = target.getXsize() * 0.5D;
        Vec3 end = crossEnd(level, caster, offset.dot(forward) < 0.0D ? 2 : 0);
        return target.minY <= center.y + 0.65D && target.maxY >= center.y - 0.65D
                && Math.abs(offset.dot(side)) <= markerRadius + half
                && Math.abs(offset.dot(forward)) <= end.distanceTo(center) + half
                && level.clip(new ClipContext(center, target.getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    boolean hitsCross(Level level, Entity caster, AABB target) {
        Vec3 middle = rayOrigin();
        double height = markerRadius * 0.62D;
        if (target.minY > middle.y + height || target.maxY < middle.y - height) {
            return false;
        }
        Vec3 offset = target.getCenter().subtract(middle);
        for (int arm = 0; arm < 4; arm++) {
            Vec3 forward = sweepPoint(arm * 90.0F, 1.0D);
            Vec3 end = crossEnd(level, caster, arm);
            double length = end.distanceTo(middle);
            double along = offset.dot(forward);
            Vec3 side = new Vec3(-forward.z, 0, forward.x);
            double alongPadding = (Math.abs(forward.x) * target.getXsize() + Math.abs(forward.z) * target.getZsize()) * 0.5D;
            double sidePadding = (Math.abs(side.x) * target.getXsize() + Math.abs(side.z) * target.getZsize()) * 0.5D;
            if (along + alongPadding >= 0 && along - alongPadding <= length
                    && Math.abs(offset.dot(side)) <= markerRadius + sidePadding) {
                return true;
            }
        }
        return false;
    }

    public double laneEdge(int lane) {
        return -radius + 2.0D * radius * lane / LANES;
    }

    public double rowAt(double time) {
        double progress = Mth.clamp((time - strike) / (end - strike), 0.0D, 1.0D);
        return Mth.lerp(progress, -radius, radius);
    }

    public Vec3 rowPoint(double across, double y, double along) {
        return switch (direction) {
            case 1 -> new Vec3(along, y, -across);
            case 2 -> new Vec3(-across, y, -along);
            case 3 -> new Vec3(-along, y, across);
            default -> new Vec3(across, y, along);
        };
    }

    boolean hitsRow(Vec3 position, long now) {
        Vec3 offset = position.subtract(Vec3.atBottomCenterOf(center));
        if (offset.y < -0.75D || offset.y > PROCESSION_HEIGHT) {
            return false;
        }
        double across = offset.dot(rowPoint(1, 0, 0));
        double along = offset.dot(rowPoint(0, 0, 1));
        return Math.abs(across) <= radius && (across < laneEdge(gap) || across >= laneEdge(gap + 1))
                && Math.abs(along - rowAt(now)) <= PROCESSION_DEPTH;
    }

    public double laneCenter(int lane) {
        return (laneEdge(lane) + laneEdge(lane + 1)) * 0.5D;
    }

    public boolean hasWheel(int lane) {
        return (wheelLanes & (1 << lane)) != 0;
    }

    boolean hitsWheel(AABB target, long now) {
        Vec3 origin = Vec3.atBottomCenterOf(center);
        if (target.minY >= origin.y + WHEEL_RADIUS * 2 || target.maxY <= origin.y) {
            return false;
        }
        double halfPlayerWidth = target.getXsize() * 0.5D;
        Vec3 offset = target.getCenter().subtract(origin);
        double along = offset.dot(rowPoint(0, 0, 1));
        if (along < rowAt(now - 1) - WHEEL_RADIUS - halfPlayerWidth
                || along > rowAt(now) + WHEEL_RADIUS + halfPlayerWidth) {
            return false;
        }
        double across = offset.dot(rowPoint(1, 0, 0));
        for (int lane = 0; lane < LANES; lane++) {
            if (hasWheel(lane) && Math.abs(across - laneCenter(lane)) <= WHEEL_HALF_WIDTH + halfPlayerWidth) {
                return true;
            }
        }
        return false;
    }

    public Vec3 sweepPoint(float angle, double reach) {
        return Vec3.directionFromRotation(0, direction + angle).scale(reach);
    }

    boolean hitsSweep(Vec3 position) {
        Vec3 offset = position.subtract(Vec3.atBottomCenterOf(center));
        double distanceSqr = offset.horizontalDistanceSqr();
        if (distanceSqr > radius * radius || offset.y < -0.75D || offset.y > 2.2D) {
            return false;
        }
        return distanceSqr < 0.01D
                || offset.dot(sweepPoint(0, 1)) >= Math.sqrt(distanceSqr) * Math.cos(MERMAID_SWEEP_HALF_ANGLE * Mth.DEG_TO_RAD);
    }

    CompoundTag toTag() {
        var tag = new CompoundTag();
        tag.putInt("Kind", kind.ordinal());
        tag.putLong("Center", center.asLong());
        tag.putDouble("Radius", radius);
        tag.putDouble("MarkerRadius", markerRadius);
        tag.putLong("Start", start);
        tag.putLong("Strike", strike);
        tag.putLong("End", end);
        tag.putLongArray("Markers", markers.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("Direction", direction);
        tag.putInt("Gap", gap);
        tag.putInt("WheelLanes", wheelLanes);
        if (hasExactOrigin(kind)) {
            tag.putDouble("OriginX", origin.x);
            tag.putDouble("OriginY", origin.y);
            tag.putDouble("OriginZ", origin.z);
        }
        return tag;
    }

    static WitchCast fromTag(CompoundTag tag) {
        if (tag.isEmpty()) {
            return null;
        }
        var kind = Kind.values()[tag.getInt("Kind")];
        var center = BlockPos.of(tag.getLong("Center"));
        Vec3 origin = hasExactOrigin(kind)
                ? new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"))
                : Vec3.atBottomCenterOf(center);
        return new WitchCast(kind, center,
                tag.getDouble("Radius"), tag.getDouble("MarkerRadius"), tag.getLong("Start"),
                tag.getLong("Strike"), tag.getLong("End"),
                Arrays.stream(tag.getLongArray("Markers")).mapToObj(BlockPos::of).toList(),
                tag.getInt("Direction"), tag.getInt("Gap"), tag.getInt("WheelLanes"), origin);
    }

    private static boolean hasExactOrigin(Kind kind) {
        return kind == Kind.OPHELIA_DASH || kind == Kind.ATONEMENT_LINE || kind == Kind.MERMAID_NOTES
                || kind == Kind.CANDELORO_CUTLERY;
    }
}
