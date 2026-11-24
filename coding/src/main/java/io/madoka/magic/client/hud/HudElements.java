package io.madoka.magic.client.hud;

import io.madoka.magic.client.ClientMadokaState;
import io.madoka.magic.client.ClientProgression;
import io.madoka.magic.client.SoulGemDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 现有 HUD 元素。咒具叠层与咒债元素在这里注册。 */
public final class HudElements {

    private HudElements() {}

    public static final class CurseDebt implements HudElement {
        @Override public String id() { return "curse_debt"; }
        @Override public HudAnchor defaultAnchor() { return HudAnchor.BOTTOM_LEFT; }
        @Override public int defaultOffsetX() { return 12; }
        @Override public int defaultOffsetY() { return 36; }
        @Override public int width() { return io.madoka.magic.client.renderer.CurseDebtHud.WIDTH; }
        @Override public int height() { return io.madoka.magic.client.renderer.CurseDebtHud.HEIGHT; }
        @Override public boolean visible() { return io.madoka.magic.client.ClientCurseDebt.visible(); }
        @Override public void render(GuiGraphics graphics, int x, int y) {
            io.madoka.magic.client.renderer.CurseDebtHud.render(graphics, x, y, false);
        }
        @Override public void renderPreview(GuiGraphics graphics, int x, int y) {
            io.madoka.magic.client.renderer.CurseDebtHud.render(graphics, x, y, true);
        }
    }

    public static final class CurseStacks implements HudElement {
        @Override public String id() { return "curse_stacks"; }
        @Override public HudAnchor defaultAnchor() { return HudAnchor.BOTTOM_LEFT; }
        @Override public int defaultOffsetX() { return 12; }
        @Override public int defaultOffsetY() { return 78; }
        @Override public int width() { return io.madoka.magic.client.renderer.CurseStackHud.WIDTH; }
        @Override public int height() { return io.madoka.magic.client.renderer.CurseStackHud.HEIGHT; }
        @Override public boolean visible() { return io.madoka.magic.client.ClientCurseStacks.view() != null; }
        @Override public void render(GuiGraphics graphics, int x, int y) {
            io.madoka.magic.client.renderer.CurseStackHud.render(graphics, x, y, false);
        }
        @Override public void renderPreview(GuiGraphics graphics, int x, int y) {
            io.madoka.magic.client.renderer.CurseStackHud.render(graphics, x, y, true);
        }
    }

    /** 左下角灵魂宝石。 */
    public static final class SoulGem implements HudElement {
        @Override
        public String id() {
            return "soulgem";
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.BOTTOM_CENTER;
        }

        @Override
        public int defaultOffsetX() {
            return -100;
        }

        @Override
        public int defaultOffsetY() {
            return 0;
        }

        @Override
        public int width() {
            return 22;
        }

        @Override
        public int height() {
            return 22;
        }

        @Override
        public boolean visible() {
            return ClientMadokaState.isContracted();
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            SoulGemDisplay.render(graphics, x, y, 22);
        }
    }

    /** 正在冷却的主动技能与咒具，从锚点向上堆叠。 */
    public static final class Cooldowns implements HudElement {
        @Override
        public String id() {
            return "cooldowns";
        }

        @Override
        public HudAnchor defaultAnchor() {
            return HudAnchor.BOTTOM_CENTER;
        }

        @Override
        public int defaultOffsetX() {
            return -164;
        }

        @Override
        public int defaultOffsetY() {
            return 28;
        }

        @Override
        public int width() {
            return 119;
        }

        @Override
        public int height() {
            return 23;
        }

        @Override
        public boolean visible() {
            return ClientMadokaState.isTransformed() && ClientMadokaState.getTheme() != null;
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            var theme = ClientMadokaState.getTheme();
            if (theme == null) {
                return;
            }
            var font = Minecraft.getInstance().font;
            float scale = Math.max(0.5f, graphics.pose().last().pose().m11());
            int rows = Math.max(1, 1 + (int) ((y - 4) / (25 * scale)));
            record Entry(String id, net.minecraft.resources.ResourceLocation icon, String nameKey) {}
            var entries = new java.util.ArrayList<Entry>();
            for (String id : theme.getSkills(ClientMadokaState.isWitchPath())) {
                var spell = io.madoka.magic.spell.SpellRegistry.get(id);
                if (spell != null && spell.active()) entries.add(new Entry(id,spell.icon(),spell.nameKey()));
            }
            entries.add(new Entry(io.madoka.magic.spell.OriginalCurseEffects.COOLDOWN_ID,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("madoka_magic","textures/item/curse/atonement.png"),
                    "tooltip.madoka_magic.curse.ward"));
            int index = 0;
            for (var entry : entries) {
                String id = entry.id();
                double remaining = ClientProgression.cooldown(id, 0.0F);
                if (remaining <= 0) {
                    continue;
                }
                int rowY = y - index % rows * 25;
                int columnX = x + index / rows * 125;
                if (rowY < 0) {
                    break;
                }
                graphics.fill(columnX, rowY, columnX + 119, rowY + 23, 0xcc1b1724);
                graphics.blit(entry.icon(), columnX + 2, rowY + 2, 19, 19, 0, 0, 32, 32, 32, 32);
                int overlay = (int) Math.ceil(19 * Math.min(1, remaining / ClientProgression.cooldownLength(id)));
                graphics.fill(columnX + 2, rowY + 21 - overlay, columnX + 21, rowY + 21, 0xa015101e);
                String time = String.format(java.util.Locale.ROOT, remaining >= 200 ? "%.0fs" : "%.1fs", remaining / 20);
                String name = font.plainSubstrByWidth(Component.translatable(entry.nameKey()).getString(), 65);
                graphics.drawString(font, name, columnX + 25, rowY + 3, 0xffdfd1ea, false);
                graphics.drawString(font, time, columnX + 116 - font.width(time), rowY + 3, 0xffe9c883, false);
                graphics.fill(columnX + 25, rowY + 17, columnX + 116, rowY + 20, 0xff49394f);
                graphics.fill(columnX + 25, rowY + 17, columnX + 25
                        + (int) Math.ceil(91 * Math.min(1, remaining / ClientProgression.cooldownLength(id))),
                        rowY + 20, 0xffbb92d0);
                index++;
            }
        }
    }
}
