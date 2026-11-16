package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.entity.RainThornEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class RainThornRenderer extends EntityRenderer<RainThornEntity> {
    public RainThornRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RainThornEntity thorn, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        WitchSkillVisuals.node(pose, buffers, camera, thorn.getBbHeight(), thorn.tickCount + (double) partialTick + thorn.getId());
        super.render(thorn, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(RainThornEntity thorn) {
        return WitchSkillVisuals.TEXTURE;
    }
}
