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

record RitualVisualStyle(int crown, int primary, int secondary, Sprite motif, Sprite accent, Motion motion,
                         Sprite bossCrest, Orbit orbit) {
    record Orbit(int row, int count, double size, double radius, double speed, double heightSpread, int frameTicks) {}
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
            0xf4b9ea, 0xed9edb, 0xad70c6, Sprite.STAR, Sprite.FLECK, Motion.INVITE, Sprite.STAR, null);
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
                        style.has("boss_crest") ? Sprite.valueOf(key(style, "boss_crest")) : motif, orbit(style)));
            }
            styles = Map.copyOf(loaded);
        } catch (Exception error) {
            LogUtils.getLogger().error("Could not reload witch visual styles from {}", STYLES, error);
        }
    }

    private static Orbit orbit(JsonObject style) {
        if (!style.has("orbit")) return null;
        var json = style.getAsJsonObject("orbit");
        var orbit = new Orbit(GsonHelper.getAsInt(json, "row"), GsonHelper.getAsInt(json, "count", 8),
                GsonHelper.getAsDouble(json, "size", 0.19), GsonHelper.getAsDouble(json, "radius", 0.8),
                GsonHelper.getAsDouble(json, "speed", 0.019), GsonHelper.getAsDouble(json, "height_spread", 0.55),
                GsonHelper.getAsInt(json, "frame_ticks", 5));
        if (orbit.row < 0 || orbit.row >= ExternalWitchParticles.ROWS || orbit.count < 1 || orbit.count > 24
                || !Double.isFinite(orbit.size) || orbit.size < 0.04 || orbit.size > 0.7
                || !Double.isFinite(orbit.radius) || orbit.radius < 0.2 || orbit.radius > 3
                || !Double.isFinite(orbit.speed) || Math.abs(orbit.speed) > 0.2
                || !Double.isFinite(orbit.heightSpread) || orbit.heightSpread < 0 || orbit.heightSpread > 1
                || orbit.frameTicks < 1 || orbit.frameTicks > 40) {
            throw new IllegalArgumentException("Invalid witch orbit profile");
        }
        return orbit;
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
