package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.FrameShardEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;

public final class AtonementRenderers {
    private AtonementRenderers() {}

    public static final class Shard extends EntityRenderer<FrameShardEntity> {
        public Shard(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(FrameShardEntity shard) {
            return AtonementVisuals.ATLAS;
        }

        @Override
        public void render(FrameShardEntity shard, float yaw, float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            AtonementVisuals.projectile(pose, buffers, Minecraft.getInstance().gameRenderer.getMainCamera(),
                    shard.getId(), shard.tickCount + (double) partialTick);
            super.render(shard, yaw, partialTick, pose, buffers, light);
        }
    }

    public static final class MourningZombie extends ZombieRenderer {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
                MadokaMagic.MODID, "textures/entity/mourning_zombie.png");

        public MourningZombie(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie zombie) {
            return TEXTURE;
        }

        @Override
        protected int getBlockLightLevel(Zombie zombie, BlockPos pos) {
            return Math.max(7, super.getBlockLightLevel(zombie, pos));
        }
    }

    public static final class MourningSkeleton extends SkeletonRenderer {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
                MadokaMagic.MODID, "textures/entity/mourning_skeleton.png");

        public MourningSkeleton(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(AbstractSkeleton skeleton) {
            return TEXTURE;
        }

        @Override
        protected int getBlockLightLevel(AbstractSkeleton skeleton, BlockPos pos) {
            return Math.max(7, super.getBlockLightLevel(skeleton, pos));
        }
    }
}
