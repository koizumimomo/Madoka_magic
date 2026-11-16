package io.madoka.magic.client;

import io.madoka.magic.spell.SpellDefinition;
import io.madoka.magic.spell.SpellRegistry;
import io.madoka.magic.data.MagicalGirl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.Objects;

final class MagicalGirlSkillsScreen extends Screen {
    private final Screen parent;
    private final boolean active;
    private int left;
    private int top;
    private int panelWidth;
    private int bottom;
    private int contentHeight;
    private int scroll;

    MagicalGirlSkillsScreen(Screen parent, boolean active) {
        super(Component.translatable("screen.madoka_magic.info." + (active ? "active" : "passive")));
        this.parent = parent;
        this.active = active;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(362, width - 16);
        left = (width - panelWidth) / 2;
        top = Math.max(6, (height - 236) / 2);
        bottom = height - top;
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.info.back_to_profile"), button -> onClose())
                .bounds(width / 2 - 48, bottom - 24, 96, 18).build());
        SkillDetails.refresh();
    }

    @Override
    public void tick() {
        SkillDetails.refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + panelWidth, bottom, 0xF21B1724);
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xF5E6FA);
        var theme = ClientMadokaState.getTheme();
        if (theme == null || !ClientMadokaState.isContracted()) {
            graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.info.uncontracted"),
                    width / 2, top + 34, 0xD4C6DF);
        } else {
            drawSkills(graphics, theme);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawSkills(GuiGraphics graphics, MagicalGirl theme) {
        boolean witch = ClientMadokaState.isWitchPath();
        var spells = (active ? theme.getSkills(witch) : theme.getPassives(witch)).stream().distinct()
                .map(SpellRegistry::get).filter(Objects::nonNull).filter(spell -> spell.active() == active).toList();
        int x = left + 10;
        int listTop = top + 23;
        int listBottom = bottom - 30;
        int y = listTop - scroll;
        graphics.enableScissor(x, listTop, left + panelWidth - 10, listBottom);
        for (SpellDefinition spell : spells) {
            y = drawSkill(graphics, spell, x, y);
        }
        graphics.disableScissor();
        contentHeight = y - (listTop - scroll);
        scroll = Math.min(scroll, maxScroll());
        if (maxScroll() > 0) {
            int visibleHeight = listBottom - listTop;
            int thumb = Math.max(8, visibleHeight * visibleHeight / contentHeight);
            int thumbY = listTop + (visibleHeight - thumb) * scroll / maxScroll();
            graphics.fill(left + panelWidth - 8, thumbY, left + panelWidth - 5, thumbY + thumb, 0xFF9F89AC);
        }
    }

    private int drawSkill(GuiGraphics graphics, SpellDefinition spell, int x, int y) {
        int purity = SkillDetails.unlockPurity(spell);
        boolean unlocked = !active || ClientMadokaState.getUnlockedSpells().contains(spell.id());
        int textX = x + 26;
        graphics.blit(spell.icon(), x + 1, y + 2, 20, 20, 0, 0, 32, 32, 32, 32);
        graphics.drawString(font, Component.translatable(spell.nameKey()), textX, y + 2, 0xFFFFFF);
        String status = "screen.madoka_magic.info." + (!active ? "passive_status" : unlocked ? "unlocked" : "locked")
                + (ClientMadokaState.isWitchPath() ? ".witch" : "");
        int lineY = drawWrapped(graphics, Component.translatable(status, purity), textX, y + 14,
                unlocked ? 0xA7DDAF : 0xF2BF89);
        lineY = drawWrapped(graphics, Component.translatable(spell.descKey()), textX, lineY, 0xE5DDEA);
        for (Component number : SkillDetails.numbers(spell, false, false)) {
            lineY = drawWrapped(graphics, number, textX, lineY, 0xB7CEE9);
        }
        graphics.fill(x, lineY + 3, left + panelWidth - 11, lineY + 4, 0xFF4B4259);
        return lineY + 10;
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int x, int y, int color) {
        for (FormattedCharSequence line : font.split(text, panelWidth - 50)) {
            graphics.drawString(font, line, x, y, color);
            y += 11;
        }
        return y;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bottom - 30 - top - 23));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX <= left + panelWidth && mouseY >= top + 23 && mouseY <= bottom - 30) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 23, 0, maxScroll());
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
}
