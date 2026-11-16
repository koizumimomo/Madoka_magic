package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.registry.ModParticles.Take;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class HoneySwarm {
    private static final String OWNER = "madoka_swarm_owner";
    private static final String EXPIRY = "madoka_swarm_expiry";
    private static final String SLOT = "madoka_swarm_slot";
    private static final Map<ServerLevel, Map<Bee, Flight>> SWARMS = new HashMap<>();

    private static final class Flight {
        final Bee bee;
        final UUID owner;
        final long end;
        final int slot;
        LivingEntity target;
        long nextSting;

        Flight(Bee bee) {
            var tag = bee.getPersistentData();
            this.bee = bee;
            owner = tag.getUUID(OWNER);
            end = tag.getLong(EXPIRY);
            slot = tag.getInt(SLOT);
        }
    }

    private HoneySwarm() {}

    static int summon(ServerPlayer caster, SpellDefinition spell, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        var flights = SWARMS.getOrDefault(level, Map.of());
        long now = level.getGameTime();
        UUID owner = caster.getUUID();
        int existing = (int) flights.values().stream().filter(f -> f.owner.equals(owner) && f.bee.isAlive() && f.end > now).count();
        int count = Math.min((int) spell.parameter("count"), (int) spell.parameter("max_bees") - existing);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }
            bee.setNoAi(true);
            bee.setNoGravity(true);
            bee.setSilent(true);
            var tag = bee.getPersistentData();
            tag.putUUID(OWNER, owner);
            tag.putLong(EXPIRY, now + spell.ticks("duration_seconds"));
            tag.putInt(SLOT, existing + i);
            bee.setPos(orbit(caster, now, existing + i));
            if (level.addFreshEntity(bee)) {
                TakeVisuals.burst(level, bee.position(), 4, 0.15D);
                spawned++;
            }
        }
        if (spawned <= 0) {
            return SpellCasting.RESULT_NOT_READY;
        }
        level.playSound(null, caster.blockPosition(), SoundEvents.BEE_LOOP, SoundSource.PLAYERS, 0.8F, 1.2F);
        return SpellCasting.RESULT_SUCCESS;
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Bee bee
                && bee.getPersistentData().hasUUID(OWNER)) {
            SWARMS.computeIfAbsent(level, ignored -> new HashMap<>()).put(bee, new Flight(bee));
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SWARMS.remove(level);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        var flights = SWARMS.get(level);
        if (flights == null) {
            return;
        }
        SpellDefinition spell = SpellRegistry.get("swarm_call");
        for (Flight flight : List.copyOf(flights.values())) {
            if (!tickFlight(level, flight, spell)) {
                flights.remove(flight.bee);
            }
        }
        if (flights.isEmpty()) {
            SWARMS.remove(level);
        }
    }

    private static boolean tickFlight(ServerLevel level, Flight flight, SpellDefinition spell) {
        Bee bee = flight.bee;
        if (!bee.isAlive() || bee.isRemoved() || bee.level() != level) {
            return false;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(flight.owner);
        long now = level.getGameTime();
        if (spell == null || now >= flight.end || owner == null || !owner.isAlive()
                || owner.level() != level || !PlayerMadokaData.of(owner).isTransformed()) {
            bee.discard();
            return false;
        }
        double range = SpellCasting.range(spell.parameter("range"));
        if (bee.distanceToSqr(owner) > (range + 4.0D) * (range + 4.0D)) {
            bee.discard();
            return false;
        }
        if ((now + flight.slot) % 6 == 0) {
            flight.target = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range),
                    mob -> Faction.isFamiliar(mob) && SpellCasting.canAffect(owner, mob)
                            && owner.distanceToSqr(mob) <= range * range && bee.hasLineOfSight(mob)).stream()
                    .min(Comparator.comparingDouble(bee::distanceToSqr)).orElse(null);
        }
        LivingEntity target = flight.target;
        if (target != null && (!target.isAlive() || target.level() != level)) {
            target = null;
        }
        Vec3 aim = target == null ? orbit(owner, now, flight.slot) : target.getBoundingBox().getCenter();
        Vec3 offset = aim.subtract(bee.position());
        if (target != null && offset.lengthSqr() <= 0.82D * 0.82D && now >= flight.nextSting
                && bee.hasLineOfSight(target)) {
            float damage = (float) MadokaMath.finalDamage(owner, spell.parameter("sting_damage"));
            if (target.hurt(level.damageSources().playerAttack(owner), damage)) {
                TakeVisuals.burst(level, target.getBoundingBox().getCenter(), 5, 0.19D);
                level.playSound(null, bee.blockPosition(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.45F, 1.4F);
            }
            flight.nextSting = now + Math.max(1, spell.ticks("sting_interval_seconds"));
        } else if (offset.lengthSqr() > 0.16D) {
            double step = target == null ? 0.28D : SpellCasting.range(0.38D);
            Vec3 movement = offset.normalize().scale(Math.min(step, offset.length()));
            float yaw = (float) (Mth.atan2(-movement.x, movement.z) * Mth.RAD_TO_DEG);
            bee.setYRot(yaw);
            bee.setYBodyRot(yaw);
            bee.setYHeadRot(yaw);
            bee.move(MoverType.SELF, movement);
        }
        if ((now + flight.slot) % 6 == 0) {
            TakeVisuals.mote(level, Take.HONEY, bee.position(), Vec3.ZERO);
        }
        return true;
    }

    private static Vec3 orbit(ServerPlayer caster, long now, int slot) {
        double angle = now * 0.105D + slot * Math.PI * 2.0D / 3.0D;
        double radius = slot < 3 ? 1.1D : 1.6D;
        return caster.position().add(radius * Math.cos(angle), 1.35D + 0.18D * Math.sin(angle * 2.0D),
                radius * Math.sin(angle));
    }
}
