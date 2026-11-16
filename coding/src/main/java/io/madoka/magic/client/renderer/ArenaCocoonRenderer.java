package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.ArenaCocoonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class ArenaCocoonRenderer extends GeoEntityRenderer<ArenaCocoonEntity> {
    private static final ResourceLocation GEOMETRY = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/giant_chrysalis.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/giant_chrysalis.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/madoka_witch.animation.json");

    public ArenaCocoonRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(ArenaCocoonEntity cocoon) {
                return GEOMETRY;
            }

            @Override
            public ResourceLocation getTextureResource(ArenaCocoonEntity cocoon) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(ArenaCocoonEntity cocoon) {
                return ANIMATION;
            }
        });
        withScale(0.64F, 0.61F);
    }

    @Override
    public Color getRenderColor(ArenaCocoonEntity cocoon, float partialTick, int packedLight) {
        return cocoon.isBee() ? Color.ofOpaque(0xffad70) : Color.WHITE;
    }

    @Override
    protected int getBlockLightLevel(ArenaCocoonEntity cocoon, BlockPos pos) {
        return Math.max(5, super.getBlockLightLevel(cocoon, pos));
    }
}
