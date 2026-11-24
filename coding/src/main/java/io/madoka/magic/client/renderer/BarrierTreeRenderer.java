package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.BarrierTreeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class BarrierTreeRenderer extends GeoEntityRenderer<BarrierTreeEntity> {
    private static final ResourceLocation GEOMETRY = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "geo/burnt_tree.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/burnt_tree.png");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "animations/barrier_prop.animation.json");

    public BarrierTreeRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(BarrierTreeEntity tree) {
                return GEOMETRY;
            }

            @Override
            public ResourceLocation getTextureResource(BarrierTreeEntity tree) {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(BarrierTreeEntity tree) {
                return ANIMATION;
            }
        });
        shadowRadius = 0.0F;
    }

    @Override
    protected int getBlockLightLevel(BarrierTreeEntity tree, BlockPos pos) {
        return Math.max(3, super.getBlockLightLevel(tree, pos));
    }
}
