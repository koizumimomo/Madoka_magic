package io.madoka.magic.spell;

import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;

/** Read-only caster-side numbers. The target's defenses and future combat events are resolved on hit. */
public record SpellPreview(float mana, float taint, int unlock, int cooldownTicks,
                           List<Component> details, Component alternateCost, List<Component> mechanics) {
    public static SpellPreview read(FriendlyByteBuf buffer) {
        return new SpellPreview(buffer.readFloat(), buffer.readFloat(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readList(FriendlyByteBuf::readComponent), buffer.readComponent(),
                buffer.readList(FriendlyByteBuf::readComponent));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeFloat(mana);
        buffer.writeFloat(taint);
        buffer.writeVarInt(unlock);
        buffer.writeVarInt(cooldownTicks);
        buffer.writeCollection(details, FriendlyByteBuf::writeComponent);
        buffer.writeComponent(alternateCost);
        buffer.writeCollection(mechanics, FriendlyByteBuf::writeComponent);
    }

    public static SpellPreview of(ServerPlayer player, SpellDefinition spell) {
        var data = PlayerMadokaData.of(player);
        long now = player.level().getGameTime();
        double outgoing = MadokaMath.outgoingDamageMultiplier(player);
        var details = switch (spell.type()) {
            case "madoka_magic:butterfly_slash" -> List.of(damage(player, spell.parameter("damage"), outgoing));
            case "madoka_magic:tulip_burst" -> List.of(damage(player, MadokaSpellEffects.TULIP_DAMAGE, outgoing));
            case "madoka_magic:bee_sting" -> List.of(damage(player, MadokaSpellEffects.BEE_STING_DAMAGE, outgoing),
                    line("poison"));
            case "madoka_magic:witch_black_wings", "madoka_magic:witch_venom", "madoka_magic:witch_honey_tide" ->
                    List.of(damage(player, spell.parameter("damage"), outgoing));
            case "madoka_magic:witch_cocoon_burst" -> {
                double base = spell.parameter("damage");
                double perMark = spell.parameter("damage_per_mark");
                yield List.of(line("witch_mark_damage", amount(player, base + perMark, outgoing),
                        amount(player, base + ButterflyMark.MAX_LAYERS * perMark, outgoing)));
            }
            case "madoka_magic:witch_tear_rain" -> List.of(line("witch_rain_damage",
                    amount(player, spell.parameter("damage"), outgoing), param(spell, "pulses")));
            case "madoka_magic:witch_thorn_echo" -> {
                var target = WitchPlayerSpells.thornTarget(player, spell);
                yield target == null ? List.of(line("no_guilt"))
                        : List.of(damage(player, spell.parameter("damage")
                        + GuiltMark.getAmount(target, player.getUUID()) * spell.parameter("damage_per_guilt"), outgoing));
            }
            case "madoka_magic:injection" -> List.of(damage(player, spell.parameter("damage"), outgoing));
            case "madoka_magic:pollen" -> pollen(player, now, outgoing);
            case "madoka_magic:blossom" -> List.of(line("none"), line("channel", number(BlossomState.MANA_PER_SECOND)));
            case "madoka_magic:wither" -> List.of(damage(player, MadokaSpellEffects.witherDamage(data), outgoing),
                    line("health", number(MadokaSpellEffects.witherHealthCost(player))));
            case "madoka_magic:thorn_embrace" -> List.of(line("per_second",
                    amount(player, ThornTracker.TICK_DAMAGE, outgoing)));
            case "madoka_magic:redemption_blade" -> redemption(player, outgoing);
            case "madoka_magic:unpayable_promise" -> PromiseState.remainingTicks(data, now) > 0
                    ? List.of(line("pool", number(MadokaMath.outgoingDamage(player, data.getPromisePool(), outgoing)))) : List.of(line("future_pool"));
            case "madoka_magic:swarm_call" -> List.of(line("swarm_sting",
                    amount(player, spell.parameter("sting_damage"), outgoing)));
            case "madoka_magic:honey", "madoka_magic:illusion", "madoka_magic:dandelion_drift"
                    -> List.of(line("none"));
            case "madoka_magic:devotion_wing" -> List.of(line("butterfly_mark", ButterflyMark.MAX_LAYERS,
                    ButterflyMark.DURATION_TICKS / 20, number(ButterflyMark.DAMAGE_BONUS_PER_LAYER * 100
                            * (1 + io.madoka.magic.data.PassiveTree.bonus(player, "devotion_wing")))));
            case "madoka_magic:obsession" -> List.of(line("obsession",
                    number((MadokaPassives.obsessionMultiplier(data, now) - 1.0D) * 100)));
            case "madoka_magic:ember" -> List.of(line("ember", number((MadokaPassives.emberMultiplier(data) - 1.0D) * 100)));
            case "madoka_magic:sin_mark" -> List.of(line("guilt", number(GuiltMark.totalFor(player))));
            case "madoka_magic:selfish_eye" -> {
                var stats = data.getSelfishEye();
                yield List.of(line("steal", number(Math.min(1, spell.parameter("steal_ratio")
                                * (1 + io.madoka.magic.data.PassiveTree.bonus(player, "selfish_eye"))) * 100),
                                number(spell.parameter("retention_ratio") * 100), number(spell.parameter("combat_timeout_seconds"))),
                        line("stolen", number(stats.attack()), number(stats.health()), number(stats.mana())));
            }
            default -> List.<Component>of();
        };
        Component alternateCost = (MadokaSpellEffects.TYPE_PREFIX + "wither").equals(spell.type())
                ? line("taint", number(data.isHalfWitch() ? 0 : MadokaSpellEffects.WITHER_TAINT_COST
                        * Math.max(0, 1.0D - io.madoka.magic.data.PassiveTree.bonus(player, "taint")))) : Component.empty();
        return new SpellPreview(spell.manaCost(), SpellCasting.taintCost(player, spell), spell.unlockLevel(),
                SpellCasting.effectiveCooldownTicks(player, spell), details, alternateCost, mechanics(player, spell, outgoing));
    }

    private static List<Component> mechanics(ServerPlayer player, SpellDefinition spell, double outgoing) {
        return switch (spell.type()) {
            case "madoka_magic:butterfly_slash", "madoka_magic:witch_black_wings" -> List.of(line("cone",
                    param(spell, "angle_degrees"), distance(spell.parameter("range"))));
            case "madoka_magic:witch_cocoon_burst", "madoka_magic:witch_honey_tide",
                    "madoka_magic:witch_tear_rain", "madoka_magic:witch_thorn_echo" ->
                    List.of(line("radius", distance(spell.parameter("radius"))));
            case "madoka_magic:witch_venom" -> List.of(line("reach", distance(spell.parameter("range"))),
                    line("witch_poison", param(spell, "poison_seconds")));
            case "madoka_magic:tulip_burst" -> List.of(line("radius", distance(MadokaSpellEffects.TULIP_RADIUS)));
            case "madoka_magic:pollen" -> {
                double oneMark = 1.0D + ButterflyMark.DAMAGE_BONUS_PER_LAYER
                        * (1 + io.madoka.magic.data.PassiveTree.bonus(player, "devotion_wing"));
                yield List.of(line("radius", distance(MadokaSpellEffects.POLLEN_RANGE)), line("pollen_one",
                        amount(player, MadokaSpellEffects.POLLEN_INITIAL_DAMAGE * oneMark, outgoing),
                        amount(player, MadokaSpellEffects.POLLEN_TICK_DAMAGE * oneMark, outgoing)));
            }
            case "madoka_magic:wither" -> List.of(line("radius", distance(MadokaSpellEffects.WITHER_RADIUS)));
            case "madoka_magic:thorn_embrace" -> List.of(line("radius", distance(ThornTracker.APPLY_RADIUS)));
            case "madoka_magic:bee_sting" -> List.of(line("reach", distance(MadokaSpellEffects.BEE_STING_RANGE)));
            case "madoka_magic:unpayable_promise" -> List.of(line("promise_range",
                    distance(MadokaSpellEffects.PROMISE_RANGE), distance(PromiseState.MAX_DISTANCE)));
            case "madoka_magic:injection" -> List.of(line("inject",
                    distance(spell.parameter("range")), number(spell.parameter("heal_ratio") * 100),
                    number(spell.parameter("honey_heal_bonus") * 100), param(spell, "mana_return")));
            case "madoka_magic:honey" -> List.of(line("honey",
                    distance(spell.parameter("range")), distance(spell.parameter("radius")), param(spell, "duration_seconds"),
                    param(spell, "linger_seconds"), number(spell.parameter("slow_per_layer") * 100),
                    param(spell, "max_layers")));
            case "madoka_magic:dandelion_drift" -> List.of(line("seeds",
                    param(spell, "seed_count"), distance(spell.parameter("range")), distance(spell.parameter("spread_radius"))));
            case "madoka_magic:illusion" -> List.of(line("illusion", distance(spell.parameter("radius")),
                    param(spell, "duration_seconds")));
            case "madoka_magic:swarm_call" -> List.of(line("swarm", param(spell, "count"),
                    param(spell, "duration_seconds"), param(spell, "max_bees")),
                    line("radius", distance(spell.parameter("range"))));
            case "madoka_magic:redemption_blade" -> List.of(line("radius", distance(MadokaSpellEffects.REDEMPTION_RANGE)),
                    line("redemption_min",
                    amount(player, MadokaSpellEffects.redemptionDamage(MadokaSpellEffects.REDEMPTION_MIN_GUILT), outgoing),
                    number(MadokaSpellEffects.REDEMPTION_MIN_GUILT)));
            default -> List.of();
        };
    }

    private static String param(SpellDefinition spell, String key) {
        return number(spell.parameter(key));
    }

    private static String distance(double base) {
        return number(SpellCasting.range(base));
    }

    private static List<Component> pollen(ServerPlayer player, long now, double outgoing) {
        var targets = MadokaSpellEffects.pollenTargets(player, now);
        if (targets.isEmpty()) {
            return List.of(line("no_mark"));
        }
        var values = targets.stream().mapToDouble(target -> ButterflyMark.getDamageMultiplier(target, now))
                .summaryStatistics();
        return List.of(line("pollen",
                damageRange(player, MadokaSpellEffects.POLLEN_INITIAL_DAMAGE, outgoing, values.getMin(), values.getMax()),
                damageRange(player, MadokaSpellEffects.POLLEN_TICK_DAMAGE, outgoing, values.getMin(), values.getMax())));
    }

    private static String damageRange(ServerPlayer player, double base, double outgoing, double min, double max) {
        String low = amount(player, base * min, outgoing);
        String high = amount(player, base * max, outgoing);
        return low.equals(high) ? low : low + "–" + high;
    }

    private static List<Component> redemption(ServerPlayer player, double outgoing) {
        var target = MadokaSpellEffects.redemptionTarget(player);
        if (target == null) {
            return List.of(line("no_guilt"));
        }
        float guilt = GuiltMark.getAmount(target, player.getUUID());
        return List.of(damage(player, MadokaSpellEffects.redemptionDamage(guilt), outgoing),
                line("recoil", number(guilt), number(guilt * MadokaSpellEffects.REDEMPTION_RECOIL_RATIO)));
    }

    private static Component damage(ServerPlayer player, double base, double outgoing) {
        return line("hit", amount(player, base, outgoing));
    }

    private static String amount(ServerPlayer player, double base, double outgoing) {
        return number(MadokaMath.outgoingDamage(player, MadokaMath.finalDamage(player, base), outgoing));
    }

    public static Component line(String key, Object... arguments) {
        return Component.translatable("spell.madoka_magic.preview." + key, arguments);
    }

    public static String number(double number) {
        return String.format(Locale.ROOT, "%.2f", number).replaceFirst("\\.?0+$", "");
    }
}
