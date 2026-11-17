package io.madoka.magic.client;

import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试工具：选择一位魔女，确认后在脚下生成她的结界入口传送门。
 *
 * <p>列表为全部有结界的主题（含装了灾变时的九大 Boss），按钮文案格式
 * 「[性质]的魔女·魔女名」。</p>
 */
public class WitchPortalTestScreen extends Screen {
    private static final int COLUMNS = 2;
    private static final int BUTTON_WIDTH = 165;
    private static final int BUTTON_HEIGHT = 18;
    private static final int TOP = 30;

    private final List<String> themes = new ArrayList<>();
    private String selected = "";
    private final List<Button> buttons = new ArrayList<>();

    public WitchPortalTestScreen() {
        super(Component.translatable("screen.madoka_magic.witch_portal_test"));
    }

    @Override
    protected void init() {
        themes.clear();
        for (String theme : MadokaDimensions.BARRIERS) {
            // 胡桃夹子的魔女尚未实装，不开放选择
            if (!WitchTypes.NUTCRACKER.equals(theme)) {
                themes.add(theme);
            }
        }
        themes.addAll(CataclysmCompat.barrierThemes());

        int centerX = this.width / 2;
        int gridWidth = COLUMNS * BUTTON_WIDTH + 8;
        buttons.clear();
        for (int i = 0; i < themes.size(); i++) {
            String theme = themes.get(i);
            int column = i % COLUMNS;
            int row = i / COLUMNS;
            Button button = addRenderableWidget(Button.builder(label(theme), b -> select(theme))
                    .bounds(centerX - gridWidth / 2 + column * (BUTTON_WIDTH + 8),
                            TOP + row * (BUTTON_HEIGHT + 3), BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            buttons.add(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.witch_portal_test.confirm"),
                        b -> {
                            if (!selected.isEmpty()) {
                                MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.SpawnWitchPortal(selected));
                            }
                            onClose();
                        })
                .bounds(centerX - 76, TOP + rowCount() * (BUTTON_HEIGHT + 3) + 6, 74, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.madoka_magic.witch_portal_test.cancel"),
                        b -> onClose())
                .bounds(centerX + 2, TOP + rowCount() * (BUTTON_HEIGHT + 3) + 6, 74, 20)
                .build());
        refresh();
    }

    private int rowCount() {
        return (themes.size() + COLUMNS - 1) / COLUMNS;
    }

    private void select(String theme) {
        selected = theme;
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = !themes.get(i).equals(selected);
        }
    }

    /** 「[性质]的魔女·魔女名」；没有性质后缀的主题（如原版名 fallback）原样显示。 */
    private static Component label(String theme) {
        String name = GriefSeedItem.witchName(theme).getString();
        if (name.endsWith("的魔女")) {
            name = "[" + name.substring(0, name.length() - "的魔女".length()) + "]的魔女";
        }
        String runes = Component.translatableWithFallback("witch.madoka_magic." + theme + ".runes", "").getString();
        return Component.literal(runes.isEmpty() ? name : name + "·" + runes);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
