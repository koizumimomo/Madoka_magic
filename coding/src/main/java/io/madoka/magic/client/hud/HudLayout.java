package io.madoka.magic.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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
        register(new HudElements.CurseStacks());
        register(new HudElements.CurseDebt());
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
            renderElement(graphics, element, placement, screenWidth, screenHeight, false);
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
            var bounds = bounds(element, placement, screenWidth, screenHeight);
            renderElement(graphics, element, placement, screenWidth, screenHeight, true);
            graphics.renderOutline(bounds.x - 2, bounds.y - 2, bounds.width + 4, bounds.height + 4,
                    placement.visible() ? 0xffeeeeee : 0xff826e88);
            Component label = Component.translatable("hud.madoka_magic." + element.id());
            if (!placement.visible()) label = Component.translatable("screen.madoka_magic.hud_edit.hidden", label);
            graphics.drawString(font, label, bounds.x, Math.max(1, bounds.y - 10),
                    placement.visible() ? 0xff9fe0a0 : 0xffaa8fae, false);
        }
    }

    /** 命中测试，返回元素 id；没有命中返回 null。 */
    public static String hitTest(int screenWidth, int screenHeight, double mouseX, double mouseY) {
        for (HudElement element : ELEMENTS.values()) {
            HudPlacement placement = PLACEMENTS.get(element.id());
            if (placement == null) {
                continue;
            }
            var bounds = bounds(element, placement, screenWidth, screenHeight);
            if (mouseX >= bounds.x - 3 && mouseX <= bounds.x + bounds.width + 3
                    && mouseY >= bounds.y - 3 && mouseY <= bounds.y + bounds.height + 3) {
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

    private record Bounds(int x, int y, int width, int height) {}

    private static Bounds bounds(HudElement element, HudPlacement placement, int screenWidth, int screenHeight) {
        float scale = scale(placement);
        int width = (int) Math.ceil(element.width() * scale);
        int height = (int) Math.ceil(element.height() * scale);
        var anchor = placement.anchor() == null ? element.defaultAnchor() : placement.anchor();
        int x = net.minecraft.util.Mth.clamp(anchor.x(screenWidth, width, placement.offsetX()), 0, Math.max(0, screenWidth - width));
        int y = net.minecraft.util.Mth.clamp(anchor.y(screenHeight, height, placement.offsetY()), 0, Math.max(0, screenHeight - height));
        return new Bounds(x, y, width, height);
    }

    private static float scale(HudPlacement placement) {
        return Float.isFinite(placement.scale()) ? net.minecraft.util.Mth.clamp(placement.scale(), 0.5f, 3f) : 1;
    }

    private static void renderElement(GuiGraphics graphics, HudElement element, HudPlacement placement,
                                      int screenWidth, int screenHeight, boolean preview) {
        var bounds = bounds(element, placement, screenWidth, screenHeight);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(bounds.x, bounds.y, 0);
        pose.scale(scale(placement), scale(placement), 1);
        pose.translate(-bounds.x, -bounds.y, 0);
        try {
            if (preview) element.renderPreview(graphics, bounds.x, bounds.y);
            else element.render(graphics, bounds.x, bounds.y);
        } finally {
            pose.popPose();
        }
    }

    public static void resize(String id, float delta) {
        var placement = PLACEMENTS.get(id);
        if (placement != null) PLACEMENTS.put(id, placement.withScale(
                net.minecraft.util.Mth.clamp(scale(placement) + delta, 0.5f, 3f)));
    }

    public static void toggle(String id) {
        var placement = PLACEMENTS.get(id);
        if (placement != null) PLACEMENTS.put(id, placement.withVisible(!placement.visible()));
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
