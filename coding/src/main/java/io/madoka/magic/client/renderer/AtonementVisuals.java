package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class AtonementVisuals {
    static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/particle/atonement_fx.png");
    private static final RenderType SPRITES = RenderType.entityTranslucentEmissive(ATLAS);
    static final RenderType CIRCLES = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/particle/atonement_circle.png"));
    private static final int SHARDS = 3;
    private static final int FLOWER = 3;
    private static final int COLUMNS = 4;
    private static final double TURN = Math.PI * 2.0D;

    private AtonementVisuals() {}

    static void frame(MadokaWitchEntity witch, PoseStack pose, MultiBufferSource buffers, Camera camera, double time) {
        var sprites = new RitualSprites(pose, buffers, camera, SPRITES);
        for (int i = 0; i < 7; i++) {
            double angle = TURN * i / 7 + time * (i % 2 == 0 ? .036D : -.027D);
            double radius = 1.15D + 0.37D * Math.sin(time * .046D + i * 2.0D);
            double y = 1.95D + .86D * Math.sin(time * .045D + i * 1.7D);
            sprites.drawTile(i % SHARDS, COLUMNS, 1, Math.cos(angle) * radius, y, Math.sin(angle) * radius,
                    .18D + (i % 3) * .065D, angle * .74D, 0xFFFFFF, .86D);
        }
        if (witch.getRainVisual() != 0 || witch.getQuintetCast() != null) {
            for (int i = 0; i < 6; i++) {
                double angle = TURN * i / 6 - time * .070D;
                double y = .77D + .48D * Math.sin(time * .065D + i * 1.6D);
                sprites.drawTile(FLOWER, COLUMNS, 1, Math.cos(angle) * 1.55D, y, Math.sin(angle) * 1.55D,
                        .34D, .37D * Math.sin(angle), 0xFFFFFF, .95D);
            }
        }
    }

    static void projectile(PoseStack pose, MultiBufferSource buffers, Camera camera, int id, double time) {
        new RitualSprites(pose, buffers, camera, SPRITES).drawTile(id % SHARDS, COLUMNS, 1,
                0, .10D, 0, .29D, time * .36D, 0xFFFFFF, 1.0D);
    }

    static void summons(PoseStack pose, MultiBufferSource buffers, WitchCast cast, double time) {
        boolean striking = time >= cast.strike();
        double progress = striking ? Mth.clamp((cast.end() - time) / (cast.end() - cast.strike()), 0, 1)
                : Mth.clamp((time - cast.start()) / (cast.strike() - cast.start()), 0, 1);
        var out = buffers.getBuffer(CIRCLES);
        for (var mark : cast.markers()) {
            circle(pose.last(), out, Vec3.atBottomCenterOf(mark).subtract(cast.origin()), cast.markerRadius(),
                    time * .024D, striking ? progress : .57D + .43D * progress);
        }
    }

    static void beneathFrame(PoseStack pose, MultiBufferSource buffers, double time) {
        circle(pose.last(), buffers.getBuffer(CIRCLES), Vec3.ZERO, 1.95D, -time * .018D, .82D);
    }

    private static void circle(PoseStack.Pose pose, VertexConsumer out, Vec3 center, double radius, double angle, double alpha) {
        if (alpha <= 0) {
            return;
        }
        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        int opacity = (int) (255 * alpha);
        corner(pose, out, center, -radius, -radius, cosine, sine, 0, 0, opacity);
        corner(pose, out, center, -radius, radius, cosine, sine, 0, 1, opacity);
        corner(pose, out, center, radius, radius, cosine, sine, 1, 1, opacity);
        corner(pose, out, center, radius, -radius, cosine, sine, 1, 0, opacity);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, Vec3 center, double x, double z,
                               double cos, double sin, float u, float v, int alpha) {
        out.vertex(pose.pose(), (float) (center.x + x * cos - z * sin), (float) (center.y + .044D),
                        (float) (center.z + x * sin + z * cos))
                .color(255, 255, 255, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 1, 0).endVertex();
    }
}
