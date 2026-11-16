package io.madoka.magic.spell;

import io.madoka.magic.registry.ModParticles.Take;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class TakeVisuals {
    private static final double TURN = Math.PI * 2.0D;
    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(0.94F, 0.51F, 0.12F), 1.2F);
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 1.05F);

    private TakeVisuals() {}

    static void honeyField(ServerLevel level, Vec3 center, double radius, long now, boolean first) {
        int count = first ? Math.min(72, Math.max(30, (int) (radius * 7)))
                : Math.min(28, Math.max(12, (int) (radius * 3)));
        double shift = now * 0.045D;
        for (int i = 0; i < count; i++) {
            double angle = shift + TURN * i / count;
            var point = center.add(Math.cos(angle) * radius, 0.10D, Math.sin(angle) * radius);
            dust(level, i % 3 == 0 ? GOLD : AMBER, point);
            if (i % 6 == 0) {
                mote(level, Take.HONEY, point, new Vec3(0, 0.024D, 0));
            }
        }
        if (first) {
            burst(level, center, 10, radius * 0.27D);
        }
    }

    static void sting(ServerPlayer caster, LivingEntity target) {
        var level = caster.serverLevel();
        var start = caster.getEyePosition().add(caster.getLookAngle().scale(0.55D));
        var center = target.getBoundingBox().getCenter();
        beam(level, start, center);
        burst(level, center, 6, 0.34D);
    }

    static void beam(ServerLevel level, Vec3 start, Vec3 end) {
        int points = Math.min(80, Math.max(5, (int) Math.ceil(start.distanceTo(end) * 5.0D)));
        for (int i = 0; i <= points; i++) {
            dust(level, i % 3 == 0 ? AMBER : GOLD, start.lerp(end, (double) i / points));
        }
    }

    static void burst(ServerLevel level, Vec3 center, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = TURN * i / count;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            mote(level, Take.HONEY, center.add(direction.scale(radius)), direction.scale(0.047D).add(0, 0.050D, 0));
        }
    }

    static void illusion(ServerLevel level, LivingEntity mob, long now) {
        double angle = now * 0.24D + mob.getId();
        double radius = Math.max(0.34D, mob.getBbWidth() * 0.42D);
        var center = mob.position().add(0, mob.getBbHeight() + 0.28D, 0);
        for (int i = 0; i < 2; i++) {
            double a = angle + Math.PI * i;
            mote(level, Take.ILLUSION, center.add(radius * Math.cos(a), 0, radius * Math.sin(a)), Vec3.ZERO);
        }
    }

    private static void dust(ServerLevel level, DustParticleOptions type, Vec3 point) {
        level.sendParticles(type, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }

    static void mote(ServerLevel level, Take effect, Vec3 point, Vec3 velocity) {
        level.sendParticles(effect.get(), point.x, point.y, point.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
    }
}
