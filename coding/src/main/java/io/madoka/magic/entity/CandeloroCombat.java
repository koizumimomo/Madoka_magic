package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CandeloroCombat {
    private static final int CAKE_COUNT = 2;
    private static final int STAIN_COUNT = 3;
    private static final WitchCast.Kind[] ROTATION = {WitchCast.Kind.CANDELORO_CAKES,
            WitchCast.Kind.CANDELORO_CUTLERY, WitchCast.Kind.CANDELORO_TEA};
    private final MadokaWitchEntity witch;
    private int nextAttack;
    private long readyAt;

    CandeloroCombat(MadokaWitchEntity witch) {
        this.witch = witch;
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        var cast = witch.getQuintetCast();
        if (cast != null) {
            tickCast(level, now, cast);
            return;
        }
        if (now < readyAt || ThornTracker.isBound(witch)) {
            return;
        }
        double range = MadokaConfig.CANDELORO_CAST_RANGE.get();
        var target = level.players().stream().filter(this::allows)
                .filter(player -> witch.distanceToSqr(player) <= range * range)
                .min(Comparator.comparingDouble(witch::distanceToSqr)).orElse(null);
        if (target == null) {
            return;
        }
        if (readyAt == 0) {
            readyAt = now + MadokaConfig.CANDELORO_REST.get() / 2;
            return;
        }
        WitchCast.Kind attack = ROTATION[nextAttack];
        nextAttack = (nextAttack + 1) % ROTATION.length;
        long strike = now + MadokaConfig.CANDELORO_WARNING.get();
        Vec3 origin = witch.position();
        Vec3 aim = target.position().subtract(origin);
        int direction = (int) Math.round(-Mth.atan2(aim.x, aim.z) * Mth.RAD_TO_DEG);
        List<BlockPos> markers = switch (attack) {
            case CANDELORO_CAKES -> cakePositions(level, target, direction);
            case CANDELORO_TEA -> teaPositions(level, target, direction);
            default -> List.of();
        };
        if (attack != WitchCast.Kind.CANDELORO_CUTLERY && markers.isEmpty()) {
            readyAt = now + 20;
            return;
        }
        int duration = switch (attack) {
            case CANDELORO_CUTLERY -> WitchCast.CANDELORO_KNIFE_DELAY + 12;
            case CANDELORO_TEA -> MadokaConfig.CANDELORO_TEA_DURATION.get();
            default -> 12;
        };
        witch.getNavigation().stop();
        witch.setDeltaMovement(witch.getDeltaMovement().multiply(0, 1, 0));
        witch.setYRot(direction);
        witch.yBodyRot = direction;
        witch.setQuintetCast(new WitchCast(attack, witch.blockPosition(), range,
                MadokaConfig.CANDELORO_TEA_RADIUS.get(), now, strike, strike + duration,
                markers, direction, -1, 0, origin));
        witch.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.1F, 1.3F);
    }

    private void tickCast(ServerLevel level, long now, WitchCast cast) {
        if (now >= cast.end()) {
            finish(now);
            return;
        }
        if (now < cast.strike()) {
            return;
        }
        long elapsed = now - cast.strike();
        switch (cast.kind()) {
            case CANDELORO_CAKES -> {
                if (elapsed == 0) {
                    for (BlockPos mark : cast.markers()) {
                        DessertFamiliarEntity.spawn(level, witch, mark);
                    }
                    witch.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.45F);
                }
            }
            case CANDELORO_CUTLERY -> {
                if (elapsed == 0) {
                    for (int lane = 0; lane < WitchCast.CANDELORO_FORKS; lane++) {
                        CandeloroCutleryEntity.fire(level, witch, cast, lane, false);
                    }
                    witch.playSound(SoundEvents.CHAIN_BREAK, 1.2F, 1.35F);
                } else if (elapsed == WitchCast.CANDELORO_KNIFE_DELAY) {
                    CandeloroCutleryEntity.fire(level, witch, cast, 0, true);
                    witch.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.3F, 1.4F);
                }
            }
            case CANDELORO_TEA -> {
                if (elapsed == 0) {
                    witch.playSound(SoundEvents.BUCKET_EMPTY, 1.2F, 0.82F);
                }
                if (elapsed % MadokaConfig.CANDELORO_TEA_INTERVAL.get() == 0) {
                    for (var player : level.players()) {
                        if (allows(player) && cast.markers().stream()
                                .anyMatch(spot -> TeaStain.contains(spot, cast.markerRadius(), player.position()))) {
                            WitchCombat.hurtWithoutKnockback(player, witch.damageSources().indirectMagic(witch, witch),
                                    MadokaConfig.CANDELORO_TEA_DAMAGE.get().floatValue());
                        }
                    }
                }
            }
            default -> { }
        }
    }

    private static List<BlockPos> cakePositions(ServerLevel level, ServerPlayer target, int yaw) {
        var marks = new ArrayList<BlockPos>(CAKE_COUNT);
        Vec3 side = Vec3.directionFromRotation(0, yaw + 90);
        for (int offset : new int[]{-1, 1}) {
            addFloor(level, marks, target.position().add(side.scale(4.0 * offset)), target.getBlockY());
        }
        return marks;
    }

    private static List<BlockPos> teaPositions(ServerLevel level, ServerPlayer target, int yaw) {
        var marks = new ArrayList<BlockPos>(STAIN_COUNT);
        addFloor(level, marks, target.position(), target.getBlockY());
        for (int angle : new int[]{-105, 105}) {
            var point = target.position().add(Vec3.directionFromRotation(0, yaw + angle).scale(4.25));
            addFloor(level, marks, point, target.getBlockY());
        }
        return marks;
    }

    private static void addFloor(ServerLevel level, List<BlockPos> marks, Vec3 point, int referenceY) {
        var column = BlockPos.containing(point);
        if (!level.hasChunkAt(column) || !level.getWorldBorder().isWithinBounds(column)) {
            return;
        }
        int floor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (Math.abs(floor - referenceY) <= 5) {
            marks.add(column.atY(floor));
        }
    }

    private boolean allows(ServerPlayer player) {
        return player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player);
    }

    boolean interrupt(long now) {
        var cast = witch.getQuintetCast();
        if (cast == null || now >= cast.strike()) {
            return false;
        }
        finish(now);
        return true;
    }

    private void finish(long now) {
        witch.setQuintetCast(null);
        readyAt = now + MadokaConfig.CANDELORO_REST.get();
    }

    void save(CompoundTag tag) {
        var cast = witch.getQuintetCast();
        tag.putLong("ReadyAt", cast == null ? readyAt : cast.end() + MadokaConfig.CANDELORO_REST.get());
        tag.putInt("NextAttack", nextAttack);
    }

    void load(CompoundTag tag) {
        readyAt = tag.getLong("ReadyAt");
        nextAttack = Math.floorMod(tag.getInt("NextAttack"), ROTATION.length);
    }
}
