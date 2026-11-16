package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class MermaidWheelModel {
    public static final ModelLayerLocation MODEL = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "mermaid_wheel"), "main");
    static final RenderType LAYER = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/entity/mermaid_wheel.png"));
    private static ModelPart wheel;

    private MermaidWheelModel() {}

    public static LayerDefinition createLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        // The supplied Blockbench model has eight identical rim pieces and eight spokes.
        for (int i = 0; i < 8; i++) {
            float spokeAngle = i * Mth.PI / 4.0F;
            int rimUv = i % 2 == 0 ? 16 : 32;
            int spokeUv = i % 2 == 0 ? 32 : 48;
            root.addOrReplaceChild("rim_" + i, CubeListBuilder.create().texOffs(0, rimUv)
                            .addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F),
                    PartPose.rotation(0, 0, spokeAngle + Mth.PI / 8.0F));
            root.addOrReplaceChild("spoke_" + i, CubeListBuilder.create().texOffs(0, spokeUv)
                            .addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F),
                    PartPose.rotation(0, 0, spokeAngle));
        }
        root.addOrReplaceChild("hub", CubeListBuilder.create()
                        .texOffs(32, 32).addBox(-1.65F, -1.65F, -1.7F, 3.3F, 3.3F, 3.4F)
                        .texOffs(32, 48).addBox(-2.15F, -2.15F, -2.0F, 4.3F, 4.3F, 0.75F)
                        .texOffs(32, 48).addBox(-2.15F, -2.15F, 1.25F, 4.3F, 4.3F, 0.75F)
                        .texOffs(48, 48).addBox(-0.65F, -0.65F, -2.2F, 1.3F, 1.3F, 4.4F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    static void render(WitchCast cast, double time, PoseStack pose, MultiBufferSource buffers) {
        if (time < cast.strike() || time > cast.end()) {
            return;
        }
        if (wheel == null) {
            wheel = Minecraft.getInstance().getEntityModels().bakeLayer(MODEL);
        }
        double distance = cast.rowAt(time);
        float rotation = (float) ((distance + cast.radius()) / WitchCast.WHEEL_RADIUS);
        var vertices = buffers.getBuffer(LAYER);
        for (int lane = 0; lane < WitchCast.LANES; lane++) {
            if (!cast.hasWheel(lane)) {
                continue;
            }
            var at = cast.rowPoint(cast.laneCenter(lane), WitchCast.WHEEL_RADIUS, distance);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(Axis.YP.rotationDegrees(90.0F * (cast.direction() + 1)));
            pose.mulPose(Axis.ZP.rotation(rotation));
            wheel.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
