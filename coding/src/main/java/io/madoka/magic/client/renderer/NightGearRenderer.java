package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.NightGearEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class NightGearRenderer extends EntityRenderer<NightGearEntity> {
    public static final ModelLayerLocation MODEL = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "night_gear"), "main");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/night_gear.png");
    private final ModelPart gear;

    public NightGearRenderer(EntityRendererProvider.Context context) {
        super(context);
        gear = context.bakeLayer(MODEL).getChild("gear_small");
        shadowRadius = 0.45F;
    }

    public static LayerDefinition createLayer() {
        // Keep the Blockbench export's original dimensions and eight rotations.
        var mesh = new MeshDefinition();
        var hub = CubeListBuilder.create().texOffs(0, 32).addBox(-5.4F, -3.9F, -5.4F, 10.8F, 7.8F, 10.8F);
        var part = mesh.getRoot().addOrReplaceChild("gear_small", hub,
                PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, Mth.HALF_PI));
        for (int i = 0; i < 8; i++) {
            var tooth = CubeListBuilder.create().texOffs(48, 32).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F);
            if (i < 4) {
                tooth.texOffs(0, 0).addBox(-12.6F, -3.3F, -5.418F, 25.2F, 6.6F, 10.836F);
            }
            part.addOrReplaceChild("tooth_" + i, tooth, PartPose.rotation(0, -i * Mth.PI / 4, 0));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(NightGearEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        float time = entity.tickCount + partialTick;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 58 + 16 * Mth.sin(time * 0.13F)));
        pose.mulPose(Axis.ZP.rotationDegrees(12 * Mth.sin(time * 0.19F)));
        pose.mulPose(Axis.XP.rotationDegrees(time * 23));
        pose.translate(0.0D, 0.175D, 0.0D);
        gear.render(pose, buffers.getBuffer(RenderType.entityCutout(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(NightGearEntity entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(NightGearEntity entity, BlockPos position) {
        return Math.max(7, super.getBlockLightLevel(entity, position));
    }
}
