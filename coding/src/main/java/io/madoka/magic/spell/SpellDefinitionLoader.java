package io.madoka.magic.spell;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.util.HashMap;
import java.util.Map;

/**
 * 技能定义的加载器：{@code data/madoka_magic/spells/*.json}。
 * <p>可随资源包热重载。</p>
 */
public class SpellDefinitionLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "spells";
    private static final Gson GSON = new Gson();
    private static Map<String, SpellDefinition> bundledDefinitions = Map.of();

    public SpellDefinitionLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, SpellDefinition> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            try {
                SpellDefinition def = parse(entry.getKey(), entry.getValue());
                if (def != null) {
                    loaded.put(def.id(), def);
                }
            } catch (Exception e) {
                SpellRegistry.LOGGER.error("Failed to load spell {}: {}", entry.getKey(), e.getMessage());
            }
        }
        SpellRegistry.replaceDefinitions(loaded);
        SpellRegistry.LOGGER.info("Loaded {} spell definition(s): {}", loaded.size(), loaded.keySet());
    }

    /** 兜底：直接从 mod 自己的 jar 里读。清单：{@code data/madoka_magic/spells/index.json}。 */
    public static Map<String, SpellDefinition> loadFromJar() {
        Map<String, SpellDefinition> result = new HashMap<>();
        try (java.io.InputStream index = SpellDefinitionLoader.class
                .getResourceAsStream("/data/madoka_magic/spells/index.json")) {
            if (index == null) {
                return result;
            }
            JsonElement parsed = JsonParser.parseReader(new java.io.InputStreamReader(index, java.nio.charset.StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return result;
            }
            for (JsonElement e : parsed.getAsJsonObject().getAsJsonArray("spells")) {
                String id = e.getAsString();
                try (java.io.InputStream in = SpellDefinitionLoader.class
                        .getResourceAsStream("/data/madoka_magic/spells/" + id + ".json")) {
                    if (in == null) {
                        continue;
                    }
                    JsonElement json = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    SpellDefinition def = new SpellDefinitionLoader()
                            .parse(new net.minecraft.resources.ResourceLocation(io.madoka.magic.MadokaMagic.MODID, id), json);
                    if (def != null) {
                        result.put(def.id(), def);
                    }
                }
            }
        } catch (Exception e) {
            SpellRegistry.LOGGER.error("Failed to load spells from jar: {}", e.getMessage());
        }
        bundledDefinitions = Map.copyOf(result);
        return result;
    }

    SpellDefinition parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        // 同上：只取文件名
        String spellId = id.getPath();
        int slash = spellId.lastIndexOf('/');
        if (slash >= 0) {
            spellId = spellId.substring(slash + 1);
        }
        if ("index".equals(spellId)) {
            return null;   // 清单文件，跳过
        }
        String type = json.has("type") ? json.get("type").getAsString() : spellId;
        boolean active = !json.has("active") || json.get("active").getAsBoolean();
        String nameKey = opt(json, "name_key", "spell.madoka_magic." + spellId);
        String descKey = opt(json, "desc_key", nameKey + ".desc");
        String icon = opt(json, "icon", "madoka_magic:textures/gui/spells/" + spellId + ".png");
        int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 5;
        float manaCost = json.has("mana_cost") ? json.get("mana_cost").getAsFloat() : 10.0F;
        float taint = json.has("taint_on_cast") ? json.get("taint_on_cast").getAsFloat() : 0.3F;
        int unlock = json.has("unlock_level") ? json.get("unlock_level").getAsInt() : 1;
        Map<String, Double> parameters = new HashMap<>();
        SpellDefinition bundled = bundledDefinitions.get(spellId);
        if (bundled != null && bundled.type().equals(type)) {
            parameters.putAll(bundled.parameters());
        }
        if (json.has("parameters")) {
            for (var entry : json.getAsJsonObject("parameters").entrySet()) {
                double value = entry.getValue().getAsDouble();
                if (!Double.isFinite(value) || value < 0.0D) {
                    throw new IllegalArgumentException("Invalid spell parameter: " + entry.getKey());
                }
                parameters.put(entry.getKey(), value);
            }
        }
        return new SpellDefinition(spellId, type, active, nameKey, descKey,
                new ResourceLocation(icon), cooldown, manaCost, taint, unlock, parameters);
    }

    private static String opt(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }
}
