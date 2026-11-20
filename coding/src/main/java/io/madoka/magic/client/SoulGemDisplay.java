package io.madoka.magic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class SoulGemDisplay {
    private static final int TILE = 32;
    private static final int GLASS_TOP = 7;
    private static final int GLASS_BOTTOM = 24;
    private static final ResourceLocation NEUTRAL = texture("neutral");
    private static final Map<String, ResourceLocation> GEMS = Map.of(
            "sacrifice", texture("sacrifice"), "take", texture("take"), "atonement", texture("atonement"));
    private static final Map<String, ItemStack> SEEDS = new HashMap<>();

    private SoulGemDisplay() {}

    public static void render(GuiGraphics graphics, int x, int y, int size) {
        boolean contracted = ClientMadokaState.isContracted();
        if (contracted && ClientMadokaState.isWitchPath()) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(size / 16.0F, size / 16.0F, 1.0F);
            graphics.renderItem(SEEDS.computeIfAbsent(ClientMadokaState.getThemeId(), GriefSeedItem::fromWitch), 0, 0);
            pose.popPose();
            return;
        }
        var texture = contracted ? GEMS.getOrDefault(ClientMadokaState.getThemeId(), NEUTRAL) : NEUTRAL;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0D);
        float scale = (float) size / TILE;
        pose.scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, TILE, TILE, TILE, TILE * 2);
        if (contracted) {
            float rows = Mth.clamp(ClientMadokaState.getTaint() / 100.0F, 0.0F, 1.0F) * (GLASS_BOTTOM - GLASS_TOP);
            int solidRows = (int) rows;
            int firstRow = GLASS_BOTTOM - solidRows;
            if (solidRows > 0) {
                graphics.blit(texture, 0, firstRow, 0.0F, TILE + firstRow, TILE, solidRows, TILE, TILE * 2);
            }
            float edgeAlpha = rows - solidRows;
            if (edgeAlpha > 0.0F) {
                // Blend the boundary row so small changes in pollution are visible even between texel steps.
                graphics.setColor(1.0F, 1.0F, 1.0F, edgeAlpha);
                graphics.blit(texture, 0, firstRow - 1, 0.0F, TILE + firstRow - 1, TILE, 1, TILE, TILE * 2);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/gui/soul_gem/" + name + ".png");
    }
}
