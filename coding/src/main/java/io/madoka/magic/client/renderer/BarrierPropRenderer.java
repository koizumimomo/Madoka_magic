package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.BarrierPropEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 结界共用近景道具渲染器：按 variant 解析模型与贴图。 */
public final class BarrierPropRenderer extends GeoEntityRenderer<BarrierPropEntity> {
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/barrier_prop.animation.json");

    public BarrierPropRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(BarrierPropEntity prop) {
                return ResourceLocation.fromNamespaceAndPath(
                        MadokaMagic.MODID, "geo/" + prop.getVariant() + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(BarrierPropEntity prop) {
                return ResourceLocation.fromNamespaceAndPath(
                        MadokaMagic.MODID, "textures/entity/" + prop.getVariant() + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(BarrierPropEntity prop) {
                return ANIMATION;
            }
        });
        shadowRadius = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(BarrierPropEntity prop, BlockPos pos) {
        int floor = "signpost".equals(prop.getVariant()) ? 9 : 3;
        return Math.max(floor, super.getBlockLightLevel(prop, pos));
    }

    /** 在偏航之后叠加俯仰角：倾斜宴会桌等需要歪着放的道具。 */
    @Override
    public void render(BarrierPropEntity prop, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light) {
        if (prop.getPitch() == 0.0F) {
            super.render(prop, yaw, partialTick, poseStack, buffers, light);
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(prop.getPitch()));
        super.render(prop, yaw, partialTick, poseStack, buffers, light);
        poseStack.popPose();
    }
}
