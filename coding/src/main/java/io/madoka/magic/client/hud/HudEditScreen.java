package io.madoka.magic.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HUD 编辑界面：拖动元素调整位置，R 重置，ESC 保存。
 *
 * <p>参考花仪绽放的 HudEditScreen：解锁鼠标、直接拖，关闭时写回配置。</p>
 */
public class HudEditScreen extends Screen {

    private String dragging;
    private double lastMouseX;
    private double lastMouseY;

    public HudEditScreen() {
        super(Component.translatable("screen.madoka_magic.hud_edit"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x80000000);
        HudLayout.renderEdit(graphics, width, height);
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font,
                Component.translatable("screen.madoka_magic.hud_edit.hint"),
                width / 2, height - 20, 0xDDD2E5);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            String hit = HudLayout.hitTest(width, height, mouseX, mouseY);
            if (hit != null) {
                HudLayout.toggle(hit);
                HudLayout.save();
                return true;
            }
        }
        if (button == 0) {
            String hit = HudLayout.hitTest(width, height, mouseX, mouseY);
            if (hit != null) {
                dragging = hit;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging != null && button == 0) {
            int dx = (int) Math.round(mouseX - lastMouseX);
            int dy = (int) Math.round(mouseY - lastMouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            if (dx != 0 || dy != 0) {
                HudLayout.move(dragging, dx, dy);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging != null) {
            dragging = null;
            HudLayout.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        String hit = HudLayout.hitTest(width, height, mouseX, mouseY);
        if (hit != null) {
            HudLayout.resize(hit, (float) Math.signum(delta) * 0.1f);
            HudLayout.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            HudLayout.reset();
            HudLayout.save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        HudLayout.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
