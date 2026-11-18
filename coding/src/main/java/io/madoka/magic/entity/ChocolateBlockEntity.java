package io.madoka.magic.entity;

import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.compat.AnnegretCombat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class ChocolateBlockEntity extends Entity {
    public static final int FUSE_TICKS = 110;
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ChocolateBlockEntity.class,
            EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> FEEDING = SynchedEntityData.defineId(ChocolateBlockEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BLAST_RADIUS = SynchedEntityData.defineId(ChocolateBlockEntity.class,
            EntityDataSerializers.FLOAT);

    private Mob witch;
    private float health;
    private boolean broken;
    private Vec3 lerpDestination;
    private int lerpSteps;

    public ChocolateBlockEntity(EntityType<? extends ChocolateBlockEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static ChocolateBlockEntity spawn(ServerLevel level, Mob witch, Vec3 floor) {
        var block = ModEntities.ANNEGRET_CHOCOLATE.get().create(level);
        if (block == null) return null;
        block.witch = witch;
        block.health = MadokaConfig.ANNEGRET_CHOCOLATE_HEALTH.get().floatValue();
        block.entityData.set(START, level.getGameTime());
        block.entityData.set(BLAST_RADIUS, MadokaConfig.ANNEGRET_CHOCOLATE_RADIUS.get().floatValue());
        block.setPos(floor.add(0, 0.18, 0));
        return level.addFreshEntity(block) ? block : null;
    }

    public boolean feeding() { return entityData.get(FEEDING); }
    public float blastRadius() { return entityData.get(BLAST_RADIUS); }
    public boolean wasBroken() { return broken; }
    public long age() { return Math.max(0, level().getGameTime() - entityData.get(START)); }

    public void floatToWitch() { entityData.set(FEEDING, true); }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || amount <= 0 || witch == null
                || !(source.getEntity() instanceof ServerPlayer player) || !AnnegretCombat.eligible(witch, player)) {
            return false;
        }
        health -= amount;
        if (health <= 0) {
            broken = true;
            crumbs(level, getX(), getY() + 0.45, getZ(), 24);
            AlexCavesCompat.block("block_of_chocolate").ifPresent(block ->
                    playSound(block.defaultBlockState().getSoundType().getBreakSound(), 1.2F, 0.95F));
            discard();
        } else {
            crumbs(level, getX(), getY() + 0.45, getZ(), 7);
        }
        return true;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpDestination = new Vec3(x, y, z);
        lerpSteps = steps;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            if (lerpSteps > 0) setPos(position().lerp(lerpDestination, 1.0 / lerpSteps--));
            return;
        }
        if (witch == null || !witch.isAlive() || witch.isRemoved()) {
            discard();
            return;
        }
        if (!feeding()) {
            if (age() >= FUSE_TICKS) burst(level);
            return;
        }
        Vec3 mouth = witch.getEyePosition().subtract(0, 0.2, 0);
        Vec3 direction = mouth.subtract(position().add(0, 0.5, 0));
        if (direction.lengthSqr() > 0.65 * 0.65 && age() < FUSE_TICKS + 40) {
            setPos(position().add(direction.normalize().scale(0.25)));
        } else if (direction.lengthSqr() <= 0.65 * 0.65) {
            crumbs(level, mouth.x, mouth.y, mouth.z, 18);
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.3F, 0.94F);
            witch.heal(witch.getMaxHealth() * MadokaConfig.ANNEGRET_CHOCOLATE_HEAL.get().floatValue());
            discard();
        } else {
            discard();
        }
    }

    private void burst(ServerLevel level) {
        float radius = blastRadius();
        var area = new AABB(getX() - radius, getY() - 1, getZ() - radius,
                getX() + radius, getY() + 2.5, getZ() + radius);
        for (var player : level.getPlayers(candidate -> area.contains(candidate.position()) && AnnegretCombat.eligible(witch, candidate))) {
            double dx = player.getX() - getX(), dz = player.getZ() - getZ();
            if (dx * dx + dz * dz <= radius * radius) {
                player.hurt(damageSources().indirectMagic(this, witch), MadokaConfig.ANNEGRET_CHOCOLATE_DAMAGE.get().floatValue());
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.45, getZ(), 1, 0, 0, 0, 0);
        crumbs(level, getX(), getY() + 0.45, getZ(), 55);
        playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 1.12F);
        discard();
    }

    private static void crumbs(ServerLevel level, double x, double y, double z, int count) {
        AlexCavesCompat.block("block_of_chocolate").ifPresent(block -> level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()), x, y, z, count, 0.30, 0.28, 0.30, 0.14));
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(START, 0L);
        entityData.define(FEEDING, false);
        entityData.define(BLAST_RADIUS, 0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
