package io.madoka.magic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.MadokaMagic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class ItemTooltipBorder {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/gui/tooltip_borders.png");
    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 32;
    private static final int BACKGROUND_Z = 400;
    private static final int TOP_COLOR = 0xFFE2B48F;
    private static final int BOTTOM_COLOR = 0xFFA57390;
    private static final int TOP_OVERHANG = 8;
    private static final int BOTTOM_OVERHANG = 6;
    private static final int CORNER_SIZE = 8;
    private static final int ATLAS_CENTER = 24;

    private ItemTooltipBorder() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void positionTooltip(RenderTooltipEvent.Pre event) {
        if (!isMadokaItem(event.getItemStack()) || event.getTooltipPositioner() != DefaultTooltipPositioner.INSTANCE) {
            return;
        }
        Size size = measure(event);
        int mouseOffset = TooltipRenderUtil.MOUSE_OFFSET;
        int minY = mouseOffset + TooltipRenderUtil.PADDING_TOP + TOP_OVERHANG;
        int maxY = event.getScreenHeight() - size.height() + mouseOffset - TooltipRenderUtil.PADDING_BOTTOM - BOTTOM_OVERHANG;
        if (minY <= maxY) {
            event.setY(Mth.clamp(event.getY(), minY, maxY));
        }

        int contentRight = event.getX() + mouseOffset + size.width();
        int rightLimit = event.getScreenWidth() - TooltipRenderUtil.PADDING_RIGHT - 3;
        if (contentRight > rightLimit && contentRight <= event.getScreenWidth()) {
            event.setX(event.getX() - (contentRight - rightLimit));
        } else if (contentRight > event.getScreenWidth()) {
            int leftMargin = TooltipRenderUtil.PADDING_LEFT + 3;
            if (size.width() + leftMargin <= rightLimit) {
                event.setX(Math.max(event.getX(), size.width() + mouseOffset + leftMargin));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drawBorder(RenderTooltipEvent.Color event) {
        if (!isMadokaItem(event.getItemStack()) || event.getBorderStart() != event.getOriginalBorderStart()
                || event.getBorderEnd() != event.getOriginalBorderEnd()) {
            return;
        }
        Size size = measure(event);
        GuiGraphics graphics = event.getGraphics();
        int x = event.getX();
        int y = event.getY();
        // Draw the decorated background here, before vanilla renders the text and item components.
        TooltipRenderUtil.renderTooltipBackground(graphics, x, y, size.width(), size.height(), BACKGROUND_Z,
                event.getBackgroundStart(), event.getBackgroundEnd(), TOP_COLOR, BOTTOM_COLOR);
        graphics.flush();
        event.setBackground(0);
        event.setBorderStart(0);
        event.setBorderEnd(0);

        int left = x - TooltipRenderUtil.PADDING_LEFT;
        int right = x + size.width() + TooltipRenderUtil.PADDING_RIGHT - 1;
        int top = y - TooltipRenderUtil.PADDING_TOP;
        int bottom = y + size.height() + TooltipRenderUtil.PADDING_BOTTOM - 1;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        blit(graphics, left - 3, top - 3, 0, 0, CORNER_SIZE, CORNER_SIZE);
        blit(graphics, right - 4, top - 3, 8, 0, CORNER_SIZE, CORNER_SIZE);
        blit(graphics, left - 3, bottom - 4, 16, 0, CORNER_SIZE, CORNER_SIZE);
        blit(graphics, right - 4, bottom - 4, 24, 0, CORNER_SIZE, CORNER_SIZE);
        drawCenter(graphics, left, right, top - TOP_OVERHANG, 8, 21, 11);
        drawCenter(graphics, left, right, bottom - 1, 20, 15, 8);
        RenderSystem.disableBlend();
    }

    private static void drawCenter(GuiGraphics graphics, int left, int right, int y, int v, int radius, int height) {
        int center = left + (right - left) / 2;
        int halfWidth = Math.min(radius, center - left - 5);
        if (halfWidth >= 0) {
            blit(graphics, center - halfWidth, y, ATLAS_CENTER - halfWidth, v, halfWidth * 2 + 1, height);
        }
    }

    private static void blit(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(TEXTURE, x, y, BACKGROUND_Z + 1, u, v, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    static void drawBarFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        int right = width * 2 - 1;
        int bottom = height * 2 - 1;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            barPiece(graphics, -3, -3, 0, 0, CORNER_SIZE, CORNER_SIZE);
            barPiece(graphics, right - 4, -3, 8, 0, CORNER_SIZE, CORNER_SIZE);
            barPiece(graphics, -3, bottom - 4, 16, 0, CORNER_SIZE, CORNER_SIZE);
            barPiece(graphics, right - 4, bottom - 4, 24, 0, CORNER_SIZE, CORNER_SIZE);
            barPiece(graphics, width - 21, -TOP_OVERHANG, ATLAS_CENTER - 21, 8, 43, 11);
            barPiece(graphics, width - 15, bottom - 1, ATLAS_CENTER - 15, 20, 31, 8);
        } finally {
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    private static void barPiece(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(TEXTURE, x, y, u, v, width, height, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    private static boolean isMadokaItem(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return !stack.isEmpty() && id != null && MadokaMagic.MODID.equals(id.getNamespace());
    }

    private static Size measure(RenderTooltipEvent event) {
        // Match GuiGraphics' measurement, including its single-line adjustment.
        int width = 0;
        int height = event.getComponents().size() == 1 ? -2 : 0;
        for (var component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        return new Size(width, height);
    }

    private record Size(int width, int height) {}
}
