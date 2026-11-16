package io.madoka.magic.client;

import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** 契约确认界面。 */
public class ContractConfirmScreen extends Screen {

    private static final int LINE_HEIGHT = 12;
    private static final int BUTTON_HEIGHT = 20;
    private final MagicalGirl theme;
    private List<FormattedCharSequence> dialogue;
    private int contentTop;

    public ContractConfirmScreen(ItemStack contract) {
        super(Component.translatable("screen.madoka_magic.contract_confirm"));
        this.theme = MagicalGirlRegistry.get(ContractItem.getThemeId(contract));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int contentWidth = Math.min(300, this.width - 24);
        dialogue = font.split(Component.translatable("screen.madoka_magic.contract.hint_" + toneKey()), contentWidth);
        int contentHeight = 22 + (theme == null ? 0 : 5 * LINE_HEIGHT + 8) + dialogue.size() * LINE_HEIGHT;
        contentTop = Math.max(8, (this.height - contentHeight - 10 - BUTTON_HEIGHT) / 2);
        int buttonY = contentTop + contentHeight + 10;
        int buttonWidth = Math.min(112, (contentWidth - 8) / 2);
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.madoka_magic.contract.accept"),
                        b -> {
                            MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.ConfirmContract());
                            onClose();
                        })
                .bounds(centerX - buttonWidth - 4, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.madoka_magic.contract.decline"),
                        b -> onClose())
                .bounds(centerX + 4, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    /** 文案语气后缀：决绝 / 轻佻 / 压抑。 */
    private String toneKey() {
        if (theme == null) {
            return "devotion";
        }
        return switch (theme.getTone()) {
            case DECISIVE -> "devotion";
            case CASUAL -> "selfish";
            case PRESSED -> "pressed";
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);

        int centerX = this.width / 2;
        graphics.drawCenteredString(font, title, centerX, contentTop, 0xFFFFFF);
        int y = contentTop + 22;
        if (theme != null) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.madoka_magic.contract.theme",
                            theme.getDisplayName(), theme.getClassification()),
                    centerX, y, theme.getPrimaryColor());
            drawModifier(graphics, "damage_dealt", theme.getDamageDealt(), y += LINE_HEIGHT);
            drawModifier(graphics, "damage_taken", theme.getDamageTaken(), y += LINE_HEIGHT);
            drawModifier(graphics, "taint_rate", theme.getTaintRate(), y += LINE_HEIGHT);
            drawModifier(graphics, "mana_gain", theme.getManaGain(), y += LINE_HEIGHT);
            y += LINE_HEIGHT + 8;
        }
        for (FormattedCharSequence line : dialogue) {
            graphics.drawCenteredString(font, line, centerX, y, 0xFFFFFF);
            y += LINE_HEIGHT;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawModifier(GuiGraphics graphics, String key, double multiplier, int y) {
        int percent = (int) Math.round((multiplier - 1.0D) * 100.0D);
        String signed = percent > 0 ? "+" + percent : Integer.toString(percent);
        graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.contract." + key, signed),
                width / 2, y, 0xFFD9A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
