package io.madoka.magic.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.client.hud.HudLayout;
import io.madoka.magic.compat.IronsCompat;
import io.madoka.magic.data.PlayerMadokaData;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** 灵魂宝石和魔力条 HUD。 */
public final class MadokaHud {

    private static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/misc/vignette.png");
    /** 不规则波浪黑框：外圈深蓝黑有机轮廓，中央视口透明。 */
    private static final ResourceLocation TAINT_FRAME = ResourceLocation.fromNamespaceAndPath("madoka_magic", "textures/gui/taint_frame.png");
    private static final int FRAME_TEX_W = 640;
    private static final int FRAME_TEX_H = 360;
    /** 边框繁星数量与所在环带（max(|nx|,|ny|) 归一化半径，超出才绘制）。 */
    private static final int STARS = 72;
    private static final float STAR_BAND = 0.84F;

    private static final int GEM_SIZE = 22;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_SLOT_SPACING = 20;
    private static final int OFFHAND_SLOT_WIDTH = 29;
    private static final int FOOD_BAR_WIDTH = 81;
    private static final int MANA_BOTTOM_OFFSET = 54;
    private static final int IRONS_MANA_SPACING = 13;

    private MadokaHud() {
    }

    /** 高污染表现：轻度压暗 + 不规则波浪黑框从屏幕外收进来 + 框内繁星流动（同魔力条手法）。 */
    public static void renderTaintVignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float taint = ClientMadokaState.getTaint();
        if (!ClientMadokaState.isContracted() || ClientMadokaState.isWitchPath()
                || taint <= PlayerMadokaData.TAINT_WARNING_THRESHOLD) {
            return;
        }
        float progress = Mth.clampedMap(taint, PlayerMadokaData.TAINT_WARNING_THRESHOLD, PlayerMadokaData.TAINT_MAX, 0.0F, 1.0F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        // 中央轻度压暗：保留视口内的画面，只铺污染氛围
        float darkness = Mth.lerp(progress, 0.10F, 0.52F);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        graphics.setColor(darkness, darkness, darkness, 1.0F);
        graphics.blit(VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();

        // 不规则黑框：先随污染淡入（此时整体在屏幕外侧），再逐渐向内收拢
        float frameAlpha = Mth.clampedMap(progress, 0.0F, 0.55F, 0.0F, 1.0F);
        if (frameAlpha > 0.01F) {
            float scale = Mth.lerp(progress, 1.42F, 1.0F);
            int fw = (int) (width * scale);
            int fh = (int) (height * scale);
            graphics.setColor(1.0F, 1.0F, 1.0F, frameAlpha);
            graphics.blit(TAINT_FRAME, (width - fw) / 2, (height - fh) / 2, fw, fh,
                    0, 0, FRAME_TEX_W, FRAME_TEX_H, FRAME_TEX_W, FRAME_TEX_H);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        drawFrameStars(graphics, width, height, progress);

        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** 边框繁星：横向缓慢流动 + 明暗闪烁，只绘制在屏幕外圈环带（黑框区域）内。 */
    private static void drawFrameStars(GuiGraphics graphics, int width, int height, float progress) {
        float visible = Mth.clampedMap(progress, 0.3F, 0.95F, 0.0F, 1.0F);
        if (visible <= 0.01F) {
            return;
        }
        double seconds = Util.getMillis() / 1000.0D;
        // 半分辨率工作，让星点能以半像素步进漂移（同魔力条）
        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(0.5F, 0.5F, 1.0F);
        int w2 = width * 2;
        int h2 = height * 2;
        float halfW = w2 / 2.0F;
        float halfH = h2 / 2.0F;
        double span = w2 + 8.0D;
        for (int i = 0; i < STARS; i++) {
            double speed = 3.0D + hash01(i, 11) * 6.0D;
            double x = frac(i * 0.61803398875D + hash01(i, 3)) * span - seconds * speed;
            x = ((x % span) + span) % span - 4.0D;
            double y = frac(i * 0.75487766624D + hash01(i, 7)) * (h2 + 8.0D) - 4.0D
                    + Math.sin(seconds * 0.4D + i * 1.7D) * 3.0D;
            // 矩形环带测试：只在黑框带内绘制，内缘做渐隐避免星星悬在视口上
            float nx = Math.abs((float) x / halfW - 1.0F);
            float ny = Math.abs((float) y / halfH - 1.0F);
            float band = Math.max(nx, ny);
            if (band <= STAR_BAND) {
                continue;
            }
            float fade = Mth.clampedMap(band, STAR_BAND, STAR_BAND + 0.07F, 0.0F, 1.0F);
            float twinkle = 0.55F + 0.45F * (float) Math.sin(seconds * (1.2D + hash01(i, 5)) + i * 2.4D);
            int alpha = (int) (visible * fade * twinkle * 230.0F);
            if (alpha < 8) {
                continue;
            }
            boolean bright = i % 5 == 0;
            int color = (alpha << 24) | (bright ? 0xF2F4FF : i % 3 == 0 ? 0xBFD2FF : 0xE6DDFF);
            int px = (int) x;
            int py = (int) y;
            graphics.fill(px, py, px + 1, py + 1, color);
            if (bright) {
                int glow = (alpha * 2 / 3 << 24) | 0xC9D6FF;
                graphics.fill(px - 1, py, px + 2, py + 1, glow);
                graphics.fill(px, py - 1, px + 1, py + 2, glow);
            }
        }
        pose.popPose();
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static float hash01(int a, int b) {
        double v = Math.sin(a * 127.1D + b * 311.7D) * 43758.5453D;
        return (float) (v - Math.floor(v));
    }

    /** 供 RegisterGuiOverlaysEvent 注册。 */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientMadokaState.isContracted() && !ClientCurseDebt.visible()) {
            return;
        }
        HudLayout.render(graphics, width, height);
        if (ClientMadokaState.isTransformed()) {
            renderManaBar(graphics, width, height);
        }
    }

    /** 魔力条与原版饥饿栏右对齐。 */
    private static void renderManaBar(GuiGraphics graphics, int width, int height) {
        int x = width / 2 + HOTBAR_HALF_WIDTH - FOOD_BAR_WIDTH;
        int y = height - MANA_BOTTOM_OFFSET - (IronsCompat.isPresent() ? IRONS_MANA_SPACING : 0);
        double mana = ClientMadokaState.getMana();
        double maxMana = ClientMadokaState.getMaxMana();
        MagicResourceBar.render(graphics, Minecraft.getInstance().font, x, y, FOOD_BAR_WIDTH,
                maxMana > 0.0D ? mana / maxMana : 0.0D, MagicResourceBar.Palette.MANA,
                Mth.floor(mana) + "/" + Mth.floor(maxMana));
    }
}
