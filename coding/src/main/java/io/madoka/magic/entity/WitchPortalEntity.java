package io.madoka.magic.entity;

import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.dimension.BarrierFloor;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.event.PortalEvents;
import io.madoka.magic.faction.WitchFamiliars;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.Difficulty;

import java.util.List;
import java.util.UUID;

/**
 * 魔女结界传送门，触碰传送。
 *
 * <p>{@code kind=entrance}：主世界 → 魔女维度（进结界）；{@code kind=exit}：魔女维度 → 主世界。
 * 返回点由 {@link MadokaSavedData} 统一记账；所有传送都<b>显式给定目标 ServerLevel 与坐标</b>，
 * 绝不依赖默认维度推断。</p>
 */
public class WitchPortalEntity extends Entity {

    public static final String KIND_ENTRANCE = "entrance";
    public static final String KIND_EXIT = "exit";

    private static final String TAG_KIND = "kind";
    private static final String TAG_THEME = "theme";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_LIFE = "life";

    private static final EntityDataAccessor<String> DATA_KIND =
            SynchedEntityData.defineId(WitchPortalEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_THEME =
            SynchedEntityData.defineId(WitchPortalEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(WitchPortalEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(WitchPortalEntity.class, EntityDataSerializers.INT);

    private static final String TELEPORT_COOLDOWN = "madoka_portal_cooldown";
    private static final long COOLDOWN_TICKS = 20L;
    private static final int EXIT_ARM_TICKS = 20;
    private UUID encounterId;

    public WitchPortalEntity(EntityType<? extends WitchPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    /** 生成一个传送门。owner 可空（出口门的魔女主人无法解析时）。 */
    public static WitchPortalEntity spawn(ServerLevel level, String kind, String themeId, UUID owner, BlockPos pos) {
        return spawn(level, kind, themeId, owner, Vec3.atBottomCenterOf(pos));
    }

    public static WitchPortalEntity spawn(ServerLevel level, String kind, String themeId, UUID owner, Vec3 pos) {
        if (!MadokaDimensions.hasBarrier(themeId)) {
            return null;
        }
        WitchPortalEntity portal = ModEntities.WITCH_PORTAL.get().create(level);
        if (portal == null) {
            return null;
        }
        portal.setKind(kind);
        portal.setThemeId(themeId);
        portal.setOwner(owner == null ? "" : owner.toString());
        portal.setLife(MadokaConfig.PORTAL_LIFETIME_TICKS.get());
        portal.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        return level.addFreshEntity(portal) ? portal : null;
    }

    public UUID getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(UUID encounterId) {
        this.encounterId = encounterId;
    }

    // ---------------------------------------------------------------- 数据

    public String getKind() {
        return this.entityData.get(DATA_KIND);
    }

    public void setKind(String kind) {
        this.entityData.set(DATA_KIND, KIND_EXIT.equals(kind) ? KIND_EXIT : KIND_ENTRANCE);
    }

    public boolean isEntrance() {
        return KIND_ENTRANCE.equals(getKind());
    }

    public String getThemeId() {
        return this.entityData.get(DATA_THEME);
    }

    public void setThemeId(String themeId) {
        this.entityData.set(DATA_THEME, themeId == null || themeId.isEmpty() ? "sacrifice" : themeId);
    }

    public String getOwner() {
        return this.entityData.get(DATA_OWNER);
    }

    public void setOwner(String owner) {
        this.entityData.set(DATA_OWNER, owner == null ? "" : owner);
    }

    public UUID getOwnerUUID() {
        String owner = getOwner();
        if (owner.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int getLife() {
        return this.entityData.get(DATA_LIFE);
    }

    public void setLife(int life) {
        this.entityData.set(DATA_LIFE, life);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_KIND, KIND_ENTRANCE);
        this.entityData.define(DATA_THEME, "sacrifice");
        this.entityData.define(DATA_OWNER, "");
        this.entityData.define(DATA_LIFE, 2400);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setKind(tag.getString(TAG_KIND));
        setThemeId(tag.getString(TAG_THEME));
        setOwner(tag.getString(TAG_OWNER));
        setLife(tag.contains(TAG_LIFE) ? tag.getInt(TAG_LIFE) : MadokaConfig.PORTAL_LIFETIME_TICKS.get());
        encounterId = tag.hasUUID("encounter") ? tag.getUUID("encounter") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(TAG_KIND, getKind());
        tag.putString(TAG_THEME, getThemeId());
        tag.putString(TAG_OWNER, getOwner());
        tag.putInt(TAG_LIFE, getLife());
        if (encounterId != null) {
            tag.putUUID("encounter", encounterId);
        }
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (!MadokaDimensions.hasBarrier(getThemeId())) {
            discard();
            return;
        }
        if (encounterId != null) {
            var encounter = MadokaSavedData.get((ServerLevel) level()).getEncounter(encounterId);
            if (encounter == null || isEntrance() == encounter.defeated()) {
                discard();
                return;
            }
        } else {
            int life = getLife() - 1;
            setLife(life);
            if (life <= 0) {
                discard();
                return;
            }
        }
        if (!isEntrance() && tickCount < EXIT_ARM_TICKS) {
            return;
        }
        if (this.tickCount % 5 == 0) {
            double range = MadokaConfig.PORTAL_TRIGGER_RANGE.get();
            List<ServerPlayer> players = level().getEntitiesOfClass(ServerPlayer.class,
                    getBoundingBox().inflate(range), player -> player.isAlive() && !player.isSpectator());
            for (ServerPlayer player : players) {
                tryTeleport(player);
                if (isRemoved()) {
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- 传送

    private void tryTeleport(ServerPlayer player) {
        long now = player.serverLevel().getServer().overworld().getGameTime();
        var state = player.getPersistentData();
        if (now < state.getLong(TELEPORT_COOLDOWN)) {
            return;
        }
        state.putLong(TELEPORT_COOLDOWN, now + COOLDOWN_TICKS);
        if (isEntrance()) {
            teleportEnter(player);
        } else {
            teleportExit(player);
        }
    }

    /** 入口：记下返回点 → 传送到该主题的魔女维度 → 确保玩家的魔女在场。 */
    private void teleportEnter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel target = server.getLevel(MadokaDimensions.witchLevel(getThemeId()));
        if (target == null || target.getDifficulty() == Difficulty.PEACEFUL
                || !MadokaDimensions.barrierTheme(player.level().dimension()).isEmpty()) {
            return;
        }
        var saved = MadokaSavedData.get(target);
        if (encounterId == null) {
            UUID id = getUUID();
            if (saved.getEncounter(id) == null) {
                var created = saved.createEncounter(id, getThemeId(), getOwnerUUID(), level().dimension().location());
                if (!spawnEncounter(target, created, id)) {
                    saved.removeEncounter(id);
                    return;
                }
            }
            encounterId = id;
        }
        var encounter = saved.getEncounter(encounterId);
        if (encounter == null || encounter.defeated()) {
            return;
        }
        var previousDim = player.level().dimension().location();
        Vec3 previousPos = player.position();
        BlockPos start = encounter.arena().offset(0, 0, -16);
        target.getChunkAt(start);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.getX(), start.getZ());
        player.teleportTo(target, start.getX() + 0.5D, y, start.getZ() + 0.5D, 0.0F, 0.0F);
        if (player.level() == target) {
            saved.recordEnter(player.getUUID(), previousDim, previousPos, encounterId);
        }
    }

    /** 出口：回主世界的存档返回点；没有记录时回退重生点 / 世界出生点。 */
    private void teleportExit(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel source = (ServerLevel) level();
        MadokaSavedData saved = MadokaSavedData.get(source);
        MadokaSavedData.Entry entry = saved.getEnter(player.getUUID());
        if (encounterId != null && (entry == null || !encounterId.equals(entry.getEncounter()))) {
            return;
        }

        Vec3 target = entry != null ? entry.getReturnPosition() : null;
        ServerLevel destination = entry != null && entry.getPrevDim() != null
                ? server.getLevel(ResourceKey.create(Registries.DIMENSION, entry.getPrevDim())) : null;
        if (destination == null || target == null) {
            destination = server.overworld();
            target = Vec3.atBottomCenterOf(destination.getSharedSpawnPos());
        }
        player.teleportTo(destination, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        if (player.level() == destination) {
            PortalEvents.leave(source, player);
            if (encounterId == null ? source.players().isEmpty() : !saved.hasVisitors(encounterId)) {
                discard();
            }
        }
    }

    private boolean spawnEncounter(ServerLevel dimension, MadokaSavedData.Encounter encounter, UUID id) {
        BlockPos anchor = encounter.arena();
        BarrierFloor.refreshLegacyArena(dimension, anchor, getThemeId());
        dimension.getChunkAt(anchor);
        boolean compat = CataclysmCompat.isBarrierTheme(getThemeId());
        boolean caves = io.madoka.magic.compat.AlexCavesCompat.isBarrierTheme(getThemeId());
        int surfaceY = dimension.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX(), anchor.getZ());
        if (compat) {
            CataclysmCompat.prepareArena(dimension, anchor.offset(0, 0, -16), surfaceY, getThemeId());
        }
        int y = compat ? CataclysmCompat.bossSpawnY(dimension, anchor, surfaceY, getThemeId()) : surfaceY;
        BlockPos near = anchor.atY(y);
        Mob boss;
        if (compat) {
            boss = CataclysmCompat.createBoss(dimension, getThemeId());
        } else if (caves) {
            boss = io.madoka.magic.compat.AlexCavesCompat.createBoss(dimension, getThemeId());
        } else if (WitchTypes.WITHER.equals(getThemeId())) {
            boss = EntityType.WITHER.create(dimension);
        } else if (WitchTypes.GLUTTONY.equals(getThemeId())) {
            boss = ModEntities.GLUTTONY_WITCH.get().create(dimension);
        } else {
            MadokaWitchEntity witch = ModEntities.WITCH.get().create(dimension);
            if (witch == null) {
                return false;
            }
            UUID owner = encounter.owner();
            if (owner != null) {
                var online = dimension.getServer().getPlayerList().getPlayer(owner);
                witch.setOwnerName(online == null ? "" : online.getGameProfile().getName());
                witch.setOwnerId(owner);
            }
            witch.setThemeId(getThemeId());
            boss = witch;
        }
        if (boss == null) {
            return false;
        }
        boss.moveTo(near.getX() + 0.5D, near.getY(), near.getZ() + 0.5D, 0.0F, 0.0F);
        boss.finalizeSpawn(dimension, dimension.getCurrentDifficultyAt(near), MobSpawnType.EVENT, null, null);
        boss.setPersistenceRequired();
        boss.getPersistentData().putUUID(WitchFamiliars.ENCOUNTER_TAG, id);
        if (!dimension.addFreshEntity(boss)) {
            return false;
        }
        if (compat) {
            CataclysmCompat.spawnFamiliars(dimension, anchor, y, getThemeId(), id);
        } else if (caves) {
            io.madoka.magic.compat.AlexCavesCompat.spawnFamiliars(dimension, anchor, getThemeId(), id);
        } else {
            WitchFamiliars.spawn(dimension, near, getThemeId(), id);
        }
        return true;
    }

}
