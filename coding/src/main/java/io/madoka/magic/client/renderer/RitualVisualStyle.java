package io.madoka.magic.client.renderer;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.madoka.magic.MadokaMagic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

record RitualVisualStyle(int crown, int primary, int secondary, Sprite motif, Sprite accent, Motion motion, Sprite bossCrest) {
    enum Sprite {
        STAR(0), BUTTERFLY(1), BUTTERFLY_FOLDED(2), HONEYCOMB(3), DROP(4), THORN(5), PETAL(6),
        GEAR(7), RIBBON(8), NOTE(9), EMBER(10), STAGE_LIGHT(11), FLECK(12), STAFF(13), SHARD(14), LINK(15);

        final int tile;

        Sprite(int tile) {
            this.tile = tile;
        }
    }

    enum Motion { SHED, GATHER, BIND, WEAVE, REWIND, INVITE, RIPPLE, FLING, PARADE, THREAD }

    private static final ResourceLocation STYLES = ResourceLocation.fromNamespaceAndPath(
            MadokaMagic.MODID, "visuals/witch_styles.json");
    private static final RitualVisualStyle FALLBACK = new RitualVisualStyle(
            0xf4b9ea, 0xed9edb, 0xad70c6, Sprite.STAR, Sprite.FLECK, Motion.INVITE, Sprite.STAR);
    private static volatile Map<String, RitualVisualStyle> styles = Map.of();

    static RitualVisualStyle forTheme(String theme) {
        return styles.getOrDefault(theme, FALLBACK);
    }

    static RitualVisualStyle forPlayer(String theme, boolean witch) {
        return witch ? forTheme(theme) : styles.getOrDefault(theme + "_player", forTheme(theme));
    }

    static void reload(ResourceManager resources) {
        try (var reader = resources.openAsReader(STYLES)) {
            Map<String, RitualVisualStyle> loaded = new HashMap<>();
            for (var entry : GsonHelper.parse(reader).entrySet()) {
                JsonObject style = entry.getValue().getAsJsonObject();
                Sprite motif = Sprite.valueOf(key(style, "motif"));
                loaded.put(entry.getKey(), new RitualVisualStyle(color(style, "crown"),
                        color(style, "primary"), color(style, "secondary"), motif,
                        Sprite.valueOf(key(style, "accent")), Motion.valueOf(key(style, "motion")),
                        style.has("boss_crest") ? Sprite.valueOf(key(style, "boss_crest")) : motif));
            }
            styles = Map.copyOf(loaded);
        } catch (Exception error) {
            LogUtils.getLogger().error("Could not reload witch visual styles from {}", STYLES, error);
        }
    }

    private static String key(JsonObject style, String key) {
        return GsonHelper.getAsString(style, key).toUpperCase(Locale.ROOT);
    }

    private static int color(JsonObject style, String key) {
        String color = GsonHelper.getAsString(style, key);
        if (!color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid " + key + " color: " + color);
        }
        return Integer.parseInt(color.substring(1), 16);
    }
}
