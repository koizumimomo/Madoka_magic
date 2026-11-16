package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchCast;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * 按主题选择魔女模型，暂未定稿的主题共用蝴蝶公主模型。
 *
 * <p>用裸 {@link GeoModel} 而非带 TransformStack 的基类：
 * 占位模型的骨骼名不是人形布局，TransformStack 会拿到 null 骨骼导致渲染崩溃。</p>
 */
public class MadokaWitchModel extends GeoModel<MadokaWitchEntity> {

    private static final ResourceLocation MODEL_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/madoka_witch.geo.json");
    private static final ResourceLocation TEXTURE_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/madoka_witch.png");
    private static final ResourceLocation ANIMATION_RESOURCE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/madoka_witch.animation.json");
    private static final ResourceLocation SALVATION_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/salvation_witch.geo.json");
    private static final ResourceLocation SALVATION_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/salvation_witch.png");
    private static final ResourceLocation BUTTERFLY_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/cio_cio_san.geo.json");
    private static final ResourceLocation BUTTERFLY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/cio_cio_san.png");
    private static final ResourceLocation BUTTERFLY_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/cio_cio_san.animation.json");
    private static final ResourceLocation TAKING_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/melissa.geo.json");
    private static final ResourceLocation TAKING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/melissa.png");
    private static final ResourceLocation TAKING_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/melissa.animation.json");
    private static final ResourceLocation MERMAID_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/oktavia.geo.json");
    private static final ResourceLocation MERMAID_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/oktavia.png");
    private static final ResourceLocation MERMAID_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/oktavia.animation.json");
    private static final ResourceLocation ATONEMENT_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/atonement_frame.geo.json");
    private static final ResourceLocation ATONEMENT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/atonement_frame.png");
    private static final ResourceLocation ATONEMENT_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/atonement_frame.animation.json");
    private static final float MERMAID_SWING_TICKS = 5.0F;
    static final ResourceLocation OPHELIA_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/ophelia.geo.json");
    private static final ResourceLocation OPHELIA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/ophelia.png");
    static final ResourceLocation OPHELIA_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/ophelia.animation.json");
    private static final ResourceLocation NIGHT_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/walpurgis_night.geo.json");
    private static final ResourceLocation NIGHT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/walpurgis_night.png");
    private static final ResourceLocation NIGHT_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/walpurgis_night.animation.json");
    private static final ResourceLocation CANDELORO_MODEL = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/candeloro.geo.json");
    private static final ResourceLocation CANDELORO_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/candeloro.png");
    private static final ResourceLocation CANDELORO_ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/candeloro.animation.json");

    @Override
    public ResourceLocation getModelResource(MadokaWitchEntity animatable) {
        if (animatable.isCandeloro()) {
            return CANDELORO_MODEL;
        }
        if (animatable.isAtonement()) {
            return ATONEMENT_MODEL;
        }
        if (animatable.isMermaid()) {
            return MERMAID_MODEL;
        }
        if (animatable.isOphelia()) {
            return OPHELIA_MODEL;
        }
        if (animatable.isWalpurgisNight()) {
            return NIGHT_MODEL;
        }
        if (animatable.isTakingWitch()) {
            return TAKING_MODEL;
        }
        if (animatable.isButterflyWitch()) {
            return BUTTERFLY_MODEL;
        }
        return animatable.isSalvation() ? SALVATION_MODEL : MODEL_RESOURCE;
    }

    @Override
    public ResourceLocation getTextureResource(MadokaWitchEntity animatable) {
        if (animatable.isCandeloro()) {
            return CANDELORO_TEXTURE;
        }
        if (animatable.isAtonement()) {
            return ATONEMENT_TEXTURE;
        }
        if (animatable.isMermaid()) {
            return MERMAID_TEXTURE;
        }
        if (animatable.isOphelia()) {
            return OPHELIA_TEXTURE;
        }
        if (animatable.isWalpurgisNight()) {
            return NIGHT_TEXTURE;
        }
        if (animatable.isTakingWitch()) {
            return TAKING_TEXTURE;
        }
        if (animatable.isButterflyWitch()) {
            return BUTTERFLY_TEXTURE;
        }
        return animatable.isSalvation() ? SALVATION_TEXTURE : TEXTURE_RESOURCE;
    }

    @Override
    public ResourceLocation getAnimationResource(MadokaWitchEntity animatable) {
        if (animatable.isCandeloro()) {
            return CANDELORO_ANIMATION;
        }
        if (animatable.isAtonement()) {
            return ATONEMENT_ANIMATION;
        }
        if (animatable.isMermaid()) {
            return MERMAID_ANIMATION;
        }
        if (animatable.isOphelia()) {
            return OPHELIA_ANIMATION;
        }
        if (animatable.isWalpurgisNight()) {
            return NIGHT_ANIMATION;
        }
        if (animatable.isTakingWitch()) {
            return TAKING_ANIMATION;
        }
        return animatable.isButterflyWitch() ? BUTTERFLY_ANIMATION : ANIMATION_RESOURCE;
    }

    @Override
    public void setCustomAnimations(MadokaWitchEntity witch, long instanceId, AnimationState<MadokaWitchEntity> state) {
        if (!witch.isMermaid()) {
            return;
        }
        float windup = 0.0F;
        float swing = 0.0F;
        var cast = witch.getQuintetCast();
        if (cast != null && cast.kind() == WitchCast.Kind.MERMAID_SWEEP) {
            // Sample the networked strike time so custom warning lengths still line up with the hit.
            float sinceStrike = witch.level().getGameTime() - cast.strike() + state.getPartialTick();
            float halfSwing = MERMAID_SWING_TICKS / 2.0F;
            float charge = (sinceStrike + (cast.strike() - cast.start())) / (cast.strike() - cast.start() - halfSwing);
            float release = smooth((sinceStrike + halfSwing) / MERMAID_SWING_TICKS);
            float recovery = smooth((sinceStrike - halfSwing) / (cast.end() - cast.strike() - halfSwing));
            windup = smooth(charge) * (1.0F - release);
            swing = release * (1.0F - recovery);
        }
        pose("upper_body", -5 * windup + 12 * swing, -52 * windup + 61 * swing, 4 * windup);
        pose("helm", -7 * windup, 31 * windup - 33 * swing, 0);
        pose("sword_arm", 28 * windup + 38 * swing, -11 * windup + 14 * swing, -8 * windup);
        pose("sword_forearm", 29 * windup - 16 * swing, 0, 0);
        pose("sword", -24 * windup - 118 * swing, 0, 4 * windup - 11 * swing);
    }

    private void pose(String name, float x, float y, float z) {
        getBone(name).ifPresent(bone -> {
            var rest = bone.getInitialSnapshot();
            bone.updateRotation(rest.getRotX() + x * Mth.DEG_TO_RAD,
                    rest.getRotY() + y * Mth.DEG_TO_RAD, rest.getRotZ() + z * Mth.DEG_TO_RAD);
        });
    }

    private static float smooth(float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
