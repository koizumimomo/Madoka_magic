package io.madoka.magic.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
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
        renderGem(graphics, width, height);
        if (ClientMadokaState.isTransformed()) {
            renderManaBar(graphics, width, height);
            renderCooldowns(graphics, partialTick, width, height);
        }
    }

    private static void renderCooldowns(GuiGraphics graphics, float partialTick, int width, int height) {
        var theme = ClientMadokaState.getTheme();
        if (theme == null) return;
        var font = Minecraft.getInstance().font;
        int x = Math.max(4, width / 2 - 91 - 132);
        int y = height - 51;
        int rows = Math.max(1, (height - 16) / 25);
        int index = 0;
        for (String id : theme.getSkills(ClientMadokaState.isWitchPath())) {
            double remaining = ClientProgression.cooldown(id, partialTick);
            if (remaining <= 0) continue;
            var spell = io.madoka.magic.spell.SpellRegistry.get(id);
            if (spell == null || !spell.active()) continue;
            int rowY = y - index % rows * 25;
            int columnX = x + index / rows * 125;
            if (rowY < 4) break;
            graphics.fill(columnX, rowY, columnX + 119, rowY + 23, 0xcc1b1724);
            graphics.blit(spell.icon(), columnX + 2, rowY + 2, 19, 19, 0, 0, 32, 32, 32, 32);
            int overlay = (int) Math.ceil(19 * Math.min(1, remaining / ClientProgression.cooldownLength(id)));
            graphics.fill(columnX + 2, rowY + 21 - overlay, columnX + 21, rowY + 21, 0xa015101e);
            String time = String.format(java.util.Locale.ROOT, remaining >= 200 ? "%.0fs" : "%.1fs", remaining / 20);
            String name = font.plainSubstrByWidth(net.minecraft.network.chat.Component.translatable(spell.nameKey()).getString(), 65);
            graphics.drawString(font, name, columnX + 25, rowY + 3, 0xffdfd1ea, false);
            graphics.drawString(font, time, columnX + 116 - font.width(time), rowY + 3, 0xffe9c883, false);
            graphics.fill(columnX + 25, rowY + 17, columnX + 116, rowY + 20, 0xff49394f);
            graphics.fill(columnX + 25, rowY + 17, columnX + 25
                    + (int) Math.ceil(91 * Math.min(1, remaining / ClientProgression.cooldownLength(id))), rowY + 20, 0xffbb92d0);
            index++;
        }
    }

    private static void renderGem(GuiGraphics graphics, int width, int height) {
        int x = width / 2 - HOTBAR_HALF_WIDTH - HOTBAR_SLOT_SPACING;
        if (Minecraft.getInstance().getCameraEntity() instanceof Player player && !player.isSpectator()
                && player.getMainArm() == HumanoidArm.RIGHT && !player.getOffhandItem().isEmpty()) {
            x -= OFFHAND_SLOT_WIDTH;
        }
        SoulGemDisplay.render(graphics, Math.max(2, x), height - GEM_SIZE, GEM_SIZE);
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
