package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.registry.ModParticles.Take;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleConsumer;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class TakeSpells {
    private static final String HONEY = "honey";
    private static final UUID HONEY_SLOW = UUID.fromString("8663a359-8fba-418b-854b-d2569bfc7ee2");
    private static final DustParticleOptions AMBER = new DustParticleOptions(
            new Vector3f(0.91F, 0.64F, 0.24F), 0.8F);
    private static final Map<ServerLevel, FieldState> FIELDS = new HashMap<>();

    private TakeSpells() {
    }

    static void registerEffects() {
        SpellCasting.registerEffect(MadokaMagic.MODID + ":honey", TakeSpells::honey);
        SpellCasting.registerEffect(MadokaMagic.MODID + ":injection", TakeSpells::injection);
        SpellCasting.registerEffect(MadokaMagic.MODID + ":illusion", TakeSpells::illusion);
        SpellCasting.registerEffect(MadokaMagic.MODID + ":dandelion_drift", TakeSpells::dandelion);
        SpellCasting.registerEffect(MadokaMagic.MODID + ":swarm_call", HoneySwarm::summon);
    }

    private record Zone(UUID cast, Vec3 center, SpellDefinition definition, long end) {
    }

    private record Coating(SpellDefinition definition, Map<UUID, Long> layers) {
    }

    private record Illusion(Mob target, SpellDefinition definition, long end) {
    }

    private static final class Seed {
        final UUID cast;
        final Mob target;
        final ServerPlayer caster;
        final SpellDefinition definition;
        final long start;
        final long end;
        Vec3 position;
        Vec3 velocity;

        Seed(UUID cast, Mob target, ServerPlayer caster, SpellDefinition definition, long now,
             Vec3 position, Vec3 velocity) {
            this.cast = cast;
            this.target = target;
            this.caster = caster;
            this.definition = definition;
            start = now;
            end = now + definition.ticks("lifetime_seconds");
            this.position = position;
            this.velocity = velocity;
        }
    }

    private record Nectar(ServerPlayer caster, Vec3 origin, long start) {
    }

    private static final class FieldState {
        final List<Zone> zones = new ArrayList<>();
        final List<Seed> seeds = new ArrayList<>();
        final Map<Mob, Coating> coatings = new HashMap<>();
        final Map<Mob, Illusion> illusions = new HashMap<>();
        final List<Nectar> nectar = new ArrayList<>();

        boolean isEmpty() {
            return zones.isEmpty() && seeds.isEmpty() && coatings.isEmpty()
                    && illusions.isEmpty() && nectar.isEmpty();
        }
    }

    private static FieldState state(ServerLevel level) {
        return FIELDS.computeIfAbsent(level, key -> new FieldState());
    }

    private static int honey(ServerPlayer caster, SpellDefinition definition, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        Vec3 eye = caster.getEyePosition();
        double range = SpellCasting.range(definition.parameter("range"));
        Vec3 look = caster.getLookAngle();
        var hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) {
            var aim = SpellCasting.aimedMob(caster, definition.parameter("range"), Faction::isFamiliar);
            Vec3 point = aim != null ? aim.getBoundingBox().getCenter() : hit.getType() == HitResult.Type.MISS
                    ? eye.add(look.scale(Math.min(range, definition.parameter("range") * 0.55D)))
                    : hit.getLocation().relative(hit.getDirection(), 0.10D);
            hit = ground(caster, point, range);
            if (hit.getType() == HitResult.Type.MISS) {
                hit = ground(caster, eye, range);
            }
        }
        if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != Direction.UP) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        Vec3 center = hit.getLocation();
        Zone zone = new Zone(UUID.randomUUID(), center, definition,
                level.getGameTime() + definition.ticks("duration_seconds"));
        FieldState state = state(level);
        state.zones.add(zone);
        pulseZone(level, state, zone, level.getGameTime());
        TakeVisuals.honeyField(level, center, SpellCasting.range(definition.parameter("radius")), level.getGameTime(), true);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static BlockHitResult ground(ServerPlayer caster, Vec3 point, double range) {
        return caster.serverLevel().clip(new ClipContext(point, point.add(0.0D, -range, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
    }

    private static int injection(ServerPlayer caster, SpellDefinition definition, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        if (!(SpellCasting.aimedMob(caster, definition.parameter("range"), Faction::isFamiliar) instanceof Mob target)) {
            return SpellCasting.RESULT_NO_TARGET;
        }
        float before = target.getHealth();
        float damage = (float) MadokaMath.finalDamage(caster, definition.parameter("damage"));
        boolean hit = target.hurt(level.damageSources().playerAttack(caster), damage);
        float healthLost = before - target.getHealth();
        if (!hit || healthLost <= 0.0F) {
            return SpellCasting.RESULT_NOT_READY;
        }
        double healRatio = definition.parameter("heal_ratio");
        FieldState state = state(level);
        if (state.coatings.containsKey(target)) {
            healRatio += definition.parameter("honey_heal_bonus");
        }
        caster.heal((float) (healthLost * healRatio));
        reward.accept(definition.parameter("mana_return"));
        state.nectar.add(new Nectar(caster, target.getBoundingBox().getCenter(), level.getGameTime()));
        TakeVisuals.beam(level, target.getBoundingBox().getCenter(), caster.getEyePosition().add(0, -0.3D, 0));
        TakeVisuals.burst(level, target.getBoundingBox().getCenter(), 8, 0.22D);
        return SpellCasting.RESULT_SUCCESS;
    }

    private static int illusion(ServerPlayer caster, SpellDefinition definition, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        double radius = SpellCasting.range(definition.parameter("radius"));
        List<Mob> mobs = familiars(level, caster.getBoundingBox().inflate(radius)).stream()
                .filter(mob -> caster.distanceToSqr(mob) <= radius * radius)
                .toList();
        long end = level.getGameTime() + definition.ticks("duration_seconds");
        boolean applied = false;
        for (Mob mob : mobs) {
            Mob target = mobs.stream()
                    .filter(other -> other != mob && mob.canAttack(other) && mob.hasLineOfSight(other))
                    .min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            if (target != null) {
                state(level).illusions.put(mob, new Illusion(target, definition, end));
                mob.setTarget(target);
                TakeVisuals.illusion(level, mob, level.getGameTime());
                applied = true;
            }
        }
        return applied ? SpellCasting.RESULT_SUCCESS : SpellCasting.RESULT_NO_TARGET;
    }

    private static int dandelion(ServerPlayer caster, SpellDefinition definition, DoubleConsumer reward) {
        ServerLevel level = caster.serverLevel();
        FieldState state = state(level);
        double range = SpellCasting.range(definition.parameter("range"));
        List<Mob> targets = familiars(level, caster.getBoundingBox().inflate(range)).stream()
                .filter(mob -> caster.distanceToSqr(mob) <= range * range && caster.hasLineOfSight(mob))
                .sorted(Comparator.<Mob, Boolean>comparing(mob -> !state.coatings.containsKey(mob))
                        .thenComparingDouble(caster::distanceToSqr))
                .toList();
        int count = (int) definition.parameter("seed_count");
        UUID cast = UUID.randomUUID();
        Vec3 look = caster.getLookAngle();
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0D * index / count;
            Vec3 offset = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Mob target = targets.isEmpty() ? null : targets.get(index % targets.size());
            Vec3 position = caster.getEyePosition().add(look.scale(0.4D)).add(offset.scale(0.25D));
            Vec3 drift = look.scale(SpellCasting.range(definition.parameter("drift_speed"))).add(offset.scale(0.035D));
            state.seeds.add(new Seed(cast, target, caster, definition, level.getGameTime(), position, drift));
        }
        return count > 0 ? SpellCasting.RESULT_SUCCESS : SpellCasting.RESULT_NOT_READY;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        FieldState state = FIELDS.get(level);
        if (state == null) {
            return;
        }
        long now = level.getGameTime();
        state.zones.removeIf(zone -> now >= zone.end());
        if (now % 5L == 0L) {
            for (Zone zone : state.zones) {
                pulseZone(level, state, zone, now);
            }
        }
        state.seeds.removeIf(seed -> tickSeed(level, state, seed, now));
        tickCoatings(level, state, now);
        state.illusions.entrySet().removeIf(entry -> tickIllusion(level, entry.getKey(), entry.getValue(), now));
        state.nectar.removeIf(nectar -> tickNectar(level, nectar, now));
        if (state.isEmpty()) {
            FIELDS.remove(level);
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FIELDS.remove(level);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        FieldState state = FIELDS.get(level);
        Illusion illusion = state == null ? null : state.illusions.get(mob);
        if (illusion != null && isValidIllusion(level, mob, illusion, level.getGameTime())) {
            event.setNewTarget(illusion.target());
        }
    }

    private static void pulseZone(ServerLevel level, FieldState state, Zone zone, long now) {
        double radius = SpellCasting.range(zone.definition().parameter("radius"));
        double height = SpellCasting.range(zone.definition().parameter("height"));
        Vec3 center = zone.center();
        AABB bounds = new AABB(center, center).inflate(radius, height, radius);
        for (Mob mob : familiars(level, bounds)) {
            double dx = mob.getX() - center.x;
            double dz = mob.getZ() - center.z;
            if (dx * dx + dz * dz <= radius * radius) {
                coat(state, mob, zone.cast(), zone.definition(), now);
            }
        }
        TakeVisuals.honeyField(level, center, radius, now, false);
    }

    private static void coat(FieldState state, Mob mob, UUID cast, SpellDefinition definition, long now) {
        Coating coating = state.coatings.computeIfAbsent(mob, key -> new Coating(definition, new HashMap<>()));
        coating.layers().put(cast, now + definition.ticks("linger_seconds"));
        applySlow(mob, coating);
    }

    private static void tickCoatings(ServerLevel level, FieldState state, long now) {
        for (Iterator<Map.Entry<Mob, Coating>> iterator = state.coatings.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Mob mob = entry.getKey();
            Coating coating = entry.getValue();
            boolean changed = coating.layers().values().removeIf(end -> now >= end);
            if (!mob.isAlive() || mob.level() != level || coating.layers().isEmpty()) {
                AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) {
                    speed.removeModifier(HONEY_SLOW);
                }
                iterator.remove();
            } else {
                if (changed) {
                    applySlow(mob, coating);
                }
                if (now % 10L == 0L) {
                    level.sendParticles(AMBER, mob.getX(), mob.getY() + 0.08D, mob.getZ(),
                            3, 0.17D, 0.02D, 0.17D, 0.0D);
                    TakeVisuals.mote(level, Take.HONEY, mob.position().add(0.0D, 0.20D, 0.0D), Vec3.ZERO);
                }
            }
        }
    }

    private static void applySlow(Mob mob, Coating coating) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int layers = Math.min(coating.layers().size(), (int) coating.definition().parameter("max_layers"));
        double slow = Math.min(1.0D, layers * coating.definition().parameter("slow_per_layer"));
        AttributeModifier existing = speed.getModifier(HONEY_SLOW);
        if (existing == null || existing.getAmount() != -slow) {
            speed.removeModifier(HONEY_SLOW);
            speed.addTransientModifier(new AttributeModifier(HONEY_SLOW, "Madoka honey", -slow,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static boolean tickIllusion(ServerLevel level, Mob mob, Illusion illusion, long now) {
        Mob target = illusion.target();
        if (!isValidIllusion(level, mob, illusion, now)) {
            if (mob.isAlive() && mob.getTarget() == target) {
                mob.setTarget(null);
            }
            return true;
        }
        if (mob.getTarget() != target) {
            mob.setTarget(target);
        }
        if ((now + mob.getId()) % 8L == 0L) {
            TakeVisuals.illusion(level, mob, now);
        }
        if (mob instanceof Slime) {
            tickConfusedSlime(level, mob, illusion, now);
        }
        return false;
    }

    private static void tickConfusedSlime(ServerLevel level, Mob slime, Illusion illusion, long now) {
        Mob target = illusion.target();
        Vec3 toward = target.position().subtract(slime.position());
        double reach = (slime.getBbWidth() + target.getBbWidth()) * 0.5D + 0.55D;
        if (toward.lengthSqr() <= reach * reach && slime.hasLineOfSight(target)) {
            int interval = Math.max(1, illusion.definition().ticks("slime_strike_interval"));
            if ((now + slime.getId()) % interval == 0) {
                target.hurt(level.damageSources().mobAttack(slime),
                        (float) illusion.definition().parameter("slime_strike_damage"));
            }
        } else if (slime.onGround() && (now + slime.getId()) % 9L == 0L) {
            Vec3 step = toward.multiply(1, 0, 1).normalize().scale(0.30D);
            slime.setDeltaMovement(step.x, 0.34D, step.z);
        }
    }

    private static boolean isValidIllusion(ServerLevel level, Mob mob, Illusion illusion, long now) {
        Mob target = illusion.target();
        return now < illusion.end() && mob.isAlive() && target.isAlive()
                && mob.level() == level && target.level() == level && mob.canAttack(target);
    }

    private static boolean tickSeed(ServerLevel level, FieldState state, Seed seed, long now) {
        if (now >= seed.end || !seed.caster.isAlive() || seed.caster.level() != level) {
            return true;
        }
        Mob target = seed.target;
        boolean tracking = target != null && target.isAlive() && target.level() == level
                && now - seed.start >= seed.definition.ticks("drift_seconds");
        Vec3 next;
        if (tracking) {
            Vec3 delta = target.getBoundingBox().getCenter().subtract(seed.position);
            double speed = SpellCasting.range(seed.definition.parameter("tracking_speed"));
            next = seed.position.add(delta.lengthSqr() <= speed * speed ? delta : delta.normalize().scale(speed));
        } else {
            seed.velocity = seed.velocity.add(0.0D, -SpellCasting.range(seed.definition.parameter("drift_gravity")), 0.0D);
            next = seed.position.add(seed.velocity);
        }
        var block = level.clip(new ClipContext(seed.position, next, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, seed.caster));
        boolean hitBlock = block.getType() != HitResult.Type.MISS;
        boolean hitTarget = tracking && (target.getBoundingBox().contains(next)
                || target.getBoundingBox().clip(seed.position, next).isPresent());
        if (hitBlock || hitTarget) {
            Vec3 impact = hitBlock ? block.getLocation() : next;
            SpellDefinition honey = SpellRegistry.get(HONEY);
            if (honey != null) {
                double radius = SpellCasting.range(seed.definition.parameter("spread_radius"));
                TakeVisuals.honeyField(level, impact, radius, now, true);
                for (Mob mob : familiars(level, new AABB(impact, impact).inflate(radius))) {
                    if (mob.distanceToSqr(impact) <= radius * radius || !hitBlock && mob == target) {
                        coat(state, mob, seed.cast, honey, now);
                    }
                }
            }
            TakeVisuals.burst(level, impact, 10, 0.28D);
            return true;
        }
        seed.position = next;
        if (now % 2L == 0L) {
            TakeVisuals.mote(level, Take.SEED, next, Vec3.ZERO);
            level.sendParticles(AMBER, next.x, next.y, next.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return false;
    }

    private static boolean tickNectar(ServerLevel level, Nectar nectar, long now) {
        double progress = (now - nectar.start()) / 8.0D;
        ServerPlayer caster = nectar.caster();
        if (progress > 1.0D || !caster.isAlive() || caster.level() != level) {
            return true;
        }
        Vec3 point = nectar.origin().lerp(caster.getBoundingBox().getCenter(), progress);
        level.sendParticles(AMBER, point.x, point.y, point.z, 3, 0.06D, 0.06D, 0.06D, 0.0D);
        return false;
    }

    private static List<Mob> familiars(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(Mob.class, bounds, mob -> mob.isAlive() && Faction.isFamiliar(mob));
    }
}
