package io.madoka.magic.entity;

import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

final class WitchBossBar {
    private final ServerBossEvent bar = new ServerBossEvent(
            Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
    private final Set<ServerPlayer> tracking = new HashSet<>();
    private String theme = "";

    void track(ServerPlayer player) {
        tracking.add(player);
    }

    void untrack(ServerPlayer player) {
        tracking.remove(player);
        bar.removePlayer(player);
    }

    void tick(LivingEntity witch) {
        if (tracking.isEmpty()) {
            return;
        }
        if (!witch.isAlive()) {
            tracking.clear();
            bar.removeAllPlayers();
            return;
        }
        String currentTheme = WitchTypes.bossTheme(witch);
        if (!theme.equals(currentTheme)) {
            theme = currentTheme;
            bar.setName(WitchTypes.GLUTTONY.equals(theme)
                    ? Component.translatable(WitchTypes.GLUTTONY_BAR_TITLE) : GriefSeedItem.witchName(theme));
        }
        bar.setProgress(witch.getHealth() / witch.getMaxHealth());
        // The portal records the visit after teleporting, so tracking can begin before the visit exists.
        for (ServerPlayer player : tracking) {
            if (WitchTypes.NIGHT.equals(theme)
                    || WitchFamiliars.isEncounterVisitor(witch, player) && FamiliarVisibility.canWitchTarget(player)) {
                bar.addPlayer(player);
            } else {
                bar.removePlayer(player);
            }
        }
    }
}
