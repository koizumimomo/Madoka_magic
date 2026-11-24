package io.madoka.magic.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import io.madoka.magic.MadokaMagic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reloadable stack thresholds and the effect that consumes a completed sigil. */
public final class CurseStackProfiles {
    private static volatile Map<String, Profile> profiles = Map.of();

    private CurseStackProfiles() {}

    public static Profile get(String theme) {
        return profiles.get(theme);
    }

    public record Profile(String nameKey, List<Integer> thresholds, double strikeDamage,
                          double levelMultiplier, double matchingMultiplier) {
        public int threshold(int level) {
            return thresholds.get(net.minecraft.util.Mth.clamp(level, 1, thresholds.size()) - 1);
        }
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), "curse_stacks");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> values, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Profile> loaded = new HashMap<>();
            values.forEach((id, value) -> {
                if (!id.getNamespace().equals(MadokaMagic.MODID)) return;
                try {
                    var json = value.getAsJsonObject();
                    var thresholds = GsonHelper.getAsJsonArray(json, "thresholds").asList().stream()
                            .map(JsonElement::getAsInt).toList();
                    double damage = GsonHelper.getAsDouble(json, "strike_damage");
                    double scale = GsonHelper.getAsDouble(json, "level_multiplier", 1.3);
                    double matching = GsonHelper.getAsDouble(json, "matching_multiplier", 1.2);
                    if (thresholds.size() != 3 || thresholds.stream().anyMatch(n -> n < 1 || n > 8)
                            || !Double.isFinite(damage) || damage < 0 || damage > 1000
                            || !Double.isFinite(scale) || scale < 0 || scale > 10
                            || !Double.isFinite(matching) || matching < 0 || matching > 10) {
                        throw new IllegalArgumentException("Invalid curse stack profile");
                    }
                    loaded.put(id.getPath(), new Profile(GsonHelper.getAsString(json, "name_key"),
                            thresholds, damage, scale, matching));
                } catch (Exception error) {
                    LogUtils.getLogger().error("Could not load curse stack profile {}", id, error);
                }
            });
            profiles = Map.copyOf(loaded);
        }
    }
}
