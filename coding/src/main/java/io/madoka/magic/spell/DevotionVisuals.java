package io.madoka.magic.spell;

import io.madoka.magic.registry.ModParticles.Devotion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

final class DevotionVisuals {
    private static final double FULL_TURN = Math.PI * 2.0D;

    private DevotionVisuals() {
    }

    static void slash(ServerPlayer caster, Vec3 forward, double range, double halfAngle) {
        ServerLevel level = caster.serverLevel();
        Vec3 origin = caster.position().add(0.0D, caster.getBbHeight() * 0.55D, 0.0D);
        Vec3 tip = origin.add(forward.scale(range * 0.56D));
        Vec3 slash = level.clip(new ClipContext(origin, tip, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getLocation();
        spawnScaled(level, Devotion.SLASH, slash);
        for (int index = -2; index <= 2; index++) {
            Vec3 direction = forward.yRot((float) (index * halfAngle / 2.0D));
            Vec3 edge = origin.add(direction.scale(range * 0.67D));
            Vec3 point = level.clip(new ClipContext(origin, edge, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster)).getLocation();
            spawn(level, Devotion.BUTTERFLY, point, direction.scale(0.09D).add(0.0D, 0.045D, 0.0D));
        }
    }

    static void tulips(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        Vec3 center = caster.position();
        spawnScaled(level, Devotion.TULIP, center.add(0.0D, 0.50D, 0.0D));
        double offset = level.random.nextDouble() * FULL_TURN;
        for (int i = 0; i < 8; i++) {
            double angle = offset + FULL_TURN * i / 8;
            double radius = SpellCasting.range((i % 2 == 0) ? 1.65D : 2.75D);
            Vec3 point = center.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
            var ground = level.clip(new ClipContext(point.add(0.0D, 1.2D, 0.0D),
                    point.add(0.0D, -1.4D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (ground.getType() != HitResult.Type.MISS) {
                spawnScaled(level, Devotion.TULIP, ground.getLocation().add(0.0D, 0.50D, 0.0D));
            }
        }
        scatter(level, Devotion.PETAL, center.add(0.0D, 0.75D, 0.0D), 10, 0.12D, 0.09D);
    }

    static void pollen(LivingEntity target) {
        if (target.level() instanceof ServerLevel level) {
            spawnScaled(level, Devotion.POLLEN, target.getBoundingBox().getCenter().add(0.0D, 0.18D, 0.0D));
        }
    }

    static void blossom(ServerPlayer caster, Devotion phase) {
        Vec3 look = caster.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z).normalize();
        Vec3 center = caster.position().add(horizontal.scale(0.90D)).add(0.0D, 0.90D, 0.0D);
        ServerLevel level = caster.serverLevel();
        spawnScaled(level, phase, center);
        if (phase == Devotion.BLOOM) {
            scatter(level, Devotion.BUTTERFLY, center, 4, 0.075D, 0.038D);
        } else if (phase == Devotion.FALL) {
            scatter(level, Devotion.PETAL, center, 7, 0.075D, -0.028D);
        }
    }

    static void blossomTick(ServerPlayer caster, boolean falling) {
        ServerLevel level = caster.serverLevel();
        double angle = level.getGameTime() * 0.19D + caster.getId();
        double radius = SpellCasting.range(falling ? 0.75D : 1.10D);
        for (int i = 0; i < 3; i++) {
            double a = angle + FULL_TURN * i / 3;
            Vec3 pos = caster.position().add(Math.cos(a) * radius, 0.48D + i * 0.22D, Math.sin(a) * radius);
            Vec3 movement = new Vec3(-Math.sin(a) * 0.033D, falling ? -0.03D : 0.035D, Math.cos(a) * 0.033D);
            spawn(level, falling ? Devotion.PETAL : Devotion.BUTTERFLY, pos, movement);
        }
    }

    static void wither(ServerPlayer caster, boolean taint, boolean fallen) {
        ServerLevel level = caster.serverLevel();
        Vec3 feet = caster.position();
        spawnScaled(level, taint ? Devotion.WITHER_TAINT : Devotion.WITHER_LIFE, feet.add(0.0D, 0.035D, 0.0D));
        scatter(level, Devotion.PETAL, feet.add(0.0D, 0.52D, 0.0D), fallen ? 18 : 11, 0.24D, 0.13D);
    }

    private static void scatter(ServerLevel level, Devotion particle, Vec3 center, int count,
                                double speed, double lift) {
        RandomSource random = level.random;
        double offset = random.nextDouble() * FULL_TURN;
        for (int i = 0; i < count; i++) {
            double a = offset + FULL_TURN * i / count;
            double v = speed * (0.7D + random.nextDouble() * 0.6D);
            Vec3 radial = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
            Vec3 point = center.add(radial.scale(0.16D + random.nextDouble() * 0.18D));
            spawn(level, particle, point, radial.scale(v).add(0.0D, lift + random.nextDouble() * 0.035D, 0.0D));
        }
    }

    private static void spawnScaled(ServerLevel level, Devotion particle, Vec3 position) {
        spawn(level, particle, position, new Vec3(SpellCasting.range(1.0D), 0.0D, 0.0D));
    }

    private static void spawn(ServerLevel level, Devotion particle, Vec3 position, Vec3 velocity) {
        // A packet with count zero supplies an exact velocity for its one particle.
        level.sendParticles(particle.get(), position.x, position.y, position.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0D);
    }
}
