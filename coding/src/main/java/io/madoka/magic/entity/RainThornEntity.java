package io.madoka.magic.entity;

import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class RainThornEntity extends Entity {
    private UUID witchId;
    private long castEnd;

    public RainThornEntity(EntityType<? extends RainThornEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    static boolean spawn(ServerLevel level, BlockPos pos, UUID witchId, long castEnd) {
        var node = ModEntities.RAIN_THORN.get().create(level);
        if (node == null) {
            return false;
        }
        node.witchId = witchId;
        node.castEnd = castEnd;
        node.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0, 0);
        return level.addFreshEntity(node);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel level) || isRemoved() || witchId == null
                || !source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof ServerPlayer player)
                || !(level.getEntity(witchId) instanceof MadokaWitchEntity witch)
                || !witch.combat().breakRainNode(player, castEnd)) {
            return false;
        }
        playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.9F, 1.15F);
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 0.55D, getZ(), 12, 0.30D, 0.36D, 0.30D, 0.025D);
        discard();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && (witchId == null || level.getGameTime() >= castEnd
                || tickCount > 10 && (!(level.getEntity(witchId) instanceof MadokaWitchEntity witch)
                || !witch.combat().hasRainNode(castEnd)))) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        witchId = tag.hasUUID("Witch") ? tag.getUUID("Witch") : null;
        castEnd = tag.getLong("CastEnd");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (witchId != null) {
            tag.putUUID("Witch", witchId);
        }
        tag.putLong("CastEnd", castEnd);
    }
}
