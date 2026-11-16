package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WalpurgisStorm {
    private static final String DATA_NAME = MadokaMagic.MODID + "_walpurgis_storm";
    private static final int CHECK_INTERVAL = 20;
    private static final int STORM_DURATION = 200;
    private static final int SPAWN_RETRY = 1200;
    private static final int MISSING_GRACE = 60;
    private static final int SPAWN_TRIES = 12;

    private WalpurgisStorm() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof MadokaWitchEntity witch) || !witch.isWalpurgisNight()) {
            return;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            event.setCanceled(true);
            return;
        }
        var state = state(level);
        if (state.boss != null) {
            if (!state.boss.equals(witch.getUUID())) {
                event.setCanceled(true);
            }
            return;
        }
        if (witch.isAlive()) {
            state.boss = witch.getUUID();
            state.position = witch.blockPosition();
            state.weather = Weather.capture(weather(level));
            state.announced = event.loadedFromDisk();
            state.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }
        long now = level.getGameTime();
        if (now % CHECK_INTERVAL != 0) {
            return;
        }
        var state = state(level);
        if (state.boss != null) {
            tickStorm(level, state, now);
        } else {
            tickNaturalSpawn(level, state, now);
        }
    }

    private static void tickStorm(ServerLevel level, State state, long now) {
        if (level.getEntity(state.boss) instanceof MadokaWitchEntity witch) {
            if (!witch.isAlive() || !witch.isWalpurgisNight()) {
                endStorm(level, state, now);
                return;
            }
            state.missingTicks = 0;
            if (!witch.blockPosition().equals(state.position)) {
                state.position = witch.blockPosition();
                state.setDirty();
            }
            if (!state.announced) {
                announce(level);
                state.announced = true;
                state.setDirty();
            }
        } else if (level.isPositionEntityTicking(state.position) || !state.announced) {
            state.missingTicks += CHECK_INTERVAL;
            if (state.missingTicks >= MISSING_GRACE) {
                endStorm(level, state, now);
                return;
            }
        } else {
            // An unloaded encounter resumes with its saved boss.
            state.missingTicks = 0;
        }
        if (state.announced) {
            var data = weather(level);
            if (!data.isRaining() || !data.isThundering() || data.getClearWeatherTime() > 0
                    || data.getRainTime() <= CHECK_INTERVAL || data.getThunderTime() <= CHECK_INTERVAL) {
                level.setWeatherParameters(0, STORM_DURATION, true, true);
            }
        }
    }

    private static void announce(ServerLevel level) {
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 0.8F);
        }
        MadokaNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new MadokaNetwork.NightArrival());
    }

    private static void endStorm(ServerLevel level, State state, long now) {
        state.weather.restore(weather(level));
        state.boss = null;
        state.weather = null;
        state.announced = false;
        state.missingTicks = 0;
        state.spawnInterval = MadokaConfig.NIGHT_SPAWN_INTERVAL.get();
        state.nextSpawn = now + state.spawnInterval;
        state.setDirty();
    }

    private static void tickNaturalSpawn(ServerLevel level, State state, long now) {
        int interval = MadokaConfig.NIGHT_SPAWN_INTERVAL.get();
        if (state.spawnInterval != interval) {
            state.spawnInterval = interval;
            state.nextSpawn = now + interval;
            state.setDirty();
        }
        if (interval == 0) {
            return;
        }
        if (now < state.nextSpawn || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        var targets = level.players().stream().filter(player -> player.isAlive() && !player.isSpectator()
                && !player.getAbilities().invulnerable && FamiliarVisibility.canWitchTarget(player)).toList();
        if (targets.isEmpty()) {
            return;
        }
        state.nextSpawn = now + SPAWN_RETRY;
        state.setDirty();
        int first = level.random.nextInt(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            if (spawnNear(level, targets.get((first + i) % targets.size()))) {
                return;
            }
        }
    }

    private static boolean spawnNear(ServerLevel level, ServerPlayer player) {
        var witch = ModEntities.WITCH.get().create(level);
        if (witch == null) {
            return false;
        }
        witch.setThemeId(WitchTypes.NIGHT);
        int halfWidth = (int) Math.ceil(witch.getBbWidth() * 0.5D);
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = 34 + level.random.nextDouble() * 15;
            int x = (int) Math.floor(player.getX() + distance * Math.cos(angle));
            int z = (int) Math.floor(player.getZ() + distance * Math.sin(angle));
            var column = new BlockPos(x, player.getBlockY(), z);
            var low = column.offset(-halfWidth, 0, -halfWidth);
            var high = column.offset(halfWidth, 0, halfWidth);
            if (!level.getWorldBorder().isWithinBounds(low) || !level.getWorldBorder().isWithinBounds(high)
                    || !level.hasChunksAt(low, high) || !level.isPositionEntityTicking(column)) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(ground - player.getY()) > 20 || !level.getFluidState(new BlockPos(x, ground - 1, z)).isEmpty()) {
                continue;
            }
            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                    ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
                }
            }
            Vec3 position = new Vec3(x + 0.5D, ground + 1.0D, z + 0.5D);
            if (position.y + witch.getBbHeight() > level.getMaxBuildHeight()
                    || position.distanceToSqr(player.position()) > 58 * 58) {
                continue;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(position.x - player.getX(), player.getZ() - position.z));
            witch.moveTo(position.x, position.y, position.z, yaw, 0);
            if (level.noCollision(witch, witch.getBoundingBox()) && level.addFreshEntity(witch)) {
                return true;
            }
        }
        return false;
    }

    private static ServerLevelData weather(ServerLevel level) {
        return level.getServer().getWorldData().overworldData();
    }

    private static State state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(State::load, State::new, DATA_NAME);
    }

    private record Weather(int clearTime, int rainTime, int thunderTime, boolean raining, boolean thundering) {
        static Weather capture(ServerLevelData data) {
            return new Weather(data.getClearWeatherTime(), data.getRainTime(), data.getThunderTime(),
                    data.isRaining(), data.isThundering());
        }

        void restore(ServerLevelData data) {
            data.setClearWeatherTime(clearTime);
            data.setRainTime(rainTime);
            data.setThunderTime(thunderTime);
            data.setRaining(raining);
            data.setThundering(thundering);
        }
    }

    private static final class State extends SavedData {
        private UUID boss;
        private BlockPos position;
        private Weather weather;
        private boolean announced;
        private int missingTicks;
        private int spawnInterval;
        private long nextSpawn;

        static State load(CompoundTag tag) {
            var state = new State();
            state.spawnInterval = tag.getInt("SpawnInterval");
            state.nextSpawn = tag.getLong("NextSpawn");
            if (tag.hasUUID("Boss")) {
                state.boss = tag.getUUID("Boss");
                state.position = BlockPos.of(tag.getLong("Position"));
                state.announced = tag.getBoolean("Announced");
                state.weather = new Weather(tag.getInt("ClearTime"), tag.getInt("RainTime"), tag.getInt("ThunderTime"),
                        tag.getBoolean("Raining"), tag.getBoolean("Thundering"));
            }
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong("NextSpawn", nextSpawn);
            tag.putInt("SpawnInterval", spawnInterval);
            if (boss != null) {
                tag.putUUID("Boss", boss);
                tag.putLong("Position", position.asLong());
                tag.putBoolean("Announced", announced);
                tag.putInt("ClearTime", weather.clearTime());
                tag.putInt("RainTime", weather.rainTime());
                tag.putInt("ThunderTime", weather.thunderTime());
                tag.putBoolean("Raining", weather.raining());
                tag.putBoolean("Thundering", weather.thundering());
            }
            return tag;
        }
    }
}
