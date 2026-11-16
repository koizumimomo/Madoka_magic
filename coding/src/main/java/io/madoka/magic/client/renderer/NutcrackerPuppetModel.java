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

final class NutcrackerPuppetModel extends GeoModel<NutcrackerPuppetModel.Puppet> {
    private static final ResourceLocation GEOMETRY = resource("geo/nutcracker.geo.json");
    private static final ResourceLocation TEXTURE = resource("textures/entity/nutcracker.png");
    private static final ResourceLocation ANIMATION = resource("animations/nutcracker.animation.json");
    private static final RenderType LAYER = RenderType.entityCutoutNoCull(TEXTURE);
    private static final Puppet PUPPET = new Puppet();
    private static GeoObjectRenderer<Puppet> renderer;

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, path);
    }

    static void render(WitchCast cast, double time, PoseStack pose, MultiBufferSource.BufferSource buffers) {
        if (time < cast.strike() || time > cast.end()) {
            return;
        }
        if (renderer == null) {
            renderer = new GeoObjectRenderer<>(new NutcrackerPuppetModel());
        }
        double row = cast.rowAt(time);
        for (int lane = 0; lane < WitchCast.LANES; lane++) {
            if (lane == cast.gap()) {
                continue;
            }
            double left = cast.laneEdge(lane);
            double step = (cast.laneEdge(lane + 1) - left) / 3;
            float scale = (float) Math.min(1.0D, step * 0.90D);
            for (int index = 0; index < 3; index++) {
                var point = cast.rowPoint(left + (index + 0.5D) * step, 0, row);
                pose.pushPose();
                pose.translate(point.x, point.y, point.z);
                pose.mulPose(Axis.YP.rotationDegrees(90.0F * cast.direction() + 180.0F));
                pose.scale(scale, scale, scale);
                // GeoObjectRenderer centers block-sized objects by default.
                pose.translate(-0.5D, -0.51D, -0.5D);
                renderer.render(pose, PUPPET, buffers, LAYER, null, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
        }
        buffers.endBatch(LAYER);
    }

    @Override
    public ResourceLocation getModelResource(Puppet puppet) {
        return GEOMETRY;
    }

    @Override
    public ResourceLocation getTextureResource(Puppet puppet) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(Puppet puppet) {
        return ANIMATION;
    }

    static final class Puppet implements GeoAnimatable {
        private static final RawAnimation MARCH = RawAnimation.begin().thenLoop("animation.nutcracker.march");
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "march", 0, state -> state.setAndContinue(MARCH)));
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
