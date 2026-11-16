package io.madoka.magic.client;

import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.spell.SpellDefinition;
import io.madoka.magic.spell.SpellPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SkillDetails {
    private static Map<String, SpellPreview> previews = Map.of();
    private static int lastRequest = -10;

    private SkillDetails() {}

    public static void accept(MadokaNetwork.SkillPreviews packet) {
        if (packet.theme().equals(ClientMadokaState.getThemeId())) {
            previews = Map.copyOf(packet.values());
        }
    }

    static void reset() {
        previews = Map.of();
        lastRequest = -10;
    }

    static void refresh() {
        var player = Minecraft.getInstance().player;
        if (player != null && ClientMadokaState.isContracted()
                && (player.tickCount - lastRequest >= 10 || player.tickCount < lastRequest)) {
            lastRequest = player.tickCount;
            MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.RequestSkillPreview());
        }
    }

    static int unlockPurity(SpellDefinition spell) {
        var preview = previews.get(spell.id());
        return preview == null ? spell.unlockLevel() : preview.unlock();
    }

    static List<Component> numbers(SpellDefinition spell, boolean wheel, boolean shift) {
        var preview = previews.get(spell.id());
        if (preview == null) {
            return List.of(SpellPreview.line("loading"));
        }
        List<Component> lines = new ArrayList<>();
        if (spell.active()) {
            String cooldown = SpellPreview.number(preview.cooldownTicks() / 20.0D);
            if (wheel && !ClientMadokaState.isWitchPath() && ClientMadokaState.getMana() < preview.mana()) {
                lines.add(SpellPreview.line("taint_instead", SpellPreview.number(preview.taint())));
                lines.add(SpellPreview.line("cooldown", cooldown));
            } else {
                lines.add(SpellPreview.line("cost", SpellPreview.number(preview.mana()), cooldown));
                if (wheel && ClientMadokaState.isWitchPath() && ClientMadokaState.getMana() < preview.mana()) {
                    lines.add(Component.translatable("message.madoka_magic.spell.no_mana"));
                }
            }
            if (!wheel && preview.taint() > 0) {
                lines.add(SpellPreview.line("taint_instead", SpellPreview.number(preview.taint())));
            }
        }
        var details = preview.details();
        boolean alternate = !preview.alternateCost().getString().isEmpty();
        if (alternate && wheel && shift) {
            // Wither has one damage line followed by its extra health cost.
            lines.add(details.get(0));
        } else {
            lines.addAll(details);
        }
        if (alternate && (!wheel || shift)) {
            lines.add(preview.alternateCost());
        }
        if (!wheel) {
            lines.addAll(preview.mechanics());
        }
        return lines;
    }
}
