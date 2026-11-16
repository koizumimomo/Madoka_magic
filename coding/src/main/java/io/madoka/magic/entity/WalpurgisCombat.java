package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

final class WalpurgisCombat {
    private static final double RANGE = 64.0D;
    private static final int FIRST_ATTACK_DELAY = 105;
    private static final int GEAR_INTERVAL = 14;
    private static final int FIREBALL_INTERVAL = 5;
    private static final int LIGHTNING_VISUAL_TICKS = 8;

    private enum Attack {
        GEARS, LIGHTNING, FIREBALLS
    }

    private final MadokaWitchEntity witch;
    private Attack attack = Attack.GEARS;
    private long nextAction;
    private int remaining;

    WalpurgisCombat(MadokaWitchEntity witch) {
        this.witch = witch;
    }

    void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (nextAction == 0) {
            nextAction = now + FIRST_ATTACK_DELAY;
        }
        WitchCast cast = witch.getQuintetCast();
        if (cast != null) {
            if (now >= cast.end()) {
                witch.setQuintetCast(null);
                finish(now);
            } else if (now == cast.strike()) {
                strikeLightning(level, cast);
            }
            return;
        }
        if (now < nextAction || remaining == 0 && ThornTracker.isBound(witch)) {
            return;
        }
        List<ServerPlayer> targets = level.players().stream().filter(this::canTarget)
                .sorted(Comparator.comparingDouble(witch::distanceToSqr)).toList();
        if (targets.isEmpty()) {
            remaining = 0;
            nextAction = now + 20;
            return;
        }
        if (attack == Attack.LIGHTNING) {
            warnLightning(level, targets, now);
            return;
        }
        if (remaining == 0) {
            int count = attack == Attack.GEARS ? MadokaConfig.NIGHT_GEAR_COUNT.get() : MadokaConfig.NIGHT_FIREBALL_COUNT.get();
            remaining = enraged() ? count + Math.max(1, count / 2) : count;
        }
        var target = targets.get((remaining - 1) % targets.size());
        Vec3 start = launchPoint(target);
        if (attack == Attack.GEARS) {
            sendGear(level, target, start);
        } else {
            sendFireball(level, target, start, remaining == 1);
        }
        if (--remaining == 0) {
            finish(now);
        } else {
            nextAction = now + (attack == Attack.GEARS ? GEAR_INTERVAL : FIREBALL_INTERVAL);
        }
    }

    private boolean canTarget(ServerPlayer player) {
        return player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player)
                && witch.distanceToSqr(player) <= RANGE * RANGE;
    }

    private boolean enraged() {
        return witch.getHealth() < witch.getMaxHealth() * 0.5F;
    }

    private Vec3 launchPoint(ServerPlayer player) {
        Vec3 toward = player.position().subtract(witch.position()).multiply(1, 0, 1).normalize();
        if (toward.lengthSqr() == 0) {
            toward = new Vec3(0, 0, 1);
        }
        Vec3 sideways = new Vec3(toward.z, 0, -toward.x);
        double jitter = (witch.getRandom().nextDouble() - 0.5D) * witch.getBbWidth() * 0.65D;
        return witch.position().add(toward.scale(witch.getBbWidth() * 0.48D)).add(sideways.scale(jitter))
                .add(0, witch.getBbHeight() * 0.38D, 0);
    }

    private void sendGear(ServerLevel level, ServerPlayer player, Vec3 start) {
        double angle = witch.getRandom().nextDouble() * Math.PI * 2;
        double distance = Math.sqrt(witch.getRandom().nextDouble()) * MadokaConfig.NIGHT_GEAR_SPREAD.get();
        Vec3 offset = player.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        NightGearEntity.launch(level, witch, start, groundBelow(level, offset));
        witch.playSound(SoundEvents.CHAIN_BREAK, 1.2F, 0.65F);
    }

    private Vec3 groundBelow(ServerLevel level, Vec3 position) {
        Vec3 top = position.add(0, 0.25D, 0);
        var hit = level.clip(new ClipContext(top, position.add(0, -24.0D, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, witch));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : position;
    }

    private void sendFireball(ServerLevel level, ServerPlayer player, Vec3 start, boolean last) {
        Vec3 aim = player.getEyePosition().subtract(start);
        if (last) {
            var fireball = new LargeFireball(level, witch, aim.x, aim.y, aim.z, 1);
            fireball.setPos(start);
            level.addFreshEntity(fireball);
            witch.playSound(SoundEvents.GHAST_SHOOT, 1.45F, 0.8F);
        } else {
            aim = aim.add(witch.getRandom().triangle(0, 3.0D), witch.getRandom().triangle(0, 1.5D),
                    witch.getRandom().triangle(0, 3.0D));
            var fireball = new SmallFireball(level, witch, aim.x, aim.y, aim.z);
            fireball.setPos(start);
            level.addFreshEntity(fireball);
            witch.playSound(SoundEvents.BLAZE_SHOOT, 0.9F, 0.8F);
        }
    }

    private void warnLightning(ServerLevel level, List<ServerPlayer> targets, long now) {
        var markers = targets.stream().map(player -> BlockPos.containing(groundBelow(level, player.position()).add(0, 0.05D, 0)))
                .distinct().toList();
        long strike = now + MadokaConfig.NIGHT_LIGHTNING_WARNING.get();
        double radius = MadokaConfig.NIGHT_LIGHTNING_RADIUS.get();
        witch.setQuintetCast(new WitchCast(WitchCast.Kind.NIGHT_LIGHTNING, witch.blockPosition(), RANGE, radius,
                now, strike, strike + LIGHTNING_VISUAL_TICKS, markers));
        witch.playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.55F, 0.65F);
    }

    private void strikeLightning(ServerLevel level, WitchCast cast) {
        for (BlockPos marker : cast.markers()) {
            var bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.setVisualOnly(true);
                bolt.moveTo(Vec3.atBottomCenterOf(marker));
                level.addFreshEntity(bolt);
            }
        }
        double radiusSqr = cast.markerRadius() * cast.markerRadius();
        float damage = MadokaConfig.NIGHT_LIGHTNING_DAMAGE.get().floatValue();
        for (var player : level.players()) {
            if (!canTarget(player) || damage <= 0) {
                continue;
            }
            boolean struck = cast.markers().stream().anyMatch(marker -> {
                Vec3 offset = player.position().subtract(Vec3.atBottomCenterOf(marker));
                return Math.abs(offset.y) <= 3.0D && offset.horizontalDistanceSqr() <= radiusSqr;
            });
            if (struck) {
                player.hurt(witch.damageSources().indirectMagic(witch, witch), damage);
            }
        }
    }

    boolean interrupt(long now) {
        WitchCast cast = witch.getQuintetCast();
        if (cast == null || now >= cast.strike()) {
            return false;
        }
        witch.setQuintetCast(null);
        finish(now);
        return true;
    }

    private void finish(long now) {
        attack = switch (attack) {
            case GEARS -> Attack.LIGHTNING;
            case LIGHTNING -> Attack.FIREBALLS;
            case FIREBALLS -> Attack.GEARS;
        };
        int rest = MadokaConfig.NIGHT_ATTACK_REST.get();
        nextAction = now + (enraged() ? rest * 2 / 3 : rest);
    }
}
