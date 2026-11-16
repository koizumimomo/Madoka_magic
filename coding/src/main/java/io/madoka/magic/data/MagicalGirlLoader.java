package io.madoka.magic.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.madoka.magic.MadokaMagic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 魔法少女定义的加载器。
 *
 * <p>数据位置：{@code data/madoka_magic/magical_girls/<id>.json}。</p>
 * <p>这是一个<b>可重载</b>的资源监听器——把 JSON 丢进资源包就能加一位新的魔法少女，
 * 不需要改代码、不需要重新编译。</p>
 */
public class MagicalGirlLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "magical_girls";
    private static final Gson GSON = new Gson();

    public MagicalGirlLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, MagicalGirl> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            try {
                MagicalGirl girl = parse(entry.getKey(), entry.getValue());
                if (girl != null) {
                    loaded.put(girl.getId(), girl);
                }
            } catch (Exception e) {
                MagicalGirlRegistry.LOGGER.error("Failed to load magical girl {}: {}", entry.getKey(), e.getMessage());
            }
        }
        MagicalGirlRegistry.replace(loaded);
        MagicalGirlRegistry.LOGGER.info("Loaded {} magical girl definition(s): {}", loaded.size(), loaded.keySet());
    }

    /**
     * 兜底：直接从 mod 自己的 jar 里读（没有服务器 / 数据生成环境下也能用）。
     * <p>清单文件：{@code data/madoka_magic/magical_girls/index.json}。</p>
     */
    public static Map<String, MagicalGirl> loadFromJar() {
        Map<String, MagicalGirl> result = new HashMap<>();
        try (java.io.InputStream index = MagicalGirlLoader.class
                .getResourceAsStream("/data/madoka_magic/magical_girls/index.json")) {
            if (index == null) {
                return result;
            }
            JsonElement parsed = JsonParser.parseReader(new java.io.InputStreamReader(index, java.nio.charset.StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return result;
            }
            for (JsonElement e : parsed.getAsJsonObject().getAsJsonArray("girls")) {
                String id = e.getAsString();
                try (java.io.InputStream in = MagicalGirlLoader.class
                        .getResourceAsStream("/data/madoka_magic/magical_girls/" + id + ".json")) {
                    if (in == null) {
                        continue;
                    }
                    JsonElement json = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                    MagicalGirl girl = new MagicalGirlLoader().parse(new ResourceLocation(MadokaMagic.MODID, id), json);
                    if (girl != null) {
                        result.put(girl.getId(), girl);
                    }
                }
            }
        } catch (Exception e) {
            MagicalGirlRegistry.LOGGER.error("Failed to load magical girls from jar: {}", e.getMessage());
        }
        return result;
    }

    MagicalGirl parse(ResourceLocation id, JsonElement jsonElement) {
        if (!jsonElement.isJsonObject()) {
            return null;
        }
        JsonObject json = jsonElement.getAsJsonObject();
        // 原版加载器给出的路径带目录前缀（magical_girls/xxx），这里只取文件名
        String girlId = id.getPath();
        int slash = girlId.lastIndexOf('/');
        if (slash >= 0) {
            girlId = girlId.substring(slash + 1);
        }
        if ("index".equals(girlId)) {
            return null;   // 清单文件，跳过
        }

        String name = opt(json, "name", girlId);
        String translationKey = opt(json, "translation_key", "madoka_magic.magical_girl." + girlId);
        String classification = opt(json, "classification", girlId);
        String role = opt(json, "role", "");
        int primary = color(json, "primary_color", 0xFFFFFF);
        int secondary = color(json, "secondary_color", 0x000000);
        String element = opt(json, "element", girlId);
        String school = opt(json, "school", "");
        int manaMax = json.has("mana_max") ? json.get("mana_max").getAsInt() : 100;
        MagicalGirl.Tone tone = parseTone(opt(json, "tone", "decisive"));

        double damageDealt = number(json, "damage_dealt", 1.0D);
        double damageTaken = number(json, "damage_taken", 1.0D);
        double taintRate = number(json, "taint_rate", 1.0D);
        double manaGain = number(json, "mana_gain", 1.0D);

        String material = opt(json, "contract_material", "");
        ResourceLocation contractMaterial = material.isEmpty()
                ? null : new ResourceLocation(material);
        String dimension = opt(json, "witch_dimension", "madoka_magic:witch_" + girlId);

        java.util.List<String> skills = stringList(json, "skills");
        java.util.List<String> passives = stringList(json, "passives");
        MagicalGirl.WitchForm witchForm = null;
        if (json.has("witch_form") && json.get("witch_form").isJsonObject()) {
            var witch = json.getAsJsonObject("witch_form");
            witchForm = new MagicalGirl.WitchForm(number(witch, "health_bonus", 0), number(witch, "attack_bonus", 0),
                    number(witch, "armor_bonus", 0), number(witch, "speed_bonus", 0),
                    stringList(witch, "skills"), stringList(witch, "passives"));
        }
        MagicalGirl.Growth growth = MagicalGirl.Growth.NONE;
        if (json.has("growth") && json.get("growth").isJsonObject()) {
            var stats = json.getAsJsonObject("growth");
            growth = new MagicalGirl.Growth(number(stats, "speed_per_step", 0),
                    number(stats, "mana_regen_per_step", 0), number(stats, "armor_per_step", 0));
        }

        return new MagicalGirl(girlId, name, translationKey, classification, role,
                primary, secondary, element, school, manaMax, tone,
                damageDealt, damageTaken, taintRate, manaGain,
                contractMaterial, dimension, skills, passives, witchForm, growth);
    }

    private static MagicalGirl.Tone parseTone(String value) {
        for (MagicalGirl.Tone tone : MagicalGirl.Tone.values()) {
            if (tone.name().equalsIgnoreCase(value)) {
                return tone;
            }
        }
        return MagicalGirl.Tone.DECISIVE;
    }

    private static java.util.List<String> stringList(JsonObject json, String key) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            json.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
        }
        return list;
    }

    private static String opt(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static double number(JsonObject json, String key, double fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : fallback;
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            String hex = json.get(key).getAsString();
            return (int) Long.parseLong(hex.replace("#", ""), 16);
        } catch (Exception e) {
            return fallback;
        }
    }
}
