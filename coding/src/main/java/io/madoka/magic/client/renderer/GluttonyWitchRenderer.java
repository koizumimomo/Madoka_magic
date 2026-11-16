package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.GluttonyWitchEntity;
import io.madoka.magic.entity.WitchTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class GluttonyWitchRenderer extends MobRenderer<GluttonyWitchEntity, SlimeModel<GluttonyWitchEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/gluttony_witch.png");
    static final ResourceLocation MORPH_ATLAS = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/gluttony_morph.png");
    private static final RenderType MORPH = RenderType.entityTranslucentEmissive(MORPH_ATLAS);
    private static final int MORPH_FRAMES = 36;

    public GluttonyWitchRenderer(EntityRendererProvider.Context context) {
        super(context, new SlimeModel<>(context.bakeLayer(ModelLayers.SLIME)), 2.0F);
        addLayer(new SlimeOuterLayer<>(this, context.getModelSet()));
    }

    @Override
    public void render(GluttonyWitchEntity witch, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        shadowRadius = witch.getBbWidth() * 0.48F;
        super.render(witch, yaw, partialTick, pose, buffers, light);
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        if (witch.isFragment() || client.player == null || witch.isInvisibleTo(client.player)
                || camera.getPosition().distanceToSqr(witch.position()) > 64.0D * 64.0D) {
            return;
        }
        var sprites = new RitualSprites(pose, buffers, camera);
        var style = RitualVisualStyle.forTheme(WitchTypes.GLUTTONY);
        double ticks = witch.tickCount + (double) partialTick;
        double height = witch.getBbHeight() * verticalScale(witch, partialTick);
        WitchAura.crown(sprites, style, 0.0D, height + 0.25D, 0.0D, ticks, 1.2D, 0.94D);
        if (camera.getPosition().distanceToSqr(witch.position()) < 32.0D * 32.0D) {
            var shapes = new RitualSprites(pose, buffers, camera, MORPH);
            for (int i = 0; i < 7; i++) {
                double angle = ticks * 0.035 + i * WitchAura.TURN / 7;
                int frame = morphFrame(ticks, i);
                double radius = 2.65 + 0.23 * Math.sin(angle * 2);
                double y = height * (0.48 + 0.20 * Math.sin(angle + i * 0.8));
                shapes.drawTile(frame, 6, 6, 32, 32, Math.cos(angle) * radius, y, Math.sin(angle) * radius,
                        0.33, 0.33, 0.0, 0xffffff, 0.86);
            }
        }
    }

    static int morphFrame(double ticks, int index) {
        return Math.floorMod((int) (ticks / 2.7D) + index * 3, MORPH_FRAMES);
    }

    @Override
    protected void scale(GluttonyWitchEntity witch, PoseStack pose, float partialTick) {
        float size = witch.getBbWidth() / 0.52F;
        float vertical = verticalScale(witch, partialTick);
        float horizontal = 1.0F / Mth.sqrt(vertical);
        pose.scale(size * horizontal, size * vertical, size * horizontal);
    }

    private static float verticalScale(GluttonyWitchEntity witch, float partialTick) {
        float progress = witch.phaseProgress(partialTick);
        return switch (witch.phase()) {
            case SWEEP -> Mth.lerp(progress, 1.0F, 0.35F);
            case LEAP -> Mth.lerp(progress, 1.0F, 1.62F);
            case AIRBORNE -> Mth.lerp(Math.min(progress * 3.0F, 1.0F), 1.62F, 1.0F);
            default -> witch.onGround() ? 1.0F : 1.08F;
        };
    }

    @Override
    public ResourceLocation getTextureLocation(GluttonyWitchEntity witch) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(GluttonyWitchEntity witch, BlockPos position) {
        return Math.max(6, super.getBlockLightLevel(witch, position));
    }
}
