package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.entity.CandeloroCutleryEntity;
import io.madoka.magic.entity.DessertFamiliarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;

public final class CandeloroCombatRenderers {
    private static final ResourceLocation CAKE = model("dessert_familiar");
    private static final ResourceLocation FORK = model("tea_fork");
    private static final ResourceLocation KNIFE = model("tea_knife");
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);

    private CandeloroCombatRenderers() {}

    private static ResourceLocation model(String name) {
        return ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "entity/" + name);
    }

    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(CAKE);
        event.register(FORK);
        event.register(KNIFE);
    }

    private static void draw(ResourceLocation name, PoseStack pose, MultiBufferSource buffers, int light) {
        var client = Minecraft.getInstance();
        var baked = client.getModelManager().getModel(name);
        client.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(LAYER), null,
                baked, 1, 1, 1, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    public static final class Dessert extends EntityRenderer<DessertFamiliarEntity> {
        public Dessert(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.45F;
        }

        @Override
        public void render(DessertFamiliarEntity cake, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            var client = Minecraft.getInstance();
            if (client.player == null || cake.isInvisibleTo(client.player)) {
                return;
            }
            boolean preparing = cake.phase() == DessertFamiliarEntity.WINDUP;
            if (preparing || cake.phase() == DessertFamiliarEntity.AIR) {
                int ground = cake.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cake.getBlockX(), cake.getBlockZ());
                pose.pushPose();
                pose.translate(0, ground - cake.getPosition(partialTick).y, 0);
                new QuintetWitchVisuals(pose.last(), buffers.getBuffer(WitchArenaVisuals.LAYER))
                        .dessertWarning(MadokaConfig.CANDELORO_CAKE_RADIUS.get(), cake.tickCount + partialTick);
                pose.popPose();
            }
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            float flutter = preparing ? Mth.sin((cake.tickCount + partialTick) * 1.9F) : 0.0F;
            pose.mulPose(Axis.ZP.rotationDegrees(flutter * 4));
            draw(CAKE, pose, buffers, light);
            if (preparing && flutter > 0.2F) {
                var sprites = new RitualSprites(pose, buffers, client.gameRenderer.getMainCamera());
                sprites.draw(RitualVisualStyle.Sprite.STAR, 0, 1.0, 0, 0.14, 0, 0xffd890, 0.93);
            }
            pose.popPose();
            super.render(cake, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(DessertFamiliarEntity cake) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        protected int getBlockLightLevel(DessertFamiliarEntity cake, BlockPos position) {
            return Math.max(8, super.getBlockLightLevel(cake, position));
        }
    }

    public static final class Cutlery extends EntityRenderer<CandeloroCutleryEntity> {
        public Cutlery(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(CandeloroCutleryEntity shot, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            Vec3 direction = shot.getDeltaMovement();
            float angle = (float) (-Mth.atan2(direction.x, direction.z) * Mth.RAD_TO_DEG);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.mulPose(Axis.ZP.rotationDegrees(shot.isKnife() ? -24 : 20));
            draw(shot.isKnife() ? KNIFE : FORK, pose, buffers, light);
            pose.popPose();
            super.render(shot, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(CandeloroCutleryEntity shot) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        protected int getBlockLightLevel(CandeloroCutleryEntity shot, BlockPos position) {
            return Math.max(10, super.getBlockLightLevel(shot, position));
        }
    }
}
