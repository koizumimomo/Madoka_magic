package io.madoka.magic.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.ProgressionState;
import io.madoka.magic.spell.SpellDefinition;
import io.madoka.magic.spell.SpellRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** A pannable constellation. Branch intersections require investment in both parent paths. */
public final class PassiveTreeScreen extends Screen {
    private final Screen parent;
    private final Map<String, Button> learningButtons = new HashMap<>();
    private double zoom = 1;
    private double panX;
    private double panY;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public PassiveTreeScreen(Screen parent) {
        super(Component.translatable("screen.madoka_magic.talents.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(540, width - 12);
        panelHeight = Math.min(350, height - 12);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        learningButtons.clear();
        MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.RequestSync());
        for (var node : ClientProgression.nodes()) {
            String id = node.getString("id");
            var builder = Button.builder(Component.translatable(node.getString("nameKey")), button ->
                    MadokaNetwork.CHANNEL.sendToServer(new ProgressionState.LearnTalent(id))).bounds(0, 0, 12, 12);
            var button = new Button(builder) {
                @Override
                protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
            };
            learningButtons.put(id, addRenderableWidget(button));
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.info.back"), button -> onClose())
                .bounds(width / 2 - 36, top + panelHeight - 23, 72, 17).build());
    }

    private double scale() {
        return zoom * Math.min((panelWidth - 30) / 350.0, (panelHeight - 84) / 330.0);
    }

    private int x(double value) {
        return (int) Math.round(width / 2.0 + panX + value * scale());
    }

    private int y(double value) {
        return (int) Math.round(top + 47 + (panelHeight - 77) / 2.0 + panY + (value + 50) * scale());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xf511101e);
        graphics.drawCenteredString(font, title, width / 2, top + 7, 0xffefe1f5);
        graphics.drawCenteredString(font, Component.translatable("screen.madoka_magic.talents.points",
                ClientProgression.state().getInt("points"), ClientProgression.state().getInt("interval")),
                width / 2, top + 21, 0xffd6bc83);
        var nodes = ClientProgression.nodes();
        if (nodes.stream().anyMatch(node -> !learningButtons.containsKey(node.getString("id")))) {
            rebuildWidgets();
            return;
        }
        var byId = new HashMap<String, CompoundTag>();
        nodes.forEach(node -> byId.put(node.getString("id"), node));
        CompoundTag hovered = null;
        graphics.enableScissor(left + 3, top + 37, left + panelWidth - 3, top + panelHeight - 28);
        for (int i = 0; i < 55; i++) {
            int sx = left + 7 + (i * 89 + i * i * 17) % Math.max(1, panelWidth - 14);
            int sy = top + 40 + (i * 47 + i * i * 7) % Math.max(1, panelHeight - 74);
            graphics.fill(sx, sy, sx + 1, sy + 1, i % 5 == 0 ? 0xff686075 : 0xff302b40);
        }
        for (var node : nodes) {
            var requires = node.getCompound("requires");
            boolean learned = node.getInt("rank") > 0;
            int color = learned ? 0xffaf8fc8 : 0xff44374e;
            if (requires.isEmpty()) line(graphics, x(0), y(78), x(node.getDouble("x")), y(node.getDouble("y")), color);
            for (String id : requires.getAllKeys()) {
                var from = byId.get(id);
                if (from != null) line(graphics, x(from.getDouble("x")), y(from.getDouble("y")),
                        x(node.getDouble("x")), y(node.getDouble("y")), color);
            }
        }
        for (var node : nodes) {
            int sx = x(node.getDouble("x"));
            int sy = y(node.getDouble("y"));
            boolean passive = !node.getString("passive").isEmpty();
            var icon = passiveIcon(node);
            int radius = passive || node.getString("id").equals("soul_spark") ? 5 : 3;
            var button = learningButtons.get(node.getString("id"));
            int hitRadius = icon == null ? 6 : 10;
            button.setWidth(hitRadius * 2);
            button.setHeight(hitRadius * 2);
            button.setX(sx - hitRadius);
            button.setY(sy - hitRadius);
            button.active = node.getBoolean("canLearn");
            button.visible = sx > left + 5 && sx < left + panelWidth - 5 && sy > top + 42 && sy < top + panelHeight - 33;
            int color = node.getInt("rank") > 0 ? 0xfff6dca5 : button.active ? 0xffcfb4e7 : 0xff5a4a69;
            if (passive) {
                int frameRadius = icon == null ? 8 : 13;
                line(graphics, sx, sy - frameRadius, sx + frameRadius, sy, color);
                line(graphics, sx + frameRadius, sy, sx, sy + frameRadius, color);
                line(graphics, sx, sy + frameRadius, sx - frameRadius, sy, color);
                line(graphics, sx - frameRadius, sy, sx, sy - frameRadius, color);
            }
            if (icon == null) {
                star(graphics, sx, sy, radius, color);
            } else {
                graphics.fill(sx - 9, sy - 9, sx + 9, sy + 9, 0xff211929);
                float brightness = node.getInt("rank") > 0 || button.active ? 1 : 0.45F;
                graphics.setColor(brightness, brightness, brightness, 1);
                RenderSystem.enableBlend();
                graphics.blit(icon.icon(), sx - 8, sy - 8, 16, 16, 0, 0, 32, 32, 32, 32);
                RenderSystem.disableBlend();
                graphics.setColor(1, 1, 1, 1);
            }
            if (button.visible && (Math.abs(mouseX - sx) <= hitRadius && Math.abs(mouseY - sy) <= hitRadius || button.isFocused())) {
                hovered = node;
                if (icon == null) graphics.fill(sx - radius - 1, sy - 1, sx + radius + 2, sy, 0xfff9f0ff);
            }
        }
        branchLabel(graphics, "quick_cast", "cooldown", -162, -176, nodes);
        branchLabel(graphics, "spell_power", "power", -26, -193, nodes);
        branchLabel(graphics, "clear_soul", "taint", 90, -176, nodes);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            int sx = x(hovered.getDouble("x"));
            int sy = y(hovered.getDouble("y"));
            int hitRadius = passiveIcon(hovered) == null ? 6 : 10;
            boolean mouseOver = Math.abs(mouseX - sx) <= hitRadius && Math.abs(mouseY - sy) <= hitRadius;
            graphics.renderComponentTooltip(font, tooltip(hovered, byId), mouseOver ? mouseX : sx, mouseOver ? mouseY : sy);
        }
    }

    private void branchLabel(GuiGraphics graphics, String id, String effect, double dx, double dy, List<CompoundTag> nodes) {
        double bonus = nodes.stream().filter(node -> effect.equals(node.getString("effect")))
                .mapToDouble(node -> node.getDouble("perRank") * node.getInt("rank")).sum();
        graphics.drawString(font, Component.translatable("talent.madoka_magic." + id).getString() + " " + percent(bonus),
                x(dx), y(dy), 0xffbca9ca, false);
    }

    private List<Component> tooltip(CompoundTag node, Map<String, CompoundTag> byId) {
        var lines = new ArrayList<Component>();
        String key = node.getString("nameKey");
        lines.add(nodeName(node).append(" " + node.getInt("rank") + "/" + node.getInt("max")));
        lines.add(Component.translatable(key + ".desc", percent(node.getDouble("perRank"))));
        if (node.getInt("rank") < node.getInt("max")) {
            lines.add(Component.translatable("screen.madoka_magic.talents.next", percent(node.getDouble("perRank") * node.getInt("rank")),
                    percent(node.getDouble("perRank") * (node.getInt("rank") + 1))));
        }
        var requires = node.getCompound("requires");
        for (String id : requires.getAllKeys()) {
            var from = byId.get(id);
            if (from != null) lines.add(Component.translatable("screen.madoka_magic.talents.requires",
                    nodeName(from), requires.getInt(id)));
        }
        lines.add(Component.translatable(node.getInt("rank") >= node.getInt("max")
                ? "screen.madoka_magic.talents.learned" : "screen.madoka_magic.talents.cost"));
        return lines;
    }

    private static SpellDefinition passiveIcon(CompoundTag node) {
        String passive = node.getString("passive");
        if (passive.equals("signature")) {
            var theme = ClientMadokaState.getTheme();
            if (theme == null) return null;
            passive = theme.getPassives(ClientMadokaState.isWitchPath()).stream()
                    .filter(id -> !id.equals("obsession") && !id.equals("ember"))
                    .findFirst().orElse("");
        }
        return SpellRegistry.get(passive);
    }

    private static net.minecraft.network.chat.MutableComponent nodeName(CompoundTag node) {
        var name = Component.translatable(node.getString("nameKey"));
        String id = node.getString("id");
        for (String branch : List.of("quick_cast", "spell_power", "clear_soul")) {
            if (id.equals(branch)) return name.append(" · 1");
            if (id.startsWith(branch + "_")) return name.append(" · " + id.substring(branch.length() + 1));
        }
        return name;
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value * 100);
    }

    private static void star(GuiGraphics graphics, int x, int y, int radius, int color) {
        graphics.fill(x, y - radius, x + 1, y + radius + 1, color);
        graphics.fill(x - radius, y, x + radius + 1, y + 1, color);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= length; i++) {
            double progress = length == 0 ? 0 : (double) i / length;
            int x = (int) Math.round(Mth.lerp(progress, x0, x1));
            int y = (int) Math.round(Mth.lerp(progress, y0, y1));
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && mouseY > top + 37 && mouseY < top + panelHeight - 28) {
            panX = Mth.clamp(panX + deltaX, -panelWidth, panelWidth);
            panY = Mth.clamp(panY + deltaY, -panelHeight, panelHeight);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        zoom = Mth.clamp(zoom * Math.pow(1.15, delta), 0.6, 2.8);
        return true;
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
