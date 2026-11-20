package io.madoka.magic.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.client.hud.HudLayout;
import io.madoka.magic.compat.IronsCompat;
import io.madoka.magic.data.PlayerMadokaData;
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

    private static final int GEM_SIZE = 22;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_SLOT_SPACING = 20;
    private static final int OFFHAND_SLOT_WIDTH = 29;
    private static final int FOOD_BAR_WIDTH = 81;
    private static final int MANA_BOTTOM_OFFSET = 54;
    private static final int IRONS_MANA_SPACING = 13;

    private MadokaHud() {
    }

    public static void renderTaintVignette(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float taint = ClientMadokaState.getTaint();
        if (!ClientMadokaState.isContracted() || ClientMadokaState.isWitchPath()
                || taint <= PlayerMadokaData.TAINT_WARNING_THRESHOLD) {
            return;
        }
        float progress = Mth.clampedMap(taint, PlayerMadokaData.TAINT_WARNING_THRESHOLD, PlayerMadokaData.TAINT_MAX, 0.0F, 1.0F);
        float darkness = Mth.lerp(progress, 0.16F, 0.94F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        graphics.setColor(darkness, darkness, darkness, 1.0F);
        graphics.blit(VIGNETTE, 0, 0, width, height, 0, 0, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** 供 RegisterGuiOverlaysEvent 注册。 */
    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (!ClientMadokaState.isContracted()) {
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
