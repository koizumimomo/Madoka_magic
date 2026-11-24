package io.madoka.magic.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gameplay values live in data packs; tooltip values use the server's synchronized definitions. */
public final class OriginalCurseDefinitions {
    public record Definition(String kind, double matching, Map<String,List<Double>> parameters, Map<String,Double> attributes) {
        public double value(String key, int rank) {
            var values = parameters.get(key);
            return values == null ? 0 : values.get(Math.min(values.size()-1,Math.max(0,rank-1)));
        }
        public double attribute(String key) { return attributes.getOrDefault(key,0.0); }
    }

    private static volatile Map<String,Definition> definitions = Map.of();
    private static volatile Map<String,Definition> client = Map.of();
    private static int revision;
    private OriginalCurseDefinitions() {}
    public static Definition get(String theme) { return definitions.get(theme); }
    public static Definition client(String theme) { return client.get(theme); }
    public static int revision() { return revision; }

    private static Definition parse(JsonObject json) {
        String kind = GsonHelper.getAsString(json,"kind");
        if (!List.of("butterfly","honey","debt").contains(kind)) throw new IllegalArgumentException("Unknown curse effect kind");
        double matching = GsonHelper.getAsDouble(json,"matching_multiplier",1.2);
        if (!Double.isFinite(matching) || matching < 0 || matching > 10) throw new IllegalArgumentException("Invalid matching bonus");
        var params = new HashMap<String,List<Double>>();
        for (var entry : GsonHelper.getAsJsonObject(json,"parameters").entrySet()) {
            List<Double> values = entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray().asList().stream()
                    .map(JsonElement::getAsDouble).toList() : List.of(entry.getValue().getAsDouble());
            if (values.isEmpty() || values.size()>3 || values.stream().anyMatch(v -> !Double.isFinite(v) || v<0 || v>1_000_000))
                throw new IllegalArgumentException("Invalid curse parameter " + entry.getKey());
            params.put(entry.getKey(),values);
        }
        var attributes = new HashMap<String,Double>();
        for (var entry : GsonHelper.getAsJsonObject(json,"attributes").entrySet()) {
            double value = entry.getValue().getAsDouble();
            if (!Double.isFinite(value) || value<0 || value>1024) throw new IllegalArgumentException("Invalid curse attribute");
            attributes.put(entry.getKey(),value);
        }
        return new Definition(kind,matching,Map.copyOf(params),Map.copyOf(attributes));
    }

    public static CompoundTag snapshot() {
        var root = new CompoundTag();
        definitions.forEach((theme,def)->{
            var value = new CompoundTag(); value.putString("kind",def.kind()); value.putDouble("matching",def.matching());
            var params = new CompoundTag(); def.parameters().forEach((key,numbers)->{
                var list = new ListTag(); numbers.forEach(n->list.add(DoubleTag.valueOf(n))); params.put(key,list);
            }); value.put("parameters",params);
            var attributes = new CompoundTag(); def.attributes().forEach(attributes::putDouble); value.put("attributes",attributes);
            root.put(theme,value);
        });
        return root;
    }

    public static void acceptClient(CompoundTag root) {
        var loaded = new HashMap<String,Definition>();
        for(String theme : root.getAllKeys()) {
            var json = root.getCompound(theme); var params = new HashMap<String,List<Double>>();
            var source = json.getCompound("parameters");
            for(String key : source.getAllKeys()) params.put(key,source.getList(key,Tag.TAG_DOUBLE).stream()
                    .map(v->((DoubleTag)v).getAsDouble()).toList());
            var attributes = new HashMap<String,Double>(); var attrs = json.getCompound("attributes");
            for(String key : attrs.getAllKeys()) attributes.put(key,attrs.getDouble(key));
            loaded.put(theme,new Definition(json.getString("kind"),json.getDouble("matching"),Map.copyOf(params),Map.copyOf(attributes)));
        }
        client=Map.copyOf(loaded);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(),"curse_effects"); }
        @Override protected void apply(Map<ResourceLocation,JsonElement> values, ResourceManager manager, ProfilerFiller profiler) {
            var loaded = new HashMap<String,Definition>();
            values.forEach((id,json)->{
                if (!id.getNamespace().equals("madoka_magic")) return;
                try { loaded.put(id.getPath(),parse(json.getAsJsonObject())); }
                catch(Exception error) { LogUtils.getLogger().error("Could not load curse effect {}",id,error); }
            });
            definitions=Map.copyOf(loaded); revision++;
        }
    }
}
