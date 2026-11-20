package io.madoka.magic.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD 布局：元素注册、位置配置与渲染。
 *
 * <p>配置放在 {@code config/madoka_magic-hud.json}，按元素 id 存锚点、偏移、缩放和显隐。</p>
 */
public final class HudLayout {

    private static final Map<String, HudElement> ELEMENTS = new LinkedHashMap<>();
    private static final Map<String, HudPlacement> PLACEMENTS = new LinkedHashMap<>();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("madoka_magic-hud.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        register(new HudElements.SoulGem());
        register(new HudElements.Cooldowns());
        load();
    }

    private HudLayout() {}

    public static void register(HudElement element) {
        ELEMENTS.put(element.id(), element);
        PLACEMENTS.putIfAbsent(element.id(), new HudPlacement(element.defaultAnchor(),
                element.defaultOffsetX(), element.defaultOffsetY(), 1.0F, true));
    }

    public static Map<String, HudElement> elements() {
        return ELEMENTS;
    }

    public static HudPlacement placement(String id) {
        return PLACEMENTS.get(id);
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        for (HudElement element : ELEMENTS.values()) {
            HudPlacement placement = PLACEMENTS.get(element.id());
            if (placement == null || !placement.visible() || !element.visible()) {
                continue;
            }
            int x = placement.anchor().x(screenWidth, element.width(), placement.offsetX());
            int y = placement.anchor().y(screenHeight, element.height(), placement.offsetY());
            element.render(graphics, x, y);
        }
    }

    /** 编辑界面用：所有元素都画框，不管当前状态是否可见。 */
    public static void renderEdit(GuiGraphics graphics, int screenWidth, int screenHeight) {
        var font = Minecraft.getInstance().font;
        for (HudElement element : ELEMENTS.values()) {
            HudPlacement placement = PLACEMENTS.get(element.id());
            if (placement == null) {
                continue;
            }
            int x = placement.anchor().x(screenWidth, element.width(), placement.offsetX());
            int y = placement.anchor().y(screenHeight, element.height(), placement.offsetY());
            graphics.renderOutline(x - 2, y - 2, element.width() + 4, element.height() + 4, 0xFFFFFFFF);
            graphics.drawString(font, element.id(), x, y - 10, 0xFF9FE0A0, false);
        }
    }

    /** 命中测试，返回元素 id；没有命中返回 null。 */
    public static String hitTest(int screenWidth, int screenHeight, double mouseX, double mouseY) {
        for (HudElement element : ELEMENTS.values()) {
            HudPlacement placement = PLACEMENTS.get(element.id());
            if (placement == null) {
                continue;
            }
            int x = placement.anchor().x(screenWidth, element.width(), placement.offsetX());
            int y = placement.anchor().y(screenHeight, element.height(), placement.offsetY());
            if (mouseX >= x - 3 && mouseX <= x + element.width() + 3
                    && mouseY >= y - 3 && mouseY <= y + element.height() + 3) {
                return element.id();
            }
        }
        return null;
    }

    public static void move(String id, int dx, int dy) {
        HudPlacement placement = PLACEMENTS.get(id);
        if (placement != null) {
            PLACEMENTS.put(id, placement.moved(dx, dy));
        }
    }

    public static void reset() {
        PLACEMENTS.clear();
        for (HudElement element : ELEMENTS.values()) {
            PLACEMENTS.put(element.id(), new HudPlacement(element.defaultAnchor(),
                    element.defaultOffsetX(), element.defaultOffsetY(), 1.0F, true));
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                return;
            }
            Map<String, HudPlacement> saved = GSON.fromJson(Files.readString(FILE),
                    new TypeToken<Map<String, HudPlacement>>() {}.getType());
            if (saved == null) {
                return;
            }
            for (HudElement element : ELEMENTS.values()) {
                HudPlacement placement = saved.get(element.id());
                if (placement != null) {
                    PLACEMENTS.put(element.id(), placement);
                }
            }
        } catch (Exception ignored) {
            // 配置坏了就用默认布局，不拦启动。
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(PLACEMENTS));
        } catch (Exception ignored) {
            // 写不进去时只影响下次启动的布局，不影响本次游戏。
        }
    }
}
