package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MourningSkullOrbitEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class MourningSkullOrbitRenderer extends EntityRenderer<MourningSkullOrbitEntity> {
    private static final ResourceLocation SKULL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/wither_witch_invulnerable.png");
    private static final RenderType SKULL_LAYER = RenderType.entityCutoutNoCull(SKULL_TEXTURE);
    private static final RenderType EMBER_LAYER = RenderType.entityTranslucentEmissive(MadokaWitchRenderer.WITHER_EMBER_ATLAS);

    private final SkullModel skull;

    public MourningSkullOrbitRenderer(EntityRendererProvider.Context context) {
        super(context);
        skull = new SkullModel(context.bakeLayer(ModelLayers.WITHER_SKULL));
    }

    @Override
    public void render(MourningSkullOrbitEntity orbit, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        double age = orbit.age(partialTick);
        if (age >= MourningSkullOrbitEntity.TOTAL_TICKS) {
            return;
        }
        double progress = MourningSkullOrbitEntity.progress(age);
        new QuintetWitchVisuals(pose.last(), buffers.getBuffer(WitchArenaVisuals.LAYER))
                .mourningSkullOrbit(MourningSkullOrbitEntity.radius(age), progress);

        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        var embers = new RitualSprites(pose, buffers, camera, EMBER_LAYER);
        for (int i = 0; i < MourningSkullOrbitEntity.SKULLS; i++) {
            for (int tail = 1; tail <= 4; tail++) {
                double delay = tail * 1.8D;
                if (age > delay) {
                    var point = orbit.skullPosition(i, age - delay);
                    embers.drawTile(0, 1, 1, 16, 16, point.x, point.y + 0.22D, point.z,
                            0.15D - tail * 0.015D, 0.15D - tail * 0.015D, age * 0.15D,
                            0xffffff, 0.80D - tail * 0.14D);
                }
            }
        }

        var vertices = buffers.getBuffer(SKULL_LAYER);
        for (int i = 0; i < MourningSkullOrbitEntity.SKULLS; i++) {
            var point = orbit.skullPosition(i, age);
            var heading = point.subtract(orbit.skullPosition(i, Math.max(0, age - 0.5D)));
            float direction = (float) (Mth.atan2(heading.x, heading.z) * Mth.RAD_TO_DEG);
            float tilt = (float) (Mth.atan2(heading.y, heading.horizontalDistance()) * Mth.RAD_TO_DEG);
            float scale = (float) (1.08D + progress * 0.16D * Math.sin(age * 0.85D));
            pose.pushPose();
            pose.translate(point.x, point.y, point.z);
            pose.scale(-scale, -scale, scale);
            skull.setupAnim(0, direction, tilt);
            skull.renderToBuffer(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(MourningSkullOrbitEntity orbit) {
        return SKULL_TEXTURE;
    }
}
