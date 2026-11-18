package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.entity.ChocolateBlockEntity;
import io.madoka.magic.entity.FrostingChessboardEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class AnnegretRenderers {
    private static final ResourceLocation TILE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID,
            "textures/particle/annegret_frosting_tile.png");
    private static final RenderType TILE = RenderType.entityTranslucentEmissive(TILE_TEXTURE);

    private AnnegretRenderers() {}

    public static final class Chessboard extends EntityRenderer<FrostingChessboardEntity> {
        public Chessboard(EntityRendererProvider.Context context) { super(context); }

        @Override
        public void render(FrostingChessboardEntity board, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            long age = board.age();
            int wave = (int) (age / FrostingChessboardEntity.WAVE_TICKS);
            if (wave >= FrostingChessboardEntity.WAVES) return;
            float time = age % FrostingChessboardEntity.WAVE_TICKS + partialTick;
            int warning = FrostingChessboardEntity.WARNING;
            float opacity = time < warning ? Mth.lerp(time / warning, 0.58F, 0.98F)
                    : Mth.clamp(1 - (time - warning) / (FrostingChessboardEntity.WAVE_TICKS - warning), 0, 1);
            VertexConsumer out = buffers.getBuffer(TILE);
            int size = FrostingChessboardEntity.TILE_SIZE;
            float half = (float) FrostingChessboardEntity.HALF_WIDTH;
            int green = time < warning ? 180 : 250;
            int alpha = Math.round(opacity * 255);
            for (int row = 0; row < FrostingChessboardEntity.TILES; row++) {
                for (int col = 0; col < FrostingChessboardEntity.TILES; col++) {
                    if (!FrostingChessboardEntity.dangerous(wave, col, row)) continue;
                    float x = col * size - half, z = row * size - half;
                    quadVertex(pose.last(), out, x, z, 0, 0, green, alpha);
                    quadVertex(pose.last(), out, x, z + size, 0, 1, green, alpha);
                    quadVertex(pose.last(), out, x + size, z + size, 1, 1, green, alpha);
                    quadVertex(pose.last(), out, x + size, z, 1, 0, green, alpha);
                }
            }
        }

        private static void quadVertex(PoseStack.Pose pose, VertexConsumer out, float x, float z,
                                       int u, int v, int green, int alpha) {
            out.vertex(pose.pose(), x, 0.034F, z).color(255, green, 223, alpha)
                    .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(), 0, 1, 0).endVertex();
        }

        @Override
        public ResourceLocation getTextureLocation(FrostingChessboardEntity entity) { return TILE_TEXTURE; }
    }

    public static final class Chocolate extends EntityRenderer<ChocolateBlockEntity> {
        public Chocolate(EntityRendererProvider.Context context) { super(context); }

        @Override
        public void render(ChocolateBlockEntity chocolate, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            double time = chocolate.age() + partialTick;
            if (!chocolate.feeding() && chocolate.blastRadius() > 0) {
                pose.pushPose();
                pose.translate(0, -0.15, 0);
                new QuintetWitchVisuals(pose.last(), buffers.getBuffer(WitchArenaVisuals.LAYER))
                        .dessertWarning(chocolate.blastRadius(), time);
                pose.popPose();
            }
            AlexCavesCompat.block("block_of_chocolate").ifPresent(block -> {
                pose.pushPose();
                pose.translate(0, 0.50 + Math.sin(time * 0.19) * 0.075, 0);
                pose.mulPose(Axis.YP.rotationDegrees((float) (time * (chocolate.feeding() ? 5 : 1.4))));
                pose.translate(-0.5, -0.5, -0.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block.defaultBlockState(), pose, buffers,
                        Math.max(light, LightTexture.pack(9, 0)), OverlayTexture.NO_OVERLAY);
                pose.popPose();
            });
        }

        @Override
        public ResourceLocation getTextureLocation(ChocolateBlockEntity entity) { return TextureAtlas.LOCATION_BLOCKS; }
    }
}
