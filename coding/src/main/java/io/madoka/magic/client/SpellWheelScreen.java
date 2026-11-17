package io.madoka.magic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.spell.SpellDefinition;
import io.madoka.magic.spell.SpellRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Hold the mapped key, move the pointer to a sector and release to cast. */
final class SpellWheelScreen extends Screen {
    private static final double TURN = 2.0D * Math.PI;
    private static final int ICON_SIZE = 20;
    private static final int ICON_TEXTURE_SIZE = 32;
    private static final int WHEEL_TEXTURE_SIZE = 192;
    private static final ResourceLocation WHEEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "textures/gui/spell_wheel.png");
    private final List<SpellDefinition> spells;
    private final int accent;
    private float outerRadius;
    private float innerRadius;
    private boolean sidePanel;

    SpellWheelScreen() {
        super(Component.translatable("screen.madoka_magic.spell_wheel"));
        var theme = ClientMadokaState.getTheme();
        spells = theme == null ? List.of() : theme.getSkills(ClientMadokaState.isWitchPath()).stream().distinct()
                .filter(ClientMadokaState.getUnlockedSpells()::contains).map(SpellRegistry::get)
                .filter(Objects::nonNull).filter(SpellDefinition::active).toList();
        accent = theme == null ? 0xC7A9DE : FastColor.ARGB32.lerp(0.4F, theme.getPrimaryColor(), 0xFFFFFF);
    }

    @Override
    protected void init() {
        sidePanel = width >= 500;
        int reserve = sidePanel ? 35 : 66;
        outerRadius = Math.max(20, Math.min(78, Math.min(width / 2 - 20, height / 2 - reserve)));
        innerRadius = outerRadius * 0.49F;
        SkillDetails.refresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        if (spells.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.spell_wheel.empty"),
                    cx, cy - 4, 0xFFFFFF);
            return;
        }

        int selected = selection(mouseX, mouseY);
        int radius = (int) outerRadius;
        graphics.blit(WHEEL_TEXTURE, cx - radius, cy - radius, radius * 2, radius * 2,
                0, 0, WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE, WHEEL_TEXTURE_SIZE);
        drawSectors(graphics, cx, cy, selected);
        double step = TURN / spells.size();
        float iconRadius = (innerRadius + outerRadius) / 2.0F;
        for (int i = 0; i < spells.size(); i++) {
            int x = cx + (int) Math.round(Math.sin(i * step) * iconRadius) - ICON_SIZE / 2;
            int y = cy - (int) Math.round(Math.cos(i * step) * iconRadius) - ICON_SIZE / 2;
            graphics.blit(spells.get(i).icon(), x, y, ICON_SIZE, ICON_SIZE,
                    0, 0, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        }

        if (selected < 0) {
            graphics.drawCenteredString(font, title, cx, cy - radius - 26, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.spell_wheel.cancel"),
                    cx, cy - radius - 14, 0xE2D7EF);
        } else {
            drawDetails(graphics, spells.get(selected), cx, cy, radius);
        }
    }

    private void drawDetails(GuiGraphics graphics, SpellDefinition spell, int cx, int cy, int radius) {
        Component hint = Component.translatable("screen.madoka_magic.spell_wheel.cast");
        if (sidePanel) {
            graphics.drawCenteredString(font, hint, cx, cy - radius - 14, 0xE2D7EF);
        }
        int panelWidth = sidePanel ? Math.min(188, width - cx - radius - 16) : Math.min(322, width - 16);
        List<FormattedCharSequence> lines = new ArrayList<>();
        Component desc = Component.translatableWithFallback(spell.nameKey() + ".short",
                Component.translatable(spell.descKey()).getString());
        lines.addAll(font.split(desc, panelWidth - 16));
        for (Component component : SkillDetails.numbers(spell, true, hasShiftDown())) {
            lines.addAll(font.split(component, panelWidth - 16));
        }
        if (!sidePanel) {
            lines.addAll(font.split(hint, panelWidth - 16));
        }
        int panelHeight = 24 + lines.size() * 11;
        int x = sidePanel ? cx + radius + 10 : cx - panelWidth / 2;
        int y = sidePanel ? Math.max(4, cy - panelHeight / 2) : Math.max(4, cy - radius - panelHeight - 4);
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xE7946F9D);
        graphics.fill(x + 1, y + 1, x + panelWidth - 1, y + panelHeight - 1, 0xF4231B30);
        graphics.drawString(font, Component.translatable(spell.nameKey()), x + 8, y + 7, 0xFFFFFF);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), x + 8, y + 19 + 11 * index, 0xDFD6EC);
        }
    }

    private int selection(double mouseX, double mouseY) {
        double dx = mouseX - width / 2;
        double dy = mouseY - height / 2;
        if (spells.isEmpty() || dx * dx + dy * dy < innerRadius * innerRadius) {
            return -1;
        }
        double angle = Math.atan2(dx, -dy);
        double step = TURN / spells.size();
        return Math.floorMod((int) Math.floor(angle / step + 0.5D), spells.size());
    }

    private void drawSectors(GuiGraphics graphics, int cx, int cy, int selected) {
        if (selected < 0 && spells.size() == 1) {
            return;
        }
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        double step = TURN / spells.size();
        double gap = spells.size() == 1 ? 0.0D : 0.035D;
        int sections = (int) Math.ceil(52.0D / spells.size());
        float outer = outerRadius * 0.91F;
        float inner = outerRadius * 0.57F;
        for (int i = 0; i < spells.size(); i++) {
            double edge = (i - 0.5D) * step;
            if (i == selected) {
                double arc = step - 2.0D * gap;
                for (int segment = 0; segment < sections; segment++) {
                    double a = edge + gap + arc * segment / sections;
                    double b = edge + gap + arc * (segment + 1) / sections;
                    sector(buffer, matrix, cx, cy, a, b, inner, outer, 0xA0000000 | accent);
                }
            }
            if (gap > 0.0D) {
                sector(buffer, matrix, cx, cy, edge - gap, edge + gap, inner, outer, 0xDD0D0A18);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void sector(BufferBuilder buffer, Matrix4f matrix, int cx, int cy, double a, double b,
                               float inner, float outer, int color) {
        vertex(buffer, matrix, cx, cy, a, outer, color);
        vertex(buffer, matrix, cx, cy, a, inner, color);
        vertex(buffer, matrix, cx, cy, b, inner, color);
        vertex(buffer, matrix, cx, cy, a, outer, color);
        vertex(buffer, matrix, cx, cy, b, inner, color);
        vertex(buffer, matrix, cx, cy, b, outer, color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, int cx, int cy,
                               double angle, float radius, int color) {
        buffer.vertex(matrix, cx + (float) Math.sin(angle) * radius, cy - (float) Math.cos(angle) * radius, 0)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (!MadokaKeys.OPEN_WHEEL.matches(key, scanCode)) {
            return super.keyReleased(key, scanCode, modifiers);
        }
        var window = minecraft.getWindow();
        release(minecraft.mouseHandler.xpos() * width / window.getScreenWidth(),
                minecraft.mouseHandler.ypos() * height / window.getScreenHeight());
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!MadokaKeys.OPEN_WHEEL.matchesMouse(button)) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        release(mouseX, mouseY);
        return true;
    }

    private void release(double x, double y) {
        int selected = selection(x, y);
        if (selected >= 0 && canCast()) {
            var player = minecraft.player;
            // 手持折页施放技能时播放挥剑动画
            if (player != null && io.madoka.magic.item.ContractWeapon.of(player.getMainHandItem())
                    == io.madoka.magic.item.ContractWeapon.BUTTERFLY) {
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.CastSpell(spells.get(selected).id(), hasShiftDown()));
        }
        onClose();
    }

    private boolean canCast() {
        return minecraft.isWindowActive() && minecraft.getConnection() != null && minecraft.player != null
                && minecraft.player.isAlive() && !minecraft.player.isSpectator() && ClientMadokaState.isTransformed();
    }

    @Override
    public void tick() {
        if (!canCast()) {
            onClose();
        } else {
            SkillDetails.refresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
