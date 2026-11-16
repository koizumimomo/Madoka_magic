package io.madoka.magic.client;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.Locale;

public final class MagicalGirlInfoScreen extends Screen {
    private static final int ROW_HEIGHT = 14;
    private static final int PADDING = 12;
    private static final int GEM_BUTTON_SIZE = 24;
    private final Screen parent;
    private int left;
    private int right;
    private int top;
    private int bottom;
    private int divider;
    private int scroll;

    private MagicalGirlInfoScreen(Screen parent) {
        super(Component.translatable(ClientMadokaState.isWitchPath()
                ? "screen.madoka_magic.info.title.witch" : "screen.madoka_magic.info.title"));
        this.parent = parent;
    }

    static Button inventoryTab(AbstractContainerScreen<?> inventory) {
        Component title = Component.translatable(ClientMadokaState.isWitchPath()
                ? "screen.madoka_magic.info.tab.witch" : "screen.madoka_magic.info.tab");
        Rect2i initial = inventoryTabBounds(inventory);
        return new Button(Button.builder(title,
                button -> net.minecraft.client.Minecraft.getInstance().setScreen(new MagicalGirlInfoScreen(inventory)))
                .tooltip(Tooltip.create(title))
                .bounds(initial.getX(), initial.getY(), initial.getWidth(), initial.getHeight())) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                Rect2i bounds = inventoryTabBounds(inventory);
                setX(bounds.getX());
                setY(bounds.getY());
                boolean highlighted = isFocused() || isMouseOver(mouseX, mouseY);
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                        highlighted ? 0xFFD5B2E4 : 0xFF71627D);
                graphics.fill(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1,
                        highlighted ? 0xFF483556 : 0xFF272131);
                SoulGemDisplay.render(graphics, getX() + 2, getY() + 2, getWidth() - 4);
            }
        };
    }

    public static Rect2i inventoryTabBounds(AbstractContainerScreen<?> inventory) {
        int left = inventory.getGuiLeft();
        int top = inventory.getGuiTop();
        int sideX = left - GEM_BUTTON_SIZE - 3;
        boolean recipeOpen = inventory instanceof InventoryScreen screen && screen.getRecipeBookComponent().isVisible();
        if (!recipeOpen && sideX >= 0) {
            return new Rect2i(sideX, top + 5, GEM_BUTTON_SIZE, GEM_BUTTON_SIZE);
        }
        return new Rect2i(left + 6, Math.max(0, top - GEM_BUTTON_SIZE - 3), GEM_BUTTON_SIZE, GEM_BUTTON_SIZE);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(340, width - 16);
        int panelHeight = Math.min(220, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        right = left + panelWidth;
        bottom = top + panelHeight;
        divider = left + panelWidth * 2 / 5;
        int buttonWidth = (panelWidth - 2 * PADDING - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.info.active"),
                        button -> minecraft.setScreen(new MagicalGirlSkillsScreen(this, true)))
                .bounds(left + PADDING, bottom - 27, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.info.passive"),
                        button -> minecraft.setScreen(new MagicalGirlSkillsScreen(this, false)))
                .bounds(left + PADDING + buttonWidth + 4, bottom - 27, buttonWidth, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.info.back"), button -> onClose())
                .bounds(right - PADDING - buttonWidth, bottom - 27, buttonWidth, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, right, bottom, 0xF21B1724);
        graphics.fill(divider, top + 27, divider + 1, bottom - 32, 0xFF4B4259);
        graphics.drawCenteredString(font, title, width / 2, top + 9, 0xFFFFFF);
        renderSoulGem(graphics);
        renderStatistics(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderSoulGem(GuiGraphics graphics) {
        int center = (left + divider) / 2;
        var theme = ClientMadokaState.getTheme();
        if (!ClientMadokaState.isContracted() || theme == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.info.uncontracted"),
                    center, top + 56, 0xD4C6DF);
            return;
        }
        int size = 52;
        SoulGemDisplay.render(graphics, center - size / 2, top + 32, size);
        graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.info.nature",
                        Component.translatableWithFallback(theme.getTranslationKey(), theme.getName())),
                center, top + 95, FastColor.ARGB32.lerp(0.45F, theme.getPrimaryColor(), 0xFFFFFF));
        double taint = ClientMadokaState.getTaint();
        String taintText = Component.translatable("screen.madoka_magic.info.taint", number(taint)).getString();
        int barWidth = Math.min(116, divider - left - 12);
        if (!ClientMadokaState.isWitchPath() && barWidth >= 42 && font.width(taintText) + 6 <= barWidth) {
            MagicResourceBar.render(graphics, font, center - barWidth / 2, top + 110, barWidth,
                    taint / PlayerMadokaData.TAINT_MAX, MagicResourceBar.Palette.TAINT, taintText);
        } else {
            graphics.drawCenteredString(font, taintText, center, top + 110, 0xDDD2E5);
        }
        String form = !ClientMadokaState.isTransformed() ? "human" : ClientMadokaState.isWitchPath() ? "witch" : "magical_girl";
        graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.info.form." + form),
                center, top + 126, 0xDDD2E5);
    }

    private void renderStatistics(GuiGraphics graphics) {
        int y = top + 30;
        boolean contracted = ClientMadokaState.isContracted();
        statistic(graphics, ClientMadokaState.isWitchPath() ? "despair" : "purity",
                contracted ? number(ClientMadokaState.getProgression()) + "%" : "—", y);
        statistic(graphics, "mana", contracted
                ? number(ClientMadokaState.getMana()) + " / " + number(ClientMadokaState.getMaxMana()) : "—", y += ROW_HEIGHT);
        statistic(graphics, "causality", number(ClientMadokaState.getCausality()), y += ROW_HEIGHT);
        statistic(graphics, "power", contracted ? number(ClientMadokaState.getPower() * 100.0D) + "%" : "—", y += ROW_HEIGHT);

        var defeated = ClientMadokaState.getDefeatedWitches();
        graphics.drawString(font, Component.translatable("screen.madoka_magic.info.witches", defeated.size()),
                divider + PADDING, y + ROW_HEIGHT + 4, 0xF1DAA8);
        int listTop = listTop();
        int listBottom = bottom - 32;
        if (defeated.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.madoka_magic.info.witches_empty"),
                    divider + PADDING, listTop + 2, 0xB7ACBF);
            return;
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
        graphics.enableScissor(divider + PADDING, listTop, right - PADDING, listBottom);
        for (int i = scroll, line = listTop + 2; i < defeated.size() && line < listBottom; i++, line += ROW_HEIGHT) {
            graphics.drawString(font, GriefSeedItem.witchName(defeated.get(i)), divider + PADDING, line, 0xDDD2E5);
        }
        graphics.disableScissor();
        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackHeight = listBottom - listTop;
            int thumbHeight = Math.max(6, trackHeight * visibleRows() / defeated.size());
            int thumbTop = listTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(right - 7, thumbTop, right - 4, thumbTop + thumbHeight, 0xFFA99AB6);
        }
    }

    private void statistic(GuiGraphics graphics, String key, String value, int y) {
        graphics.drawString(font, Component.translatable("screen.madoka_magic.info." + key), divider + PADDING, y, 0xDDD2E5);
        graphics.drawString(font, value, right - PADDING - font.width(value), y, 0xFFFFFF);
    }

    private int listTop() {
        return top + 30 + 5 * ROW_HEIGHT + 4;
    }

    private int visibleRows() {
        return Math.max(1, (bottom - 32 - listTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, ClientMadokaState.getDefeatedWitches().size() - visibleRows());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX > divider && mouseX < right && mouseY >= listTop() && mouseY < bottom - 32 && maxScroll() > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
