package io.madoka.magic.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The same irregular silhouette clips the rendered tea and the damage check. */
public final class TeaStain {
    private static final List<Vec3> EDGE = makeEdge();

    private TeaStain() {}

    private static List<Vec3> makeEdge() {
        int samples = 48;
        var points = new ArrayList<Vec3>(samples);
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * i / samples;
            double reach = Math.min(1, 0.85 + 0.087 * Math.cos(3 * angle + 0.65)
                    + 0.071 * Math.sin(5 * angle) + 0.033 * Math.cos(9 * angle - 0.45));
            points.add(new Vec3(Math.cos(angle) * reach, 0, Math.sin(angle) * reach));
        }
        return List.copyOf(points);
    }

    public static List<Vec3> edge() {
        return EDGE;
    }

    public static float yaw(BlockPos center) {
        return (float) Math.floorMod(center.asLong() ^ (center.asLong() >>> 17), 360) * ((float) Math.PI / 180);
    }

    public static boolean contains(BlockPos center, double radius, Vec3 feet) {
        Vec3 offset = feet.subtract(Vec3.atBottomCenterOf(center));
        if (offset.y < -0.2 || offset.y > 0.42 || offset.horizontalDistanceSqr() > radius * radius) {
            return false;
        }
        Vec3 local = offset.yRot(-yaw(center)).scale(1.0 / radius);
        boolean inside = false;
        for (int i = 0, j = EDGE.size() - 1; i < EDGE.size(); j = i++) {
            Vec3 a = EDGE.get(i);
            Vec3 b = EDGE.get(j);
            if ((a.z > local.z) != (b.z > local.z)
                    && local.x < (b.x - a.x) * (local.z - a.z) / (b.z - a.z) + a.x) {
                inside = !inside;
            }
        }
        return inside;
    }
}
