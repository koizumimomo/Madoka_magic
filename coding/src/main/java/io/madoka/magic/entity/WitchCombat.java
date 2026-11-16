package io.madoka.magic.entity;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WitchCombat {
    public static final ResourceKey<DamageType> FAMILIAR_LINK = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "familiar_link"));
    private static final int LINK_LIMIT = 4;
    private static final double COCOON_RANGE = 24.0D;
    private static final int COCOON_STAGGER = 40;
    private static final ThreadLocal<ServerPlayer> STATIONARY_HIT = new ThreadLocal<>();

    private final MadokaWitchEntity witch;
    private final WitchArenaAttack arena;
    private final QuintetWitchCombat quintet;
    private final AtonementWitchCombat atonement;
    private final WalpurgisCombat walpurgis;
    private final CandeloroCombat candeloro;
    private UUID cocoon;
    private boolean usedCocoon;
    private long staggerUntil;

    WitchCombat(MadokaWitchEntity witch) {
        this.witch = witch;
        arena = new WitchArenaAttack(witch);
        quintet = new QuintetWitchCombat(witch);
        atonement = new AtonementWitchCombat(witch);
        walpurgis = new WalpurgisCombat(witch);
        candeloro = new CandeloroCombat(witch);
    }

    WitchArenaAttack arena() {
        return arena;
    }

    static void hurtWithoutKnockback(ServerPlayer player, DamageSource source, float damage) {
        ServerPlayer previous = STATIONARY_HIT.get();
        STATIONARY_HIT.set(player);
        try {
            player.hurt(source, damage);
        } finally {
            if (previous == null) {
                STATIONARY_HIT.remove();
            } else {
                STATIONARY_HIT.set(previous);
            }
        }
    }

    @SubscribeEvent
    public static void suppressStationaryHitKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() == STATIONARY_HIT.get()) {
            event.setCanceled(true);
        }
    }

    void tick(ServerLevel level) {
        switch (witch.getThemeId()) {
            case "sacrifice" -> {
                tickCocoon(level);
                arena.tick(level, false);
            }
            case "take" -> {
                if (witch.tickCount % 5 == 0) {
                    updateLinks(linkedBees(level));
                }
                arena.tick(level, true);
            }
            case "atonement" -> atonement.tick(level);
            case "candeloro" -> candeloro.tick(level);
            case WitchTypes.SALVATION -> quintet.tickSalvation(level);
            case WitchTypes.NUTCRACKER -> quintet.tickNutcracker(level);
            case WitchTypes.MERMAID -> quintet.tickMermaid(level);
            case WitchTypes.OPHELIA -> quintet.tickOphelia(level);
            case WitchTypes.NIGHT -> walpurgis.tick(level);
        }
    }

    /** Cancel a telegraphed attack before its first hit or projectile. */
    public boolean interruptWindup(ServerLevel level) {
        long now = level.getGameTime();
        return switch (witch.getThemeId()) {
            case "sacrifice", "take" -> arena.interruptWindup(now);
            case "atonement" -> atonement.interrupt(now);
            case "candeloro" -> candeloro.interrupt(now);
            case WitchTypes.NIGHT -> walpurgis.interrupt(now);
            case WitchTypes.SALVATION, WitchTypes.NUTCRACKER, WitchTypes.MERMAID, WitchTypes.OPHELIA ->
                    quintet.interruptWindup(now);
            default -> false;
        };
    }

    private void tickCocoon(ServerLevel level) {
        long now = level.getGameTime();
        if (cocoon != null) {
            if (activeCocoon(level) == null) {
                if (level.getEntity(cocoon) instanceof MadokaWitchEntity princess && princess.isDeadOrDying()) {
                    staggerUntil = now + COCOON_STAGGER;
                }
                cocoon = null;
                updateLinks(List.of());
            } else {
                updateLinks(List.of(activeCocoon(level)));
            }
        }
        if (usedCocoon || ThornTracker.isBound(witch)
                || witch.getHealth() > witch.getMaxHealth() * MadokaConfig.WITCH_COCOON_TRIGGER.get()) {
            return;
        }
        var princess = level.getEntitiesOfClass(MadokaWitchEntity.class, witch.getBoundingBox().inflate(COCOON_RANGE),
                        entity -> entity.isPrincess() && entity.isAlive() && !entity.isCocooned() && sameEncounter(entity))
                .stream().min(Comparator.comparingDouble(witch::distanceToSqr)).orElse(null);
        if (princess == null) {
            return;
        }
        usedCocoon = true;
        cocoon = princess.getUUID();
        princess.beginCocoon(witch.getUUID(), now + MadokaConfig.WITCH_COCOON_TICKS.get());
        updateLinks(List.of(princess));
        witch.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.72F);
    }

    boolean hasShield(ServerLevel level) {
        return activeCocoon(level) != null;
    }

    boolean isStaggered(ServerLevel level) {
        return level.getGameTime() < staggerUntil;
    }

    private MadokaWitchEntity activeCocoon(ServerLevel level) {
        if (cocoon != null && level.getEntity(cocoon) instanceof MadokaWitchEntity princess
                && princess.isAlive() && princess.cocoonProtects(witch.getUUID(), level.getGameTime())) {
            return princess;
        }
        return null;
    }

    public float handleDamage(ServerLevel level, DamageSource source, float amount) {
        if (amount <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return amount;
        }
        if (witch.isPrincess()) {
            if (witch.isCocooned() && level.getEntity(witch.cocoonBoss()) instanceof MadokaWitchEntity boss) {
                boss.markAssistedBy(source.getEntity());
            }
            return amount;
        }
        if ("take".equals(witch.getThemeId())) {
            return redirectToBees(level, source, amount);
        }
        if (witch.isAtonement()) {
            atonement.recordDamage(level.getGameTime(), amount);
        }
        return amount;
    }

    private float redirectToBees(ServerLevel level, DamageSource source, float amount) {
        var bees = linkedBees(level);
        updateLinks(bees);
        if (bees.isEmpty()) {
            return amount;
        }
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(FAMILIAR_LINK);
        var transfer = new DamageSource(type, source.getDirectEntity(), source.getEntity());
        float share = amount / bees.size();
        int received = 0;
        for (var bee : bees) {
            if (bee.hurt(transfer, share)) {
                received++;
            }
        }
        if (received > 0) {
            witch.markAssistedBy(source.getEntity());
        }
        updateLinks(linkedBees(level));
        return share * (bees.size() - received);
    }

    private List<BlackBeeEntity> linkedBees(ServerLevel level) {
        double radius = MadokaConfig.WITCH_BEE_LINK_RADIUS.get();
        return level.getEntitiesOfClass(BlackBeeEntity.class, witch.getBoundingBox().inflate(radius),
                        bee -> bee.isAlive() && sameEncounter(bee) && witch.distanceToSqr(bee) <= radius * radius)
                .stream().sorted(Comparator.<BlackBeeEntity>comparingDouble(witch::distanceToSqr)
                        .thenComparingInt(Entity::getId)).limit(LINK_LIMIT).toList();
    }

    private void updateLinks(List<? extends Entity> links) {
        witch.setSkillLinks(links.stream().mapToInt(Entity::getId).toArray());
    }

    private boolean sameEncounter(Entity entity) {
        return Objects.equals(encounterOf(witch), encounterOf(entity));
    }

    private static UUID encounterOf(Entity entity) {
        var tag = entity.getPersistentData();
        return tag.hasUUID(WitchFamiliars.ENCOUNTER_TAG) ? tag.getUUID(WitchFamiliars.ENCOUNTER_TAG) : null;
    }

    boolean allowsVisitor(ServerPlayer player) {
        return FamiliarVisibility.canWitchTarget(player) && !player.isSpectator()
                && WitchFamiliars.isEncounterVisitor(witch, player);
    }

    boolean breakRainNode(ServerPlayer player, long castEnd) {
        return witch.isAtonement() && atonement.breakRainNode(player, castEnd);
    }

    boolean hasRainNode(long castEnd) {
        return witch.isAtonement() && atonement.hasRainNode(castEnd);
    }

    void save(CompoundTag tag) {
        tag.put("Arena", arena.save());
        if (WitchTypes.SALVATION.equals(witch.getThemeId()) || WitchTypes.NUTCRACKER.equals(witch.getThemeId())
                || WitchTypes.MERMAID.equals(witch.getThemeId()) || witch.isOphelia()) {
            var state = new CompoundTag();
            quintet.save(state);
            tag.put("Quintet", state);
        }
        if (cocoon != null) {
            tag.putUUID("Cocoon", cocoon);
        }
        tag.putBoolean("UsedCocoon", usedCocoon);
        tag.putLong("StaggerUntil", staggerUntil);
        if (witch.isAtonement()) {
            atonement.save(tag);
        }
        if (witch.isCandeloro()) {
            var state = new CompoundTag();
            candeloro.save(state);
            tag.put("Candeloro", state);
        }
    }

    void load(CompoundTag tag) {
        arena.load(tag.getCompound("Arena"));
        quintet.load(tag.getCompound("Quintet"));
        cocoon = tag.hasUUID("Cocoon") ? tag.getUUID("Cocoon") : null;
        usedCocoon = tag.getBoolean("UsedCocoon");
        staggerUntil = tag.getLong("StaggerUntil");
        atonement.load(tag);
        candeloro.load(tag.getCompound("Candeloro"));
    }
}
