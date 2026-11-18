package io.madoka.magic.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Data-driven talents. Point awards use permanent base causality and survive form changes. */
public final class PassiveTree {
    public record Node(String id, String effect, String passive, String theme, Map<String, Integer> requires,
                       int maxRank, double perRank, double x, double y, String nameKey) {
        public boolean matches(PlayerMadokaData data) {
            return theme.isEmpty() || theme.equals(data.getThemeId());
        }
    }

    private static volatile List<Node> nodes = List.of();
    private static volatile int causalityPerPoint = 10;

    private PassiveTree() {}

    public static List<Node> nodes() {
        return nodes;
    }

    public static int causalityPerPoint() {
        return causalityPerPoint;
    }

    public static boolean award(Player player, PlayerMadokaData data) {
        var attribute = player.getAttribute(MadokaAttributes.MAGIC_CAUSALITY.get());
        if (attribute == null || !data.isContracted()) return false;
        double causality = attribute.getBaseValue();
        if (!Double.isFinite(causality)) return false;
        return data.awardTalentPoints((int) Math.floor(Math.min(io.madoka.magic.util.MadokaMath.CAUSALITY_CAP,
                Math.max(0, causality)) / causalityPerPoint));
    }

    public static double bonus(Player player, String effect) {
        return bonus(PlayerMadokaData.of(player), effect);
    }

    public static double bonus(PlayerMadokaData data, String effect) {
        if (!data.isContracted() || !data.isTransformed()) return 0;
        var passives = java.util.Set.of("devotion_wing", "selfish_eye", "sin_mark", "obsession", "ember");
        double bonus = 0;
        for (Node node : nodes) {
            if (node.effect.equals(effect) && node.matches(data)) {
                bonus += Math.min(node.maxRank, data.getTalentRank(node.id)) * node.perRank;
            }
            if (node.effect.equals("passive_mastery") && node.matches(data)
                    && passives.contains(effect)) {
                bonus += Math.min(node.maxRank, data.getTalentRank(node.id)) * node.perRank;
            }
            if (node.effect.equals("signature_passive") && node.matches(data) && data.getTheme() != null
                    && data.getTheme().getPassives(data.isWitchPath()).contains(effect)
                    && !effect.equals("obsession") && !effect.equals("ember")) {
                bonus += Math.min(node.maxRank, data.getTalentRank(node.id)) * node.perRank;
            }
        }
        return bonus;
    }

    public static boolean canLearn(PlayerMadokaData data, Node node) {
        return data.isContracted() && node.matches(data) && data.getTalentPoints() > 0
                && data.getTalentRank(node.id) < node.maxRank
                && node.requires.entrySet().stream().allMatch(entry -> data.getTalentRank(entry.getKey()) >= entry.getValue());
    }

    public static void learn(ServerPlayer player, String id) {
        var data = PlayerMadokaData.of(player);
        award(player, data);
        nodes.stream().filter(node -> node.id.equals(id)).findFirst().filter(node -> canLearn(data, node))
                .ifPresent(node -> data.learnTalent(node.id));
        data.save(player);
        io.madoka.magic.data.PlayerTransformation.applyAttributes(player);
        io.madoka.magic.network.MadokaNetwork.syncTo(player);
    }

    public static void bootstrap() {
        try (var stream = PassiveTree.class.getResourceAsStream("/data/madoka_magic/passive_trees/default.json")) {
            if (stream != null) {
                parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        } catch (Exception error) {
            SpellRegistry.LOGGER.error("Could not load passive tree", error);
        }
    }

    private static void parse(JsonElement json) {
        var root = json.getAsJsonObject();
        int interval = GsonHelper.getAsInt(root, "causality_per_point");
        if (interval < 1) throw new IllegalArgumentException("causality_per_point must be positive");
        List<Node> loaded = new ArrayList<>();
        for (var element : root.getAsJsonArray("nodes")) {
            var value = element.getAsJsonObject();
            Map<String, Integer> requires = new java.util.LinkedHashMap<>();
            if (value.has("requires")) {
                value.getAsJsonObject("requires").entrySet().forEach(entry -> requires.put(entry.getKey(), entry.getValue().getAsInt()));
            }
            var node = new Node(GsonHelper.getAsString(value, "id"), GsonHelper.getAsString(value, "effect"),
                    GsonHelper.getAsString(value, "passive", ""), GsonHelper.getAsString(value, "theme", ""),
                    Map.copyOf(requires),
                    GsonHelper.getAsInt(value, "max_rank"), GsonHelper.getAsDouble(value, "per_rank"),
                    GsonHelper.getAsDouble(value, "x"), GsonHelper.getAsDouble(value, "y"),
                    GsonHelper.getAsString(value, "name_key"));
            if (node.maxRank < 1 || node.maxRank > 100 || !Double.isFinite(node.perRank) || node.perRank < 0
                    || !Double.isFinite(node.x) || !Double.isFinite(node.y) || Math.abs(node.x) > 1024 || Math.abs(node.y) > 1024
                    || loaded.stream().anyMatch(other -> other.id.equals(node.id))) {
                throw new IllegalArgumentException("Invalid talent " + node.id);
            }
            for (var requirement : node.requires.entrySet()) {
                if (requirement.getValue() < 1 || loaded.stream().noneMatch(parent -> parent.id.equals(requirement.getKey())
                        && parent.maxRank >= requirement.getValue())) {
                    throw new IllegalArgumentException("Invalid prerequisite for " + node.id);
                }
            }
            loaded.add(node);
        }
        nodes = List.copyOf(loaded);
        causalityPerPoint = interval;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new com.google.gson.Gson(), "passive_trees");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> values, ResourceManager manager, ProfilerFiller profiler) {
            var json = values.get(ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "default"));
            if (json == null) return;
            try {
                parse(json);
            } catch (Exception error) {
                SpellRegistry.LOGGER.error("Could not reload passive tree", error);
            }
        }
    }
}
