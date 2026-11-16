package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.TeaStain;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class CandeloroVisuals {
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/candeloro_orbit.png"));
    private static final int MOTIFS = 3;
    private static final int PARTICLES = 6;
    static final RenderType TEA = RenderType.entityTranslucentEmissive(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/tea_stain.png"));

    private CandeloroVisuals() {}

    static void orbit(PoseStack pose, MultiBufferSource buffers, Camera camera, double ticks) {
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        for (int i = 0; i < PARTICLES; i++) {
            double angle = ticks * 0.042D + i * Math.PI * 2 / PARTICLES;
            double phase = Mth.frac(ticks / 96.0D + i * 0.19D);
            double radius = 2.1D + 0.20D * Math.sin(angle * 2);
            double height = 0.65D + 1.25D * phase + 0.13D * Math.sin(angle);
            double alpha = Math.min(1.0D, Math.sin(phase * Math.PI) * 1.8D);
            sprites.drawTile(i % MOTIFS, MOTIFS, 1, 32, 32, radius * Math.cos(angle), height, radius * Math.sin(angle),
                    0.28D, 0.28D, 0.22D * Math.sin(angle * 1.5D), 0xffffff, alpha);
        }
    }

    static void tea(PoseStack pose, MultiBufferSource buffers, WitchCast cast, double time) {
        if (time > cast.end()) {
            return;
        }
        boolean hot = time >= cast.strike();
        int opacity = 215 + (int) (14 * Math.sin(time * 0.22));
        var points = TeaStain.edge();
        var out = buffers.getBuffer(TEA);
        for (var marker : cast.markers()) {
            Vec3 origin = Vec3.atBottomCenterOf(marker).subtract(cast.origin()).add(0, 0.022, 0);
            float rotation = TeaStain.yaw(marker);
            for (int i = 0; i < points.size(); i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get((i + 1) % points.size());
                if (hot) {
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), Vec3.ZERO, opacity);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), b, opacity);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), a, opacity);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), a, opacity);
                } else {
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), a.scale(0.92), 165);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), b.scale(0.92), 165);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), b, 205);
                    vertex(out, pose.last(), origin, rotation, cast.markerRadius(), a, 205);
                }
            }
        }
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer out, PoseStack.Pose pose, Vec3 origin,
                               float yaw, double radius, Vec3 vertex, int alpha) {
        Vec3 point = origin.add(vertex.yRot(yaw).scale(radius));
        out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z)
                .color(255, 232, 205, alpha).uv((float) (0.5 + vertex.x * 0.485), (float) (0.5 + vertex.z * 0.485))
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
