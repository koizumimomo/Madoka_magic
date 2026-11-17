package io.madoka.magic.event;

import com.mojang.authlib.GameProfile;
import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchPortalEntity;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.faction.WitchFamiliars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * 魔女结界传送门：主世界随机刷出入口、魔女死亡留出口。
 *
 * <p>入口的存活状态以 {@link MadokaSavedData} 的 entrance 记录为准，
 * 随机刷新前先看记录、再核实实体，避免同一玩家叠门。</p>
 */
public class PortalEvents {

    /**
     * 魔女死在专属维度 → 原地生成出口门，并回收该玩家的主世界入口门。
     *
     * <p>玩家若还在维度里不管 —— 出口门等她来碰。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWitchDeath(LivingDeathEvent event) {
        var witch = event.getEntity();
        String theme = WitchTypes.bossTheme(witch);
        if (theme.isEmpty()) {
            theme = CataclysmCompat.barrierTheme(witch);
        }
        if (!(witch.level() instanceof ServerLevel level) || !MadokaDimensions.hasBarrier(theme)
                || !level.dimension().equals(MadokaDimensions.witchLevel(theme))) {
            return;
        }

        var state = witch.getPersistentData();
        var saved = MadokaSavedData.get(level);
        if (state.hasUUID(WitchFamiliars.ENCOUNTER_TAG)) {
            UUID id = state.getUUID(WitchFamiliars.ENCOUNTER_TAG);
            var run = saved.getEncounter(id);
            if (run == null || run.defeated()) {
                return;
            }
            BlockPos exit = exitPosition(level, witch.blockPosition());
            saved.defeatEncounter(id, exit);
            closeEntrance(level.getServer(), saved, run.owner(), id);
            if (saved.hasVisitors(id)) {
                var portal = WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_EXIT, theme, run.owner(), exit);
                if (portal != null) {
                    portal.setEncounterId(id);
                }
            } else {
                saved.removeEncounter(id);
            }
            return;
        }

        MinecraftServer server = level.getServer();
        UUID owner = witch instanceof MadokaWitchEntity named
                ? named.getOwnerId() != null ? named.getOwnerId() : resolveOwner(server, named.getOwnerName())
                : null;
        WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_EXIT, theme, owner, exitPosition(level, witch.blockPosition()));
        closeEntrance(server, saved, owner, null);
    }

    private static BlockPos exitPosition(ServerLevel level, BlockPos death) {
        int x = death.getX() + 4;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, death.getZ()), death.getZ());
    }

    private static void closeEntrance(MinecraftServer server, MadokaSavedData saved, UUID owner, UUID id) {
        if (owner == null) {
            if (id != null) {
                var run = saved.getEncounter(id);
                var source = server.getLevel(ResourceKey.create(Registries.DIMENSION, run.entranceDim()));
                if (source != null && source.getEntity(id) instanceof WitchPortalEntity portal) {
                    portal.discard();
                }
            }
            return;
        }
        MadokaSavedData.Entry entry = saved.getEnter(owner);
        if (entry == null || entry.getEntrancePos() == null || entry.getEntranceDim() == null) {
            return;
        }
        ServerLevel entranceLevel = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, entry.getEntranceDim()));
        if (entranceLevel != null && entranceLevel.hasChunkAt(entry.getEntrancePos())) {
            boolean removed = false;
            for (WitchPortalEntity portal : entranceLevel.getEntitiesOfClass(WitchPortalEntity.class,
                    new AABB(entry.getEntrancePos()).inflate(4.0D),
                    p -> p.isEntrance() && owner.equals(p.getOwnerUUID()) && (id == null || id.equals(p.getUUID())))) {
                portal.discard();
                removed = true;
            }
            if (removed || id == null) {
                saved.clearEntrance(owner);
            }
        }
    }

    public static void leave(ServerLevel source, ServerPlayer player) {
        var saved = MadokaSavedData.get(source);
        var entry = saved.getEnter(player.getUUID());
        if (entry == null || entry.getReturnPosition() == null) {
            return;
        }
        UUID id = entry.getEncounter();
        var run = id == null ? null : saved.getEncounter(id);
        if (run != null && !source.dimension().equals(MadokaDimensions.witchLevel(run.theme()))) {
            return;
        }
        saved.clearEnter(player.getUUID());
        if (run != null && run.defeated() && !saved.hasVisitors(id)) {
            saved.removeEncounter(id);
            var area = new AABB(run.exit()).inflate(4.0D);
            source.getEntitiesOfClass(WitchPortalEntity.class, area,
                    portal -> !portal.isEntrance() && id.equals(portal.getEncounterId())).forEach(WitchPortalEntity::discard);
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !MadokaDimensions.barrierTheme(event.getFrom()).isEmpty()) {
            var source = player.serverLevel().getServer().getLevel(event.getFrom());
            if (source != null) {
                leave(source, player);
            }
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var current = player.serverLevel();
        var saved = MadokaSavedData.get(current);
        var entry = saved.getEnter(player.getUUID());
        var run = entry == null || entry.getEncounter() == null ? null : saved.getEncounter(entry.getEncounter());
        if (run != null && !current.dimension().equals(MadokaDimensions.witchLevel(run.theme()))) {
            var previous = current.getServer().getLevel(MadokaDimensions.witchLevel(run.theme()));
            if (previous != null) {
                leave(previous, player);
            }
        }
    }

    /** 主世界随机刷入口：每个签约玩家按概率掷点，命中且没有存活入口才刷。 */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int interval = MadokaConfig.PORTAL_CHECK_INTERVAL.get();
        if (interval <= 0 || server.getTickCount() % interval != 0) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        double chance = MadokaConfig.PORTAL_SPAWN_CHANCE.get();
        for (ServerPlayer player : overworld.players()) {
            PlayerMadokaData data = PlayerMadokaData.of(player);
            if (!data.isContracted()) {
                continue;
            }
            if (overworld.random.nextDouble() >= chance) {
                continue;
            }
            if (hasLivingEntrance(overworld, player.getUUID())) {
                continue;
            }
            var themes = MadokaDimensions.NATURAL_BARRIERS;
            spawnEntrance(overworld, player, themes.get(overworld.random.nextInt(themes.size())));
        }
    }

    /**
     * 该玩家是否已有存活入口。
     * <p>记录存在但区块未加载时按「存活」处理（宁可不刷，也别在卸载区块里叠门）。</p>
     */
    private static boolean hasLivingEntrance(ServerLevel overworld, UUID uuid) {
        MadokaSavedData saved = MadokaSavedData.get(overworld);
        MadokaSavedData.Entry entry = saved.getEnter(uuid);
        if (entry == null || entry.getEntrancePos() == null || entry.getEntranceDim() == null) {
            return false;
        }
        ServerLevel dim = overworld.getServer().getLevel(
                ResourceKey.create(Registries.DIMENSION, entry.getEntranceDim()));
        if (dim == null) {
            saved.clearEntrance(uuid);
            return false;
        }
        if (!dim.hasChunkAt(entry.getEntrancePos())) {
            return true;
        }
        boolean alive = !dim.getEntitiesOfClass(WitchPortalEntity.class,
                new AABB(entry.getEntrancePos()).inflate(4.0D),
                p -> p.isEntrance() && uuid.equals(p.getOwnerUUID())).isEmpty();
        if (!alive) {
            saved.clearEntrance(uuid);
        }
        return alive;
    }

    /** 在玩家 8–16 格的随机位置找安全地面（高度图）生成入口，并记账。 */
    private static void spawnEntrance(ServerLevel overworld, ServerPlayer player, String themeId) {
        BlockPos pos = randomEntrancePos(overworld, player);
        WitchPortalEntity portal = WitchPortalEntity.spawn(overworld, WitchPortalEntity.KIND_ENTRANCE,
                themeId, player.getUUID(), pos);
        if (portal != null) {
            MadokaSavedData.get(overworld).setEntrance(player.getUUID(),
                    overworld.dimension().location(), pos);
        }
    }

    /** 玩家 8–16 格外的随机地面位置（测试工具开门也用它，避免开在脚下）。 */
    private static BlockPos randomEntrancePos(ServerLevel level, ServerPlayer player) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 8.0D + level.random.nextDouble() * 8.0D;
        int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, y, z);
    }

    /**
     * 测试工具入口：在玩家 8–16 格外的随机地面开门。
     *
     * <p>语义同 {@link #openFixedEntrance}（回收旧入口、记账、音效），
     * 只是位置不再取玩家脚下。</p>
     */
    public static boolean openNearbyEntrance(ServerLevel level, ServerPlayer player, String themeId) {
        return openFixedEntrance(level, player, randomEntrancePos(level, player), themeId);
    }

    /**
     * 在固定位置（灾变祭坛/刷怪笼上方）开一道入口门。
     *
     * <p>灾变入口不走随机刷新、也不消耗祭品：右键原召唤方块即开门，
     * 开门前先回收该玩家之前的入口，避免叠门。</p>
     */
    public static boolean openFixedEntrance(ServerLevel level, ServerPlayer player, BlockPos pos, String themeId) {
        if (!MadokaDimensions.hasBarrier(themeId)) {
            return false;
        }
        var saved = MadokaSavedData.get(level);
        closeEntrance(level.getServer(), saved, player.getUUID(), null);
        boolean existing = !level.getEntitiesOfClass(WitchPortalEntity.class,
                new AABB(pos).inflate(4.0D),
                portal -> portal.isEntrance() && player.getUUID().equals(portal.getOwnerUUID())).isEmpty();
        if (existing) {
            return true;
        }
        WitchPortalEntity portal = WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_ENTRANCE,
                themeId, player.getUUID(), pos);
        if (portal == null) {
            return false;
        }
        saved.setEntrance(player.getUUID(), level.dimension().location(), pos);
        level.playSound(null, pos, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 1.4F);
        return true;
    }

    /** 魔女只存了主人名字：优先在线玩家，离线走 profile cache 解析 UUID。 */
    private static UUID resolveOwner(MinecraftServer server, String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(ownerName);
        if (online != null) {
            return online.getUUID();
        }
        Optional<GameProfile> profile = server.getProfileCache().get(ownerName);
        return profile.map(GameProfile::getId).orElse(null);
    }
}
