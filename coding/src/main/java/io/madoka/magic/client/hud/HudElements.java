package io.madoka.magic.client.hud;

import io.madoka.magic.client.ClientMadokaState;
import io.madoka.magic.client.ClientProgression;
import io.madoka.magic.client.SoulGemDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 现有 HUD 元素。咒具叠层等新元素以后在这里注册。 */
public final class HudElements {

    private HudElements() {}

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
            int rows = Math.max(1, (Minecraft.getInstance().getWindow().getGuiScaledHeight() - 16) / 25);
            int index = 0;
            for (String id : theme.getSkills(ClientMadokaState.isWitchPath())) {
                double remaining = ClientProgression.cooldown(id, 0.0F);
                if (remaining <= 0) {
                    continue;
                }
                var spell = io.madoka.magic.spell.SpellRegistry.get(id);
                if (spell == null || !spell.active()) {
                    continue;
                }
                int rowY = y - index % rows * 25;
                int columnX = x + index / rows * 125;
                if (rowY < 4) {
                    break;
                }
                graphics.fill(columnX, rowY, columnX + 119, rowY + 23, 0xcc1b1724);
                graphics.blit(spell.icon(), columnX + 2, rowY + 2, 19, 19, 0, 0, 32, 32, 32, 32);
                int overlay = (int) Math.ceil(19 * Math.min(1, remaining / ClientProgression.cooldownLength(id)));
                graphics.fill(columnX + 2, rowY + 21 - overlay, columnX + 21, rowY + 21, 0xa015101e);
                String time = String.format(java.util.Locale.ROOT, remaining >= 200 ? "%.0fs" : "%.1fs", remaining / 20);
                String name = font.plainSubstrByWidth(Component.translatable(spell.nameKey()).getString(), 65);
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
