package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class WitchBossHud {
    private static final int VANILLA_WIDTH = 182;
    private static final int BAR_HEIGHT = 8;
    private static final int ROW_HEIGHT = 44;
    private static final int ORNAMENT_SIZE = 14;
    private static final String NAME_PREFIX = "witch.madoka_magic.";
    private static final String NAME_SUFFIX = ".name";
    private static final ResourceLocation RUNE_FONT = ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "witch_runes");

    private WitchBossHud() {}

    @SubscribeEvent
    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Component title = event.getBossEvent().getName();
        String theme = themeOf(title);
        if (theme.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setIncrement(Math.max(event.getIncrement(), ROW_HEIGHT));
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        var style = RitualVisualStyle.forTheme(theme);
        int center = event.getX() + VANILLA_WIDTH / 2;
        int width = Math.min(VANILLA_WIDTH, graphics.guiWidth() - 8);
        int left = center - width / 2;
        int y = event.getY() + 5;

        Component displayName = WitchTypes.WITHER.equals(theme) ? GriefSeedItem.witchName(theme) : title;
        graphics.drawCenteredString(font, displayName, center, event.getY() - 10, 0xfff8f1ff);
        drawBar(graphics, left, y, width, event.getBossEvent().getProgress(), style);

        String runesKey = NAME_PREFIX + theme + ".runes";
        if (Language.getInstance().has(runesKey)) {
            var runes = Component.translatable(runesKey).withStyle(styleText -> styleText.withFont(RUNE_FONT));
            graphics.drawCenteredString(font, runes, center, y + 19, opaque(style.crown()));
        }
    }

    private static String themeOf(Component title) {
        if (!(title.getContents() instanceof TranslatableContents translated)) {
            return "";
        }
        String key = translated.getKey();
        if ("entity.minecraft.wither".equals(key)) {
            var level = Minecraft.getInstance().level;
            return level != null && level.dimension().equals(MadokaDimensions.witchLevel(WitchTypes.WITHER))
                    ? WitchTypes.WITHER : "";
        }
        if (WitchTypes.GLUTTONY_BAR_TITLE.equals(key)) {
            return WitchTypes.GLUTTONY;
        }
        if (key.length() <= NAME_PREFIX.length() + NAME_SUFFIX.length()
                || !key.startsWith(NAME_PREFIX) || !key.endsWith(NAME_SUFFIX)) {
            return "";
        }
        String theme = key.substring(NAME_PREFIX.length(), key.length() - NAME_SUFFIX.length());
        return MadokaDimensions.hasBarrier(theme) || WitchTypes.NIGHT.equals(theme) ? theme : "";
    }

    private static void drawBar(GuiGraphics graphics, int left, int y, int width, float progress,
                                RitualVisualStyle style) {
        int right = left + width;
        int trackLeft = left + ORNAMENT_SIZE;
        int trackRight = right - ORNAMENT_SIZE;
        int frame = opaque(shade(style.secondary(), 0.68F));
        graphics.fill(left + 1, y, right - 1, y + BAR_HEIGHT, frame);
        graphics.fill(left, y + 1, right, y + BAR_HEIGHT - 1, frame);
        graphics.fillGradient(trackLeft, y + 1, trackRight, y + BAR_HEIGHT - 1,
                0xff271e32, 0xff13101b);
        int fill = Mth.ceil((trackRight - trackLeft) * Mth.clamp(progress, 0.0F, 1.0F));
        if (fill > 0) {
            int end = trackLeft + fill;
            graphics.fillGradient(trackLeft, y + 1, end, y + BAR_HEIGHT - 1,
                    opaque(style.primary()), opaque(shade(style.primary(), 0.64F)));
            graphics.fill(trackLeft, y + 1, end, y + 2, opaque(style.crown()));
        }
        drawOrnament(graphics, left, y - 3, style.motif(), style.crown(), frame);
        drawOrnament(graphics, right - ORNAMENT_SIZE, y - 3, style.accent(), style.crown(), frame);
        drawCenter(graphics, left + width / 2, y, style, frame);
    }

    private static void drawCenter(GuiGraphics graphics, int center, int y, RitualVisualStyle style, int frame) {
        int rim = opaque(shade(style.crown(), 0.64F));
        int light = opaque(shade(style.crown(), 0.90F));

        mirroredFill(graphics, center, 8, 27, y, y + 1, rim);
        mirroredFill(graphics, center, 25, 41, y - 2, y, frame);
        mirroredFill(graphics, center, 27, 39, y - 2, y - 1, light);
        mirroredFill(graphics, center, 39, 57, y, y + 1, rim);
        mirroredFill(graphics, center, 8, 32, y + 7, y + 9, frame);
        mirroredFill(graphics, center, 15, 28, y + 8, y + 9, light);
        mirroredFill(graphics, center, 31, 54, y + 7, y + 8, rim);
        mirroredFill(graphics, center, 32, 36, y + 8, y + 11, frame);
        mirroredFill(graphics, center, 33, 35, y + 11, y + 12, rim);

        var accent = style.bossCrest() == style.accent() ? style.motif() : style.accent();
        drawSprite(graphics, center - 47, y + 6, 10, accent, style.crown());
        drawSprite(graphics, center + 37, y + 6, 10, accent, style.crown());

        graphics.fill(center - 2, y - 4, center + 2, y, frame);
        graphics.fill(center - 5, y - 2, center + 5, y + 1, rim);
        graphics.fill(center - 1, y - 3, center + 1, y, light);
        graphics.fill(center - 8, y + 5, center + 8, y + 10, rim);
        graphics.fill(center - 6, y + 6, center + 6, y + 13, frame);
        graphics.fill(center - 4, y + 7, center + 4, y + 15, frame);
        graphics.fill(center - 2, y + 14, center + 2, y + 17, rim);
        graphics.fill(center - 5, y + 6, center + 5, y + 12, 0xff211a29);
        drawSprite(graphics, center - 7, y + 3, 14, style.bossCrest(), style.crown());
    }

    private static void mirroredFill(GuiGraphics graphics, int center, int from, int to, int top, int bottom, int color) {
        graphics.fill(center - to, top, center - from, bottom, color);
        graphics.fill(center + from, top, center + to, bottom, color);
    }

    private static void drawOrnament(GuiGraphics graphics, int x, int y, RitualVisualStyle.Sprite sprite,
                                     int tint, int border) {
        graphics.fill(x + 4, y, x + 10, y + 14, border);
        graphics.fill(x + 2, y + 2, x + 12, y + 12, border);
        graphics.fill(x, y + 4, x + 14, y + 10, border);
        graphics.fill(x + 3, y + 3, x + 11, y + 11, 0xff211a29);
        drawSprite(graphics, x + 1, y + 1, ORNAMENT_SIZE - 2, sprite, tint);
    }

    private static void drawSprite(GuiGraphics graphics, int x, int y, int size, RitualVisualStyle.Sprite sprite, int tint) {
        int tile = RitualSprites.TILE_SIZE;
        int u = sprite.tile % RitualSprites.COLUMNS * tile;
        int v = sprite.tile / RitualSprites.COLUMNS * tile;
        RenderSystem.enableBlend();
        graphics.setColor((tint >> 16 & 255) / 255.0F, (tint >> 8 & 255) / 255.0F, (tint & 255) / 255.0F, 1.0F);
        graphics.blit(RitualSprites.ATLAS, x, y, size, size, u, v,
                tile, tile, tile * RitualSprites.COLUMNS, tile * RitualSprites.ROWS);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static int shade(int rgb, float amount) {
        int red = Math.round((rgb >> 16 & 255) * amount);
        int green = Math.round((rgb >> 8 & 255) * amount);
        int blue = Math.round((rgb & 255) * amount);
        return red << 16 | green << 8 | blue;
    }

    private static int opaque(int rgb) {
        return 0xff000000 | rgb;
    }
}
