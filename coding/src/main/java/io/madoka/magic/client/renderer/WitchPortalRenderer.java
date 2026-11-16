package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchPortalEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Plays the PixelFX entrance and exit loops on an upright sprite at the portal's feet. */
public final class WitchPortalRenderer extends EntityRenderer<WitchPortalEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/particle/witch_portals.png");
    private static final RenderType LAYER = RenderType.entityTranslucentEmissive(TEXTURE);
    private static final int FRAMES_PER_PORTAL = 16;
    private static final int COLUMNS = 4;
    private static final int ROWS = 8;

    public WitchPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WitchPortalEntity portal, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double time = portal.tickCount + (double) partialTick + (portal.getId() & 15);
        int frame = Mth.floor(time * 0.58D) % FRAMES_PER_PORTAL;
        if (!portal.isEntrance()) {
            frame += FRAMES_PER_PORTAL;
        }
        var sprites = new RitualSprites(pose, buffers, camera, LAYER);
        double height = portal.getBbHeight();
        sprites.drawTile(frame, COLUMNS, ROWS, 96, 128, 0, height * 0.5D, 0,
                portal.getBbWidth() * 0.73D, height * 0.55D, 0, 0xffffff, 1.0D);

        // A few theme-colored sparks identify the encounter independently of the entrance/exit color.
        var style = RitualVisualStyle.forTheme(portal.getThemeId());
        var sparks = new RitualSprites(pose, buffers, camera);
        for (int i = 0; i < 4; i++) {
            double phase = (time / 46.0D + i * 0.25D) % 1.0D;
            double angle = i * WitchAura.TURN / 4.0D + time * 0.035D;
            double radius = 0.83D + 0.24D * phase;
            sparks.draw(RitualVisualStyle.Sprite.STAR, radius * Math.cos(angle), height * (0.19D + 0.64D * phase),
                    radius * Math.sin(angle), 0.07D, 0, style.crown(), WitchAura.fade(phase) * 0.76D);
        }
        super.render(portal, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WitchPortalEntity portal) {
        return TEXTURE;
    }
}
