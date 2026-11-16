package io.madoka.magic.client;

import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 魔女化后的去留选择。
 *
 * <p>保留身份 → 回去打自己的魔女化身，可尝试半魔女化或增强自身；
 * 放弃身份 → 重新做一张别的契约（之后也可以再回来）。</p>
 */
public class WitchChoiceScreen extends Screen {

    public WitchChoiceScreen() {
        super(Component.translatable("screen.madoka_magic.witch_choice"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int buttonY = this.height / 2 + 20;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("screen.madoka_magic.witch_choice.keep"),
                        b -> {
                            MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.WitchChoice(true));
                            onClose();
                        })
                .bounds(centerX - 152, buttonY, 150, 20)
                .build());

        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                        Component.translatable("screen.madoka_magic.witch_choice.drop"),
                        b -> {
                            MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.WitchChoice(false));
                            onClose();
                        })
                .bounds(centerX + 2, buttonY, 150, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.madoka_magic.witch_choice.title"),
                centerX, this.height / 2 - 26, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.madoka_magic.witch_choice.desc"),
                centerX, this.height / 2 - 10, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
