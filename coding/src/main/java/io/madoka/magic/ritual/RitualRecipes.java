package io.madoka.magic.ritual;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 祭坛仪式合成配方加载器：{@code data/madoka_magic/ritual_recipes/*.json}。
 *
 * <p>格式：{@code {"inputs":["minecraft:yellow_tulip","minecraft:coal"],
 * "output":{"item":"madoka_magic:black_tulip","count":1}}}。
 * inputs 是无序集合匹配：祭坛上的材料去重后与 inputs 集合完全相等即命中。</p>
 */
public class RitualRecipes extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "ritual_recipes";
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LoggerFactory.getLogger(RitualRecipes.class);

    private static volatile Map<ResourceLocation, RitualRecipe> RECIPES = Map.of();

    public RitualRecipes() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, RitualRecipe> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
            try {
                RitualRecipe recipe = parse(entry.getKey(), entry.getValue());
                if (recipe != null) {
                    loaded.put(recipe.id(), recipe);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load ritual recipe {}: {}", entry.getKey(), e.getMessage());
            }
        }
        RECIPES = Map.copyOf(loaded);
        LOGGER.info("[madoka_magic] loaded {} ritual recipe(s): {}", loaded.size(), loaded.keySet());
    }

    /** 全部已加载配方。 */
    public static Map<ResourceLocation, RitualRecipe> all() {
        return RECIPES;
    }

    /**
     * 查找与当前材料（无序集合）匹配的配方。
     *
     * @param materials 祭坛上的非空材料
     * @return 匹配的配方；无则 {@code null}
     */
    public static RitualRecipe findMatch(List<ItemStack> materials) {
        if (materials.isEmpty()) {
            return null;
        }
        Set<Item> present = new HashSet<>();
        for (ItemStack stack : materials) {
            if (!stack.isEmpty()) {
                present.add(stack.getItem());
            }
        }
        for (RitualRecipe recipe : RECIPES.values()) {
            if (recipe.inputs().equals(present)) {
                return recipe;
            }
        }
        return null;
    }

    private static RitualRecipe parse(ResourceLocation id, JsonElement element) {
        if (!element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        Set<Item> inputs = new HashSet<>();
        for (JsonElement e : json.getAsJsonArray("inputs")) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(e.getAsString()));
            if (item == null) {
                throw new IllegalArgumentException("unknown item " + e.getAsString());
            }
            inputs.add(item);
        }
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("empty inputs");
        }
        JsonObject outputJson = json.getAsJsonObject("output");
        Item outputItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(outputJson.get("item").getAsString()));
        if (outputItem == null) {
            throw new IllegalArgumentException("unknown output item " + outputJson.get("item").getAsString());
        }
        int count = outputJson.has("count") ? outputJson.get("count").getAsInt() : 1;
        return new RitualRecipe(id, inputs, new ItemStack(outputItem, count));
    }
}
