package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class QuintetWitchVisuals {
    private static final double TURN = Math.PI * 2;
    private static final double FLOOR = 0.032D;
    private static final int PINK = 0xff72ba;
    private static final int WHITE = 0xfff9ff;
    private static final int SHELTER = 0x92fff0;
    private static final int VIOLET = 0xb37aff;
    private static final int RED = 0xe84d85;
    private static final int SEA_BLUE = 0x59a7e6;
    private static final int FOAM = 0xc1f1ff;
    private static final int EMBER = 0xf46f44;
    private static final int GOLD = 0xffcf82;

    private final PoseStack.Pose pose;
    private final VertexConsumer out;

    QuintetWitchVisuals(PoseStack.Pose pose, VertexConsumer out) {
        this.pose = pose;
        this.out = out;
    }

    void cast(MadokaWitchEntity witch, WitchCast cast, double time, double runner) {
        if (time > cast.end()) {
            return;
        }
        boolean striking = time >= cast.strike();
        double radius = cast.radius();
        switch (cast.kind()) {
            case ARROW_RAIN -> {
                ring(Vec3.ZERO, radius, 0.12D, WHITE, 162);
                ring(Vec3.ZERO, radius - 0.32D, 0.055D, PINK, 108);
            }
            case HEAVEN -> {
                int opacity = striking ? 230 : 115;
                ring(Vec3.ZERO, radius, 0.14D, PINK, opacity);
                ring(Vec3.ZERO, 2.25D, 0.13D, WHITE, opacity);
                for (int i = 0; i < 16; i++) {
                    double angle = TURN * i / 16 + time * 0.012D;
                    double distance = radius - 1.0D - (time * 0.24D + i * 0.96D) % (radius - 3.25D);
                    line(radial(angle, distance), radial(angle, Math.min(radius, distance + 2.5D)),
                            0.055D, PINK, opacity);
                }
                for (var marker : cast.markers()) {
                    var point = Vec3.atBottomCenterOf(marker).subtract(Vec3.atBottomCenterOf(cast.center()));
                    ring(point, cast.markerRadius(), 0.17D, SHELTER, 245);
                    ring(point, cast.markerRadius() - 0.28D, 0.055D, WHITE, 170);
                    star(point, cast.markerRadius() * 0.72D, 4, 0, SHELTER, 220);
                }
                if (striking) {
                    double fade = (cast.end() - time) / (cast.end() - cast.strike());
                    beam(Vec3.ZERO, 2.3D, fade);
                    ring(Vec3.ZERO, radius * (0.32D + 0.68D * (1.0D - fade)), 0.40D, PINK, (int) (200 * fade));
                }
            }
            case REDEMPTION -> {
                double progress = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0, 1);
                for (var marker : cast.markers()) {
                    var point = Vec3.atBottomCenterOf(marker).subtract(Vec3.atBottomCenterOf(cast.center()));
                    ring(point, cast.markerRadius(), 0.13D, PINK, 230);
                    if (striking) {
                        beam(point, cast.markerRadius(), (cast.end() - time) / (cast.end() - cast.strike()));
                    } else {
                        double inner = Mth.lerp(progress, cast.markerRadius() * 0.84D, 0.22D);
                        ring(point, inner, 0.08D, WHITE, (int) (100 + 135 * progress));
                        star(point, cast.markerRadius() * 0.63D, 4, Math.PI / 4, PINK, 175);
                        for (int i = 0; i < 4; i++) {
                            double angle = TURN * (i + 0.5D) / 4;
                            Vec3 foot = point.add(radial(angle, cast.markerRadius() * 0.88D));
                            Vec3 tip = foot.add(0, 0.55D + 0.6D * progress, 0);
                            beamWing(foot, tip, radial(angle, 0.1D).multiply(1, 0, 1), WHITE, 182);
                            beamWing(foot, tip, radial(angle + Math.PI, 0.1D).multiply(1, 0, 1), PINK, 182);
                        }
                    }
                }
            }
            case PROCESSION -> procession(cast, time, striking);
            case WHEEL_WAVE -> wheels(cast, time, striking);
            case MERMAID_SWEEP -> swordSweep(cast, time, striking);
            case MERMAID_NOTES -> {
                double duration = cast.strike() - cast.start();
                double progress = Mth.clamp((time - cast.start()) / duration, 0, 1);
                ring(Vec3.ZERO, 1.18D, 0.10D, SEA_BLUE, 195);
                if (!striking) {
                    ring(Vec3.ZERO, Mth.lerp(progress, 1.64D, 1.18D), 0.05D, FOAM, 175);
                    for (int lane = 0; lane < WitchCast.MERMAID_NOTE_DIRECTIONS; lane++) {
                        Vec3 direction = WitchCast.noteDirection(lane);
                        Vec3 base = direction.scale(1.18D).add(0, FLOOR, 0);
                        Vec3 tip = direction.scale(radius).add(0, FLOOR, 0);
                        line(base, tip, 0.068D, SEA_BLUE, 178);
                        Vec3 side = new Vec3(direction.z, 0, -direction.x);
                        Vec3 chevron = tip.subtract(direction.scale(0.36D));
                        line(chevron.add(side.scale(0.22D)), tip, 0.062D, FOAM, 225);
                        line(chevron.subtract(side.scale(0.22D)), tip, 0.062D, FOAM, 225);
                    }
                } else {
                    ring(Vec3.ZERO, 1.34D + 0.13D * Math.sin((time - cast.strike()) * 0.38D),
                            0.065D, FOAM, 120);
                }
            }
            case SALVATION_CROSS -> cross(witch, cast, time, striking);
            case OPHELIA_DASH -> dash(cast, striking, runner);
            case ATONEMENT_LINE -> atonementLine(witch, cast, time, striking);
            case ATONEMENT_SUMMON -> { }
            case MERCY_WAVE -> mercyWave(cast, time, striking);
            case CANDELORO_CAKES -> {
                for (var marker : cast.markers()) {
                    Vec3 point = Vec3.atBottomCenterOf(marker).subtract(cast.origin());
                    if (!striking) {
                        ring(point, 0.94D, 0.08D, GOLD, 210);
                        star(point, 0.74D, 5, time * 0.05D, PINK, 188);
                    }
                }
            }
            case CANDELORO_CUTLERY -> {
                if (!striking) {
                    for (int lane = 0; lane < WitchCast.CANDELORO_FORKS; lane++) {
                        cutleryWarning(witch, cast, lane, false);
                    }
                }
                if (time < cast.strike() + WitchCast.CANDELORO_KNIFE_DELAY) {
                    cutleryWarning(witch, cast, 0, true);
                }
            }
            case CANDELORO_TEA -> { }
            case NIGHT_LIGHTNING -> {
                double progress = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0, 1);
                for (var marker : cast.markers()) {
                    Vec3 point = Vec3.atBottomCenterOf(marker).subtract(cast.origin());
                    ring(point, cast.markerRadius(), 0.16D, VIOLET, striking ? 180 : 225);
                    if (!striking) {
                        ring(point, Mth.lerp(progress, cast.markerRadius() * 0.92D, 0.25D), 0.065D, WHITE, 205);
                        star(point, cast.markerRadius() * 0.55D, 4, 0, GOLD, 174);
                    }
                }
            }
        }
    }

    void dessertWarning(double radius, double time) {
        ring(Vec3.ZERO, radius, 0.075D, GOLD, 202);
        ring(Vec3.ZERO, radius - 0.1D, 0.20D, PINK, 80);
        star(Vec3.ZERO, radius * 0.68D, 5, time * 0.04D, GOLD, 150);
    }

    void mourningSkullOrbit(double radius, double progress) {
        int glow = (int) Mth.lerp(progress, 145, 245);
        ring(Vec3.ZERO, radius, 0.10D, VIOLET, glow);
        ring(Vec3.ZERO, radius - 0.12D, 0.035D, FOAM, glow * 2 / 3);
        star(Vec3.ZERO, 0.43D, 4, Math.PI / 4, FOAM, glow);
    }

    private void cutleryWarning(MadokaWitchEntity witch, WitchCast cast, int lane, boolean knife) {
        Vec3 direction = cast.cutleryDirection(lane, knife);
        Vec3 start = cast.cutleryStart(direction);
        Vec3 far = start.add(direction.scale(cast.radius() - WitchCast.CANDELORO_MUZZLE));
        Vec3 hit = witch.level().clip(new net.minecraft.world.level.ClipContext(start, far,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, witch))
                .getLocation().subtract(cast.origin());
        Vec3 muzzle = start.subtract(cast.origin());
        line(new Vec3(muzzle.x, FLOOR, muzzle.z), new Vec3(hit.x, FLOOR, hit.z),
                knife ? 0.38 : 0.21, knife ? PINK : FOAM, knife ? 180 : 145);
    }

    private void mercyWave(WitchCast cast, double time, boolean expanding) {
        if (!expanding) {
            double charge = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0, 1);
            ring(Vec3.ZERO, 2.3D, 0.11D, GOLD, 180);
            star(Vec3.ZERO, 1.32D, 4, 0, PINK, 210);
            orb(new Vec3(0, 0.95D, 0), 0.23D + charge * 0.55D, WHITE, (int) (95 + charge * 125));
            return;
        }
        double radius = cast.mercyRadiusAt(time);
        if (radius < 0.05D) {
            return;
        }
        double height = WitchCast.MERCY_WAVE_HEIGHT;
        Vec3 middle = new Vec3(0, height * 0.58D, 0);
        Vec3 top = new Vec3(0, height - FLOOR, 0);
        ring(Vec3.ZERO, radius, Math.min(radius * 0.65D, 0.42D), GOLD, 220);
        ring(middle, radius, Math.min(radius * 0.65D, 0.20D), WHITE, 170);
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            Vec3 a = radial(TURN * i / segments, radius);
            Vec3 b = radial(TURN * (i + 1) / segments, radius);
            quad(a, b, b.add(middle), a.add(middle), GOLD, 82);
            quad(a.add(middle), b.add(middle), b.add(top), a.add(top), PINK, 39);
        }
    }

    private void cross(MadokaWitchEntity witch, WitchCast cast, double time, boolean striking) {
        double charge = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0.0D, 1.0D);
        double fade = 1.0D - (time - cast.strike()) / (cast.end() - cast.strike());
        double strength = striking ? Mth.clamp(fade, 0.0D, 1.0D) : charge;
        Vec3 source = new Vec3(0, WitchCast.CROSS_BEAM_Y, 0);
        ring(Vec3.ZERO, 1.25D + 0.35D * strength, 0.07D, PINK, 195);
        orb(source, (striking ? 0.82D : 0.24D) + 0.45D * strength, PINK, (int) (95 + 115 * strength));
        orb(source, (striking ? 0.35D : 0.11D) + 0.22D * strength, WHITE, (int) (145 + 100 * strength));
        for (int arm = 0; arm < 4; arm++) {
            Vec3 end = cast.crossEnd(witch.level(), witch, arm).subtract(cast.origin());
            Vec3 groundEnd = new Vec3(end.x, FLOOR, end.z);
            Vec3 groundStart = new Vec3(0, FLOOR, 0);
            double width = cast.markerRadius();
            line(groundStart, groundEnd, 2 * width, PINK, striking ? (int) (138 * strength) : 43);
            Vec3 edge = new Vec3(end.z, 0, -end.x).normalize().scale(width);
            line(groundStart.add(edge), groundEnd.add(edge), 0.07D, PINK, 168);
            line(groundStart.subtract(edge), groundEnd.subtract(edge), 0.07D, PINK, 168);
            if (striking) {
                ray(source, end, width * strength, strength, PINK, WHITE);
            } else {
                Vec3 spark = end.lerp(source, charge);
                ray(spark, spark.lerp(source, 0.15D), 0.085D + charge * 0.14D, 0.6D, PINK, WHITE);
            }
        }
    }

    private void atonementLine(MadokaWitchEntity witch, WitchCast cast, double time, boolean striking) {
        double charge = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0, 1);
        double flash = Mth.clamp((cast.end() - time) / (cast.end() - cast.strike()), 0, 1);
        double width = cast.markerRadius();
        Vec3 center = cast.rayOrigin().subtract(cast.origin());
        Vec3 offset = cast.castPoint(width, 0, 0);
        for (int arm : new int[]{0, 2}) {
            Vec3 tip = cast.crossEnd(witch.level(), witch, arm).subtract(cast.origin());
            Vec3 ground = new Vec3(tip.x, FLOOR * 1.6D, tip.z);
            Vec3 start = new Vec3(0, FLOOR * 1.6D, 0);
            line(start, ground, width * 2, SEA_BLUE, striking ? (int) (flash * 165) : (int) (48 + 35 * charge));
            line(start.add(offset), ground.add(offset), 0.09D, FOAM, 218);
            line(start.subtract(offset), ground.subtract(offset), 0.09D, FOAM, 218);
            for (double step = 2; step < ground.horizontalDistance() - 0.4D; step += 2.75D) {
                Vec3 mark = start.lerp(ground, step / ground.horizontalDistance());
                Vec3 along = ground.subtract(start).normalize();
                line(mark.add(offset.scale(.48D)), mark.add(along.scale(.70D)), 0.09D, FOAM, 190);
                line(mark.subtract(offset.scale(.48D)), mark.add(along.scale(.70D)), 0.09D, FOAM, 190);
            }
            if (striking) {
                ray(center, tip, width * flash, flash, SEA_BLUE, FOAM);
            } else {
                Vec3 spark = tip.lerp(center, charge);
                ray(spark, spark.lerp(center, .20D), .10D + charge * .19D, .68D, SEA_BLUE, FOAM);
            }
        }
    }

    private void ray(Vec3 start, Vec3 end, double width, double opacity, int outer, int core) {
        Vec3 axis = end.subtract(start);
        if (width < 0.01D || axis.lengthSqr() < 0.0004D) {
            return;
        }
        Vec3 unit = axis.normalize();
        Vec3 side = new Vec3(unit.z, 0, -unit.x);
        for (int face = 0; face < 4; face++) {
            double angle = face * TURN / 8;
            Vec3 wing = side.scale(Math.cos(angle) * width).add(0, Math.sin(angle) * width * 0.62D, 0);
            beamWing(start, end, wing, outer, (int) (130 * opacity));
            beamWing(start, end, wing.reverse(), outer, (int) (130 * opacity));
            beamWing(start, end, wing.scale(0.34D), core, (int) (240 * opacity));
            beamWing(start, end, wing.scale(-0.34D), core, (int) (240 * opacity));
        }
    }

    private void orb(Vec3 center, double radius, int color, int opacity) {
        int sides = 12;
        int rings = 6;
        for (int i = 0; i < sides; i++) {
            double a = i * TURN / sides;
            double b = (i + 1) * TURN / sides;
            for (int band = 0; band < rings; band++) {
                double bottom = (band / (double) rings - 0.5D) * Math.PI;
                double top = ((band + 1) / (double) rings - 0.5D) * Math.PI;
                quad(orbPoint(center, radius, a, bottom), orbPoint(center, radius, b, bottom),
                        orbPoint(center, radius, b, top), orbPoint(center, radius, a, top), color, opacity);
            }
        }
    }

    private static Vec3 orbPoint(Vec3 center, double radius, double yaw, double pitch) {
        double horizontal = radius * Math.cos(pitch);
        return center.add(Math.cos(yaw) * horizontal, radius * Math.sin(pitch), Math.sin(yaw) * horizontal);
    }

    private void dash(WitchCast cast, boolean striking, double runner) {
        double tail = Math.max(0, runner - cast.dashDistance(cast.strike() + 5));
        for (int side = -1; side <= 1; side++) {
            if (side != 0 && !cast.hasDecoy(side)) {
                continue;
            }
            double across = side * WitchCast.OPHELIA_DECOY_OFFSET;
            double halfWidth = cast.markerRadius();
            int color = side == 0 ? EMBER : VIOLET;
            Vec3 origin = cast.castPoint(across, FLOOR, striking ? tail : 0);
            Vec3 tip = cast.castPoint(across, FLOOR, striking ? runner : cast.radius());
            Vec3 offset = cast.castPoint(halfWidth, 0, 0);
            quad(origin.add(offset), tip.add(offset), tip.subtract(offset), origin.subtract(offset), color, striking ? 62 : 39);
            line(origin.add(offset), tip.add(offset), 0.1D, color, 210);
            line(origin.subtract(offset), tip.subtract(offset), 0.1D, color, 210);
            if (striking) {
                Vec3 front = cast.castPoint(across, 0.9D, runner);
                Vec3 back = cast.castPoint(across, 0.25D, tail);
                beamWing(front, back, offset.scale(0.7D), color, 190);
                beamWing(front, back, offset.scale(-0.7D), color, 190);
            } else {
                for (double place = 2; place + 1 < cast.radius(); place += 3) {
                    Vec3 arrow = cast.castPoint(across, FLOOR, place + 0.75D);
                    line(cast.castPoint(across - halfWidth * 0.55D, FLOOR, place), arrow, 0.10D, color, 180);
                    line(cast.castPoint(across + halfWidth * 0.55D, FLOOR, place), arrow, 0.10D, color, 180);
                }
            }
            if (side == 0 && striking) {
                line(tip.subtract(offset), tip.add(offset), 0.12D, GOLD, 223);
            }
        }
    }
    private void wheels(WitchCast cast, double time, boolean striking) {
        double radius = cast.radius();
        double row = cast.rowAt(time);
        for (int lane = 0; lane < WitchCast.LANES; lane++) {
            if (!cast.hasWheel(lane)) {
                continue;
            }
            double center = cast.laneCenter(lane);
            double cueWidth = WitchCast.WHEEL_HALF_WIDTH + 0.30D;
            strip(cast, center - cueWidth, center + cueWidth, striking ? row : -radius, radius,
                    SEA_BLUE, striking ? 42 : 91);
            if (!striking) {
                double progress = Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0.0D, 1.0D);
                double pulse = Mth.lerp(progress, -radius, radius);
                strip(cast, center - cueWidth, center + cueWidth, pulse, Math.min(radius, pulse + 0.9D), FOAM, 210);
            } else {
                ring(cast.rowPoint(center, 0, row - 0.47D), 0.54D, 0.072D, FOAM, 140);
            }
        }
    }

    private void swordSweep(WitchCast cast, double time, boolean striking) {
        final int segments = 32;
        double age = time - cast.strike();
        if (striking && age >= 9) {
            return;
        }
        float angle = WitchCast.MERMAID_SWEEP_HALF_ANGLE;
        double radius = cast.radius();
        Vec3 floor = new Vec3(0, FLOOR, 0);
        double flash = striking ? 1.0D - age / 9.0D : 0.0D;
        for (int i = 0; i < segments; i++) {
            double u = i / (double) segments;
            double v = (i + 1) / (double) segments;
            float a = Mth.lerp((float) u, -angle, angle);
            float b = Mth.lerp((float) v, -angle, angle);
            Vec3 outerA = floor.add(cast.sweepPoint(a, radius));
            Vec3 outerB = floor.add(cast.sweepPoint(b, radius));
            Vec3 innerA = floor.add(cast.sweepPoint(a, radius - 0.13D));
            Vec3 innerB = floor.add(cast.sweepPoint(b, radius - 0.13D));
            quad(floor, innerB, innerA, floor, SEA_BLUE, striking ? (int) (92 * flash) : 48);
            quad(innerA, innerB, outerB, outerA, FOAM, striking ? (int) (230 * flash) : 195);
            if (striking) {
                double outer = radius * (0.63D + 0.37D * age / 9.0D);
                Vec3 air = new Vec3(0, 1.08D + age * 0.035D, 0);
                Vec3 edgeA = air.add(cast.sweepPoint(a, outer));
                Vec3 edgeB = air.add(cast.sweepPoint(b, outer));
                Vec3 trailA = air.add(cast.sweepPoint(a, outer - Math.sin(Math.PI * u) * 1.05D));
                Vec3 trailB = air.add(cast.sweepPoint(b, outer - Math.sin(Math.PI * v) * 1.05D));
                quad(trailA, trailB, edgeB, edgeA, SEA_BLUE, (int) (184 * flash));
                quad(edgeA, edgeB, edgeB.add(0, 0.13D, 0), edgeA.add(0, 0.13D, 0), FOAM, (int) (242 * flash));
            }
        }
        if (!striking) {
            line(floor, floor.add(cast.sweepPoint(-angle, radius)), 0.08D, FOAM, 184);
            line(floor, floor.add(cast.sweepPoint(angle, radius)), 0.08D, FOAM, 184);
        }
    }

    void curse(double radius, double time) {
        ring(Vec3.ZERO, radius, 0.17D, VIOLET, 220);
        ring(Vec3.ZERO, radius * 0.91D, 0.065D, VIOLET, 120);
        ring(Vec3.ZERO, radius * 0.45D, 0.08D, VIOLET, 130);
        star(Vec3.ZERO, radius * 0.75D, 8, time * 0.004D, VIOLET, 108);
        for (int i = 0; i < 24; i++) {
            double angle = TURN * i / 24;
            line(radial(angle, radius * 0.93D), radial(angle, radius * 0.98D), 0.12D, VIOLET, 198);
        }
    }

    private void procession(WitchCast cast, double time, boolean striking) {
        double radius = cast.radius();
        double row = cast.rowAt(time);
        for (int lane = 0; lane < WitchCast.LANES; lane++) {
            double left = cast.laneEdge(lane);
            double right = cast.laneEdge(lane + 1);
            if (lane == cast.gap()) {
                strip(cast, left + 0.13D, left + 0.25D, -radius, radius, SHELTER, 222);
                strip(cast, right - 0.25D, right - 0.13D, -radius, radius, SHELTER, 222);
            } else if (striking) {
                strip(cast, left, right, row - WitchCast.PROCESSION_DEPTH,
                        row + WitchCast.PROCESSION_DEPTH, RED, 145);
            } else {
                strip(cast, left, right, -radius, radius, RED, 44);
                strip(cast, left, right, -radius, -radius + 0.24D, RED, 205);
            }
        }
        guillotine(cast, time);
    }

    private void guillotine(WitchCast cast, double time) {
        double end = cast.radius() + 0.14D;
        upright(cast, -1.65D, -1.48D, 0.02D, 3.85D, end, VIOLET, 220);
        upright(cast, 1.48D, 1.65D, 0.02D, 3.85D, end, VIOLET, 220);
        upright(cast, -1.65D, 1.65D, 3.67D, 3.85D, end, VIOLET, 220);
        double height = 2.8D - 2.18D * Mth.clamp(1.0D - (cast.end() - time) / 8.0D, 0, 1);
        quad(cast.rowPoint(-1.48D, height, end), cast.rowPoint(1.48D, height + 0.42D, end),
                cast.rowPoint(1.48D, height + 0.68D, end), cast.rowPoint(-1.48D, height + 0.68D, end), RED, 213);
    }

    private void upright(WitchCast cast, double left, double right, double bottom, double top,
                         double along, int color, int opacity) {
        quad(cast.rowPoint(left, bottom, along), cast.rowPoint(right, bottom, along),
                cast.rowPoint(right, top, along), cast.rowPoint(left, top, along), color, opacity);
    }

    private void strip(WitchCast cast, double left, double right, double from, double to, int color, int opacity) {
        quad(cast.rowPoint(left, FLOOR, from), cast.rowPoint(left, FLOOR, to),
                cast.rowPoint(right, FLOOR, to), cast.rowPoint(right, FLOOR, from), color, opacity);
    }

    private void beam(Vec3 base, double radius, double fade) {
        if (fade <= 0) {
            return;
        }
        double height = WitchCast.BEAM_HEIGHT;
        double width = radius * (0.6D + 0.4D * fade);
        Vec3 bottom = base.add(0, FLOOR, 0);
        Vec3 top = base.add(0, height, 0);
        for (int i = 0; i < 4; i++) {
            Vec3 wing = radial(i * TURN / 8, width).multiply(1, 0, 1);
            beamWing(bottom, top, wing, PINK, (int) (176 * fade));
            beamWing(bottom, top, wing.scale(-1), PINK, (int) (176 * fade));
            beamWing(bottom, top, wing.scale(0.29D), WHITE, (int) (225 * fade));
            beamWing(bottom, top, wing.scale(-0.29D), WHITE, (int) (225 * fade));
        }
    }

    private void beamWing(Vec3 bottom, Vec3 top, Vec3 wing, int color, int alpha) {
        vertex(bottom, color, alpha);
        vertex(bottom.add(wing), color, 0);
        vertex(top.add(wing), color, 0);
        vertex(top, color, alpha / 3);
    }

    private void ring(Vec3 origin, double radius, double width, int color, int opacity) {
        int sides = radius >= 6.0D ? 72 : 32;
        for (int i = 0; i < sides; i++) {
            double a = TURN * i / sides, b = TURN * (i + 1) / sides;
            quad(origin.add(radial(a, radius - width)), origin.add(radial(b, radius - width)),
                    origin.add(radial(b, radius)), origin.add(radial(a, radius)), color, opacity);
        }
    }

    private void star(Vec3 origin, double radius, int points, double rotation, int color, int opacity) {
        for (int i = 0; i < points * 2; i++) {
            double a = rotation + TURN * i / (points * 2);
            double b = rotation + TURN * (i + 1) / (points * 2);
            double inner = radius * 0.43D;
            line(origin.add(radial(a, i % 2 == 0 ? radius : inner)),
                    origin.add(radial(b, i % 2 == 0 ? inner : radius)), 0.09D, color, opacity);
        }
    }

    private static Vec3 radial(double angle, double radius) {
        return new Vec3(Math.cos(angle) * radius, FLOOR, Math.sin(angle) * radius);
    }

    private void line(Vec3 a, Vec3 b, double width, int color, int opacity) {
        Vec3 side = new Vec3(b.z - a.z, 0, a.x - b.x).normalize().scale(width * 0.5D);
        quad(a.add(side), b.add(side), b.subtract(side), a.subtract(side), color, opacity);
    }

    private void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int opacity) {
        vertex(a, color, opacity);
        vertex(b, color, opacity);
        vertex(c, color, opacity);
        vertex(d, color, opacity);
    }

    private void vertex(Vec3 point, int color, int opacity) {
        out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, opacity)
                .uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
