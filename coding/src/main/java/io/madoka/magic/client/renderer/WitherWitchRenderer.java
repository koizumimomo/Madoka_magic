package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.dimension.MadokaDimensions;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WitherBossRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.wither.WitherBoss;

public final class WitherWitchRenderer extends WitherBossRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/wither_witch.png");
    private static final ResourceLocation CHARGING = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/entity/wither_witch_invulnerable.png");

    public WitherWitchRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    static boolean inBarrier(WitherBoss wither) {
        return wither.level().dimension().equals(MadokaDimensions.witchLevel(WitchTypes.WITHER));
    }

    @Override
    public ResourceLocation getTextureLocation(WitherBoss wither) {
        if (!inBarrier(wither)) {
            return super.getTextureLocation(wither);
        }
        int charge = wither.getInvulnerableTicks();
        return charge > 0 && (charge > 80 || charge / 5 % 2 != 1) ? CHARGING : TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(WitherBoss wither, BlockPos position) {
        int light = super.getBlockLightLevel(wither, position);
        return inBarrier(wither) ? Math.max(light, 5) : light;
    }
}
