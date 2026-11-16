package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BeeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Bee;

public final class BlackBeeRenderer extends BeeRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/black_bee.png");
    private static final RenderType EYES = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/black_bee_eyes.png"));

    public BlackBeeRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new EyesLayer<Bee, BeeModel<Bee>>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Bee bee) {
        return TEXTURE;
    }
}
