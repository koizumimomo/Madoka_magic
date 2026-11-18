package io.madoka.magic.entity;

import io.madoka.magic.compat.AnnegretCombat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class FrostingChessboardEntity extends Entity {
    public static final int TILES = 7;
    public static final int TILE_SIZE = 3;
    public static final double HALF_WIDTH = TILES * TILE_SIZE / 2.0;
    public static final int WARNING = 36;
    public static final int WAVE_TICKS = 46;
    public static final int WAVES = 2;

    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(FrostingChessboardEntity.class,
            EntityDataSerializers.LONG);
    private Mob witch;

    public FrostingChessboardEntity(EntityType<? extends FrostingChessboardEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static FrostingChessboardEntity spawn(ServerLevel level, Mob witch, BlockPos center) {
        var board = ModEntities.FROSTING_CHESSBOARD.get().create(level);
        if (board == null) return null;
        board.witch = witch;
        board.setPos(Vec3.atBottomCenterOf(center));
        board.entityData.set(START, level.getGameTime());
        return level.addFreshEntity(board) ? board : null;
    }

    public long age() {
        return Math.max(0, level().getGameTime() - entityData.get(START));
    }

    public static boolean dangerous(int wave, int column, int row) {
        return column >= 0 && row >= 0 && column < TILES && row < TILES
                && (wave == 0 ? column : row) != TILES / 2 && ((column + row) & 1) == wave;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        long age = age();
        if (witch == null || !witch.isAlive() || witch.isRemoved() || age >= WAVES * WAVE_TICKS) {
            discard();
            return;
        }
        if (age % WAVE_TICKS != WARNING) return;
        int wave = (int) (age / WAVE_TICKS);
        level.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.6F, 0.8F + wave * 0.2F);
        var area = new AABB(getX() - HALF_WIDTH, getY() - 1, getZ() - HALF_WIDTH,
                getX() + HALF_WIDTH, getY() + 2, getZ() + HALF_WIDTH);
        for (var player : level.getPlayers(candidate -> area.contains(candidate.position()) && AnnegretCombat.eligible(witch, candidate))) {
            int column = Mth.floor((player.getX() - getX() + HALF_WIDTH) / TILE_SIZE);
            int row = Mth.floor((player.getZ() - getZ() + HALF_WIDTH) / TILE_SIZE);
            if (dangerous(wave, column, row)) {
                player.hurt(damageSources().indirectMagic(this, witch), MadokaConfig.ANNEGRET_CHESS_DAMAGE.get().floatValue());
            }
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(HALF_WIDTH, 1, HALF_WIDTH);
    }

    @Override
    protected void defineSynchedData() { entityData.define(START, 0L); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
