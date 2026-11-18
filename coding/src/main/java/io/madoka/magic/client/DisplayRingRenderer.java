package io.madoka.magic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.block.DisplayRingBlockEntity;
import io.madoka.magic.client.renderer.DisplayRingVisuals;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

public final class DisplayRingRenderer implements BlockEntityRenderer<DisplayRingBlockEntity> {
    private static final ResourceLocation METAL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/block/display_ring_bands.png");
    private static final int SEGMENTS = 24;
    private static final int LOOP_TICKS = 8_400;
    private static final double CENTER_Y = 1.29;
    private static final Band[] BANDS = {
            new Band(6.68F, 0.74F, 0, 22, 0, 210, 240),
            new Band(5.39F, 0.58F, 8, 71, 65, 280, -300),
            new Band(4.18F, 0.44F, 16, 113, 147, -350, 200)
    };

    private record Band(float radius, float width, int textureRow, float tilt, float angle,
                        int precessionPeriod, int spinPeriod) {}

    private final ItemRenderer itemRenderer;
    private final ModelPart[] rings = new ModelPart[BANDS.length];

    public DisplayRingRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
        for (int i = 0; i < BANDS.length; i++) rings[i] = bakeRing(BANDS[i]);
    }

    private static ModelPart bakeRing(Band band) {
        var mesh = new MeshDefinition();
        float segmentLength = (float) (band.radius() * 2 * Math.tan(Math.PI / SEGMENTS)) + 0.15F;
        for (int i = 0; i < SEGMENTS; i++) {
            var cubes = CubeListBuilder.create().texOffs(0, band.textureRow())
                    .addBox(-segmentLength / 2, -0.21F, band.radius() - band.width() / 2,
                            segmentLength, 0.42F, band.width());
            if (i % 6 == 0) {
                cubes.texOffs(16, band.textureRow())
                        .addBox(-0.75F, -0.36F, band.radius() - band.width() / 2 - 0.12F,
                                1.5F, 0.72F, band.width() + 0.24F);
            }
            float angle = (float) (i * Math.PI * 2 / SEGMENTS);
            mesh.getRoot().addOrReplaceChild("segment_" + i, cubes, PartPose.rotation(0, angle, 0));
        }
        return LayerDefinition.create(mesh, 32, 24).bakeRoot();
    }

    @Override
    public void render(DisplayRingBlockEntity display, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        var level = display.getLevel();
        if (level == null) return;
        var pos = display.getBlockPos();
        double ticks = Math.floorMod(level.getGameTime() + (pos.asLong() & 255), LOOP_TICKS) + partialTick;
        int ambient = LevelRenderer.getLightColor(level, pos.above());
        int bandLight = withMinimumBlockLight(ambient, 5);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));

        pose.pushPose();
        pose.translate(0.5, CENTER_Y, 0.5);
        for (int i = 0; i < BANDS.length; i++) {
            Band band = BANDS[i];
            pose.pushPose();
            pose.scale(2.0F, 2.0F, 2.0F);
            pose.mulPose(Axis.YP.rotationDegrees(band.angle() + degrees(ticks, band.precessionPeriod())));
            pose.mulPose(Axis.ZP.rotationDegrees(band.tilt()));
            pose.mulPose(Axis.YP.rotationDegrees(degrees(ticks, band.spinPeriod())));
            rings[i].render(pose, vertices, bandLight, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        var stack = display.getDisplayedItem();
        if (!stack.isEmpty()) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(degrees(ticks, 200)));
            pose.scale(0.78F, 0.78F, 0.78F);
            // GROUND translates generated item models upward. NONE keeps them on the rings' shared pivot.
            itemRenderer.renderStatic(stack, ItemDisplayContext.NONE, withMinimumBlockLight(ambient, 10),
                    overlay, pose, buffers, level, (int) pos.asLong());
            pose.popPose();
            DisplayRingVisuals.render(stack, pose, buffers, ticks);
        }
        pose.popPose();
    }

    private static float degrees(double ticks, int period) {
        return (float) ((ticks % period) * 360 / period);
    }

    private static int withMinimumBlockLight(int light, int minimum) {
        return LightTexture.pack(Math.max(minimum, LightTexture.block(light)), LightTexture.sky(light));
    }
}
