package io.madoka.magic.entity;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class ArenaCocoonEntity extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> BEE = SynchedEntityData.defineId(ArenaCocoonEntity.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private UUID witchId;
    private long castId;
    private int sector;
    private float health;
    private long lastHit = -1;

    public ArenaCocoonEntity(EntityType<? extends ArenaCocoonEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    static boolean spawn(ServerLevel level, BlockPos pos, MadokaWitchEntity witch, long castId, int sector, boolean bee) {
        var node = ModEntities.ARENA_COCOON.get().create(level);
        if (node == null) {
            return false;
        }
        node.witchId = witch.getUUID();
        node.castId = castId;
        node.sector = sector;
        node.health = MadokaConfig.WITCH_ARENA_COCOON_HEALTH.get().floatValue();
        node.entityData.set(BEE, bee);
        node.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        return level.addFreshEntity(node);
    }

    public boolean isBee() {
        return entityData.get(BEE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || amount <= 0 || witchId == null
                || !(source.getEntity() instanceof ServerPlayer player) || lastHit == level.getGameTime()
                || !(level.getEntity(witchId) instanceof MadokaWitchEntity witch)
                || !witch.combat().allowsVisitor(player) || !witch.combat().arena().hasNode(castId, sector)) {
            return false;
        }
        lastHit = level.getGameTime();
        health -= amount;
        if (health > 0) {
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 0.95F);
            level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.6D, getZ(), 5, 0.2D, 0.25D, 0.2D, 0.04D);
        } else {
            witch.combat().arena().breakNode(castId, sector);
            witch.markAssistedBy(player);
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.3F);
            level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 0.6D, getZ(), 16, 0.3D, 0.4D, 0.3D, 0.07D);
            discard();
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > 10 && level() instanceof ServerLevel level
                && (witchId == null || !(level.getEntity(witchId) instanceof MadokaWitchEntity witch)
                || !witch.combat().arena().hasNode(castId, sector))) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(BEE, false);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        witchId = tag.hasUUID("Witch") ? tag.getUUID("Witch") : null;
        castId = tag.getLong("Cast");
        sector = tag.getInt("Sector");
        health = tag.getFloat("Health");
        entityData.set(BEE, tag.getBoolean("Bee"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (witchId != null) {
            tag.putUUID("Witch", witchId);
        }
        tag.putLong("Cast", castId);
        tag.putInt("Sector", sector);
        tag.putFloat("Health", health);
        tag.putBoolean("Bee", isBee());
    }
}
