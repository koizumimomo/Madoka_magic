package io.madoka.magic.spell;

import io.madoka.magic.registry.ModParticles.Witch;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 魔女途径技能的紫黑色施法特效，与魔法少女的金色系区分。 */
final class WitchVisuals {
    private static final double FULL_TURN = Math.PI * 2.0D;
    private static final DustParticleOptions VOID = new DustParticleOptions(new Vector3f(0.13F, 0.04F, 0.25F), 1.25F);
    private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.43F, 0.16F, 0.72F), 1.10F);
    private static final DustParticleOptions GLOW = new DustParticleOptions(new Vector3f(0.72F, 0.45F, 0.95F), 0.92F);

    private WitchVisuals() {}

    /** 黑翼斩：紫黑月牙 + 暗色羽屑。 */
    static void slash(ServerPlayer caster, Vec3 forward, double range, double halfAngle) {
        ServerLevel level = caster.serverLevel();
        Vec3 origin = caster.position().add(0.0D, caster.getBbHeight() * 0.55D, 0.0D);
        Vec3 tip = origin.add(forward.scale(range * 0.56D));
        Vec3 slash = level.clip(new ClipContext(origin, tip, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getLocation();
        spawnScaled(level, Witch.SLASH, slash);
        for (int index = -2; index <= 2; index++) {
            Vec3 direction = forward.yRot((float) (index * halfAngle / 2.0D));
            Vec3 edge = origin.add(direction.scale(range * 0.67D));
            Vec3 point = level.clip(new ClipContext(origin, edge, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster)).getLocation();
            spawn(level, Witch.MOTE, point, direction.scale(0.09D).add(0.0D, 0.045D, 0.0D));
        }
    }

    /** 蛊毒刺击：紫黑射线 + 淬毒珠。 */
    static void sting(ServerPlayer caster, LivingEntity target) {
        sting(caster, target.getBoundingBox().getCenter());
    }

    /** 无目标空放：射线打向指定点。 */
    static void sting(ServerPlayer caster, Vec3 center) {
        var level = caster.serverLevel();
        var start = caster.getEyePosition().add(caster.getLookAngle().scale(0.55D));
        beam(level, start, center);
        burst(level, center, 6, 0.34D);
    }

    static void beam(ServerLevel level, Vec3 start, Vec3 end) {
        int points = Math.min(80, Math.max(5, (int) Math.ceil(start.distanceTo(end) * 5.0D)));
        for (int i = 0; i <= points; i++) {
            dust(level, i % 3 == 0 ? GLOW : VIOLET, start.lerp(end, (double) i / points));
        }
    }

    /** 蜜潮领域：紫黑环带 + 淬毒珠。 */
    static void field(ServerLevel level, Vec3 center, double radius, long now, boolean first) {
        int count = first ? Math.min(72, Math.max(30, (int) (radius * 7)))
                : Math.min(28, Math.max(12, (int) (radius * 3)));
        double shift = now * 0.045D;
        for (int i = 0; i < count; i++) {
            double angle = shift + FULL_TURN * i / count;
            var point = center.add(Math.cos(angle) * radius, 0.10D, Math.sin(angle) * radius);
            dust(level, i % 3 == 0 ? GLOW : VOID, point);
            if (i % 6 == 0) {
                mote(level, Witch.VENOM, point, new Vec3(0, 0.024D, 0));
            }
        }
        if (first) {
            burst(level, center, 10, radius * 0.27D);
        }
    }

    static void burst(ServerLevel level, Vec3 center, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = FULL_TURN * i / count;
            Vec3 direction = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            mote(level, Witch.VENOM, center.add(direction.scale(radius)),
                    direction.scale(0.047D).add(0, 0.050D, 0));
        }
    }

    /** 茧爆：目标身上炸开紫黑咒环 + 升腾暗屑。 */
    static void cocoonBurst(ServerLevel level, Vec3 center) {
        spawnScaled(level, Witch.MARK, center);
        scatter(level, Witch.MOTE, center, 10, 0.14D, 0.09D);
        for (int i = 0; i < 8; i++) {
            double angle = FULL_TURN * i / 8;
            dust(level, VIOLET, center.add(Math.cos(angle) * 0.42D, 0.0D, Math.sin(angle) * 0.42D));
        }
    }

    /** 泪雨脉冲：大片紫黑泪滴错落坠落 + 竖直雨丝 + 地面脉冲环。 */
    static void tearRain(ServerLevel level, Vec3 center, double radius) {
        RandomSource random = level.random;
        // 密集泪滴：随机散布全领域，起始高度错落，落点时间不同才有雨感
        for (int i = 0; i < 32; i++) {
            double angle = random.nextDouble() * FULL_TURN;
            double r = radius * Math.sqrt(random.nextDouble());
            double height = 3.4D + random.nextDouble() * 2.6D;
            mote(level, Witch.TEAR, center.add(Math.cos(angle) * r, height, Math.sin(angle) * r),
                    new Vec3(0, -0.24D, 0));
        }
        // 竖直雨丝：分段亮点拉出下落轨迹
        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * FULL_TURN;
            double r = radius * Math.sqrt(random.nextDouble());
            double x = center.x + Math.cos(angle) * r;
            double z = center.z + Math.sin(angle) * r;
            for (int s = 0; s < 4; s++) {
                dust(level, s % 2 == 0 ? VIOLET : GLOW, new Vec3(x, 1.2D + s * 1.05D, z));
            }
        }
        // 外圈旋转泪滴保留环绕感
        for (int i = 0; i < 10; i++) {
            double angle = level.getGameTime() * 0.31D + FULL_TURN * i / 10;
            double r = radius * (0.20D + 0.72D * ((i * 5 % 7) / 7.0D));
            mote(level, Witch.TEAR, center.add(Math.cos(angle) * r, 4.2D, Math.sin(angle) * r),
                    new Vec3(0, -0.22D, 0));
        }
        spawnScaled(level, Witch.PULSE, center.add(0.0D, 0.08D, 0.0D));
    }

    /** 泪滴命中目标的溅落。 */
    static void tearHit(ServerLevel level, Vec3 center) {
        scatter(level, Witch.TEAR, center, 5, 0.10D, -0.02D);
    }

    /** 荆棘回响：目标脚下炸开荆棘脉冲。 */
    static void thornEcho(ServerLevel level, Vec3 center) {
        spawnScaled(level, Witch.PULSE, center);
        scatter(level, Witch.MOTE, center, 8, 0.12D, 0.06D);
        beam(level, center.add(0.0D, 0.1D, 0.0D), center.add(0.0D, 1.6D, 0.0D));
    }

    private static void scatter(ServerLevel level, Witch particle, Vec3 center, int count,
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

    private static void spawnScaled(ServerLevel level, Witch particle, Vec3 position) {
        spawn(level, particle, position, new Vec3(SpellCasting.range(1.0D), 0.0D, 0.0D));
    }

    private static void spawn(ServerLevel level, Witch particle, Vec3 position, Vec3 velocity) {
        level.sendParticles(particle.get(), position.x, position.y, position.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0D);
    }

    private static void mote(ServerLevel level, Witch effect, Vec3 point, Vec3 velocity) {
        level.sendParticles(effect.get(), point.x, point.y, point.z, 0, velocity.x, velocity.y, velocity.z, 1.0D);
    }

    private static void dust(ServerLevel level, DustParticleOptions type, Vec3 point) {
        level.sendParticles(type, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
}
