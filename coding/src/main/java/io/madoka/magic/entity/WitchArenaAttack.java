package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.spell.ThornTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WitchArenaAttack {
    private static final int SECTORS = 4;
    private static final int ALL_SECTORS = (1 << SECTORS) - 1;
    private static final int FLASH_TICKS = 12;
    private static final double HEIGHT = 8.0D;

    private record Charm(int count, long expires) {}

    private final MadokaWitchEntity witch;
    private final Map<UUID, Charm> charms = new HashMap<>();
    private BlockPos anchor;
    private double radius;
    private long readyAt;
    private long castId;
    private long nextBlast;
    private long flashUntil;
    private int pending;
    private int flash;
    private int step;
    private int firstSector;

    WitchArenaAttack(MadokaWitchEntity witch) {
        this.witch = witch;
    }

    static BlockPos encounterAnchor(ServerLevel level, MadokaWitchEntity witch) {
        var tag = witch.getPersistentData();
        if (!witch.getThemeId().equals(MadokaDimensions.barrierTheme(level.dimension()))
                || !tag.hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
            return null;
        }
        var encounter = MadokaSavedData.get(level).getEncounter(tag.getUUID(WitchFamiliars.ENCOUNTER_TAG));
        if (encounter == null || encounter.defeated() || !witch.getThemeId().equals(encounter.theme())) {
            return null;
        }
        var arena = encounter.arena();
        return arena.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, arena.getX(), arena.getZ()));
    }

    void tick(ServerLevel level, boolean bee) {
        long now = level.getGameTime();
        if (flash != 0 && now >= flashUntil) {
            flash = 0;
            syncVisual(bee, now);
        }
        if (pending != 0) {
            if (now > nextBlast + 40 || level.players().stream().noneMatch(witch.combat()::allowsVisitor)) {
                finish(now);
            } else if (now >= nextBlast) {
                detonate(level, targets(level, anchor, radius), bee, now);
            }
            syncVisual(bee, now);
            return;
        }
        if (now < readyAt || now % 10 != 0 || ThornTracker.isBound(witch) || !bee && witch.combat().hasShield(level)) {
            return;
        }
        if (bee) {
            charms.values().removeIf(charm -> charm.expires() <= now);
        }
        BlockPos center = encounterAnchor(level, witch);
        double reach = MadokaConfig.WITCH_ARENA_RADIUS.get();
        if (center == null || targets(level, center, reach).isEmpty()) {
            return;
        }
        if (readyAt == 0) {
            readyAt = now + MadokaConfig.WITCH_ARENA_COOLDOWN.get();
            return;
        }
        anchor = center;
        radius = reach;
        castId = now;
        nextBlast = now + MadokaConfig.WITCH_ARENA_CHARGE.get();
        step = 0;
        firstSector = bee ? witch.getRandom().nextInt(SECTORS) : 0;
        for (int sector = 0; sector < SECTORS; sector++) {
            if (ArenaCocoonEntity.spawn(level, nodePos(level, sector), witch, castId, sector, bee)) {
                pending |= 1 << sector;
            }
        }
        if (pending == 0) {
            finish(now);
        } else {
            witch.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, bee ? 1.3F : 0.7F);
        }
        syncVisual(bee, now);
    }

    private BlockPos nodePos(ServerLevel level, int sector) {
        int offset = Math.max(2, (int) Math.round(radius * 0.35D));
        int x = anchor.getX() + ((sector & 1) == 0 ? offset : -offset);
        int z = anchor.getZ() + ((sector & 2) == 0 ? offset : -offset);
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private List<ServerPlayer> targets(ServerLevel level, BlockPos center, double reach) {
        Vec3 origin = Vec3.atBottomCenterOf(center);
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(origin, origin).inflate(reach, HEIGHT, reach),
                player -> player.isAlive() && !player.getAbilities().invulnerable && witch.combat().allowsVisitor(player)
                        && player.position().subtract(origin).horizontalDistanceSqr() <= reach * reach);
    }

    static int sector(double x, double z) {
        return (x < 0 ? 1 : 0) | (z < 0 ? 2 : 0);
    }

    private int currentSector() {
        return (firstSector + step) % SECTORS;
    }

    private void detonate(ServerLevel level, List<ServerPlayer> players, boolean bee, long now) {
        int due = bee ? 1 << currentSector() : ALL_SECTORS;
        int detonated = pending & due;
        pending &= ~due;
        if (detonated != 0) {
            flash = detonated;
            flashUntil = now + FLASH_TICKS;
            float damage = (bee ? MadokaConfig.WITCH_ARENA_BEE_DAMAGE : MadokaConfig.WITCH_ARENA_BUTTERFLY_DAMAGE)
                    .get().floatValue();
            Vec3 center = Vec3.atBottomCenterOf(anchor);
            for (var player : players) {
                Vec3 offset = player.position().subtract(center);
                if ((detonated & (1 << sector(offset.x, offset.z))) != 0
                        && player.hurt(witch.damageSources().indirectMagic(witch, witch), damage) && bee) {
                    charm(level, player, now);
                }
            }
            for (int sector = 0; sector < SECTORS; sector++) {
                if ((detonated & (1 << sector)) == 0) {
                    continue;
                }
                var point = Vec3.atBottomCenterOf(nodePos(level, sector)).add(0, 0.8D, 0);
                level.sendParticles(bee ? ParticleTypes.WAX_OFF : ParticleTypes.CRIT, point.x, point.y, point.z,
                        24, 1.3D, 0.65D, 1.3D, 0.14D);
                level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE,
                        witch.getSoundSource(), 0.85F, bee ? 1.25F : 0.95F);
            }
        }
        if (pending == 0 || ++step == SECTORS) {
            finish(now);
        } else {
            nextBlast = now + MadokaConfig.WITCH_ARENA_BEE_STEP.get();
        }
    }

    private void charm(ServerLevel level, ServerPlayer player, long now) {
        Charm old = charms.get(player.getUUID());
        int count = old == null || old.expires() <= now ? 1 : old.count() + 1;
        int threshold = MadokaConfig.WITCH_ARENA_CHARM_THRESHOLD.get();
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + player.getBbHeight() + 0.15D,
                player.getZ(), Math.min(count, 5), 0.36D, 0.2D, 0.36D, 0.02D);
        if (count >= threshold) {
            charms.remove(player.getUUID());
            witch.heal(witch.getMaxHealth() * MadokaConfig.WITCH_ARENA_CHARM_HEAL.get().floatValue());
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MadokaConfig.WITCH_ARENA_CHARM_SLOW_TICKS.get(), 2));
            player.displayClientMessage(Component.translatable("message.madoka_magic.arena.charmed"), true);
        } else {
            charms.put(player.getUUID(), new Charm(count, now + MadokaConfig.WITCH_ARENA_CHARM_TICKS.get()));
            player.displayClientMessage(Component.translatable("message.madoka_magic.arena.charm_count", count, threshold), true);
        }
    }

    private void finish(long now) {
        pending = 0;
        readyAt = now + MadokaConfig.WITCH_ARENA_COOLDOWN.get();
    }

    boolean interruptWindup(long now) {
        if (pending == 0 || step != 0 || now >= nextBlast) {
            return false;
        }
        finish(now);
        syncVisual(witch.isTakingWitch(), now);
        return true;
    }

    boolean hasNode(long cast, int sector) {
        return witch.isAlive() && cast == castId && sector >= 0 && sector < SECTORS && (pending & (1 << sector)) != 0;
    }

    void breakNode(long cast, int sector) {
        if (hasNode(cast, sector)) {
            pending &= ~(1 << sector);
            long now = witch.level().getGameTime();
            if (pending == 0) {
                finish(now);
            }
            syncVisual("take".equals(witch.getThemeId()), now);
        }
    }

    private void syncVisual(boolean bee, long now) {
        var data = new CompoundTag();
        if (pending != 0 || flash != 0) {
            data.putLong("Center", anchor.asLong());
            data.putFloat("Radius", (float) radius);
            data.putInt("Pending", pending);
            data.putInt("Flash", flash);
            data.putLong("FlashUntil", flashUntil);
            data.putInt("Warning", nextBlast - now <= 24 ? pending & (bee ? 1 << currentSector() : ALL_SECTORS) : 0);
        }
        witch.setArenaVisual(data);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Ready", readyAt);
        if (pending != 0) {
            tag.putLong("Center", anchor.asLong());
            tag.putDouble("Radius", radius);
            tag.putLong("Cast", castId);
            tag.putLong("Blast", nextBlast);
            tag.putInt("Pending", pending);
            tag.putInt("Step", step);
            tag.putInt("First", firstSector);
        }
        var list = new ListTag();
        charms.forEach((id, charm) -> {
            var entry = new CompoundTag();
            entry.putUUID("Player", id);
            entry.putInt("Count", charm.count());
            entry.putLong("Expires", charm.expires());
            list.add(entry);
        });
        tag.put("Charms", list);
        return tag;
    }

    void load(CompoundTag tag) {
        readyAt = tag.getLong("Ready");
        anchor = tag.contains("Center") ? BlockPos.of(tag.getLong("Center")) : null;
        radius = tag.getDouble("Radius");
        castId = tag.getLong("Cast");
        nextBlast = tag.getLong("Blast");
        pending = anchor == null ? 0 : tag.getInt("Pending") & ALL_SECTORS;
        step = tag.getInt("Step");
        firstSector = tag.getInt("First");
        charms.clear();
        for (Tag value : tag.getList("Charms", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (entry.hasUUID("Player")) {
                charms.put(entry.getUUID("Player"), new Charm(entry.getInt("Count"), entry.getLong("Expires")));
            }
        }
    }
}
