package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

final class OpheliaDecoyModel extends GeoModel<OpheliaDecoyModel.Decoy> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/ophelia_decoy.png");
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(TEXTURE);
    private static final Decoy DECOY = new Decoy();
    private static GeoObjectRenderer<Decoy> renderer;

    static void render(WitchCast cast, double time, double runner, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (time > cast.end() || (!cast.hasDecoy(-1) && !cast.hasDecoy(1))) {
            return;
        }
        if (renderer == null) {
            renderer = new GeoObjectRenderer<>(new OpheliaDecoyModel());
        }
        DECOY.dashing = time >= cast.strike();
        for (int side : new int[]{-1, 1}) {
            if (!cast.hasDecoy(side)) {
                continue;
            }
            var pos = cast.castPoint(side * WitchCast.OPHELIA_DECOY_OFFSET, 0, runner);
            pose.pushPose();
            pose.translate(pos.x, pos.y, pos.z);
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - cast.direction()));
            // Undo GeoObjectRenderer's placement inside a block before drawing the entity-shaped model.
            pose.translate(-0.5D, -0.51D, -0.5D);
            renderer.render(pose, DECOY, buffers, LAYER, null, LightTexture.pack(11, 0));
            pose.popPose();
        }
        buffers.endBatch(LAYER);
    }

    @Override
    public ResourceLocation getModelResource(Decoy decoy) {
        return MadokaWitchModel.OPHELIA_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(Decoy decoy) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Decoy decoy) {
        return MadokaWitchModel.OPHELIA_ANIMATION;
    }

    static final class Decoy implements GeoAnimatable {
        private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ophelia.idle");
        private static final RawAnimation GAIT = RawAnimation.begin().thenLoop("animation.ophelia.gait");
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private boolean dashing;

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "decoy", 2,
                    state -> state.setAndContinue(dashing ? GAIT : IDLE)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object ignored) {
            return Minecraft.getInstance().level.getGameTime();
        }
    }
}
