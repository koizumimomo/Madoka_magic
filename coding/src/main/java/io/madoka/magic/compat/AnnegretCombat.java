package io.madoka.magic.compat;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.entity.ChocolateBlockEntity;
import io.madoka.magic.entity.FrostingChessboardEntity;
import io.madoka.magic.faction.FamiliarVisibility;
import io.madoka.magic.faction.WitchFamiliars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Higher-priority casts for Annegret, interleaved with the original Licowitch attack goal. */
public final class AnnegretCombat extends Goal {
    private static final String RITUAL_USED = "madoka_annegret_chocolate_used";
    private static final double CAST_RANGE = 24;
    private static final double ACTIVE_RANGE = 40;
    private static final int FEED_DELAY = 64;
    private static final int CHOCOLATE_COUNT = 3;
    private static final int INITIAL_REST = 100;
    private static final DustParticleOptions WARD_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.52F, 0.70F), 0.9F);

    private final Mob witch;
    private final List<ChocolateBlockEntity> chocolate = new ArrayList<>(CHOCOLATE_COUNT);
    private FrostingChessboardEntity chessboard;
    private long nextChessboard;
    private long feedAt;
    private long retryAt;
    private boolean ritual;
    private boolean feedStarted;

    AnnegretCombat(Mob witch) {
        this.witch = witch;
        nextChessboard = witch.level().getGameTime() + INITIAL_REST;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static boolean eligible(Mob witch, ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && FamiliarVisibility.canWitchTarget(player) && WitchFamiliars.isEncounterVisitor(witch, player);
    }

    private boolean hasTarget() {
        return witch.getTarget() instanceof ServerPlayer player && eligible(witch, player)
                && witch.distanceToSqr(player) <= CAST_RANGE * CAST_RANGE;
    }

    private boolean readyForRitual() {
        return witch.getHealth() <= witch.getMaxHealth() * 0.5F
                && !witch.getPersistentData().getBoolean(RITUAL_USED)
                && AlexCavesCompat.block("block_of_chocolate").isPresent();
    }

    @Override
    public boolean canUse() {
        long now = witch.level().getGameTime();
        return hasTarget() && now >= retryAt && (now >= nextChessboard || readyForRitual());
    }

    @Override
    public boolean canContinueToUse() {
        boolean active = ritual ? chocolate.stream().anyMatch(block -> !block.isRemoved())
                : chessboard != null && !chessboard.isRemoved();
        return active && witch.isAlive() && ((ServerLevel) witch.level()).players().stream().anyMatch(player ->
                witch.distanceToSqr(player) <= ACTIVE_RANGE * ACTIVE_RANGE && eligible(witch, player));
    }

    @Override
    public void start() {
        ServerLevel level = (ServerLevel) witch.level();
        witch.getNavigation().stop();
        ritual = readyForRitual();
        if (ritual) {
            double angle = witch.getRandom().nextDouble() * Math.PI * 2;
            for (int i = 0; i < CHOCOLATE_COUNT; i++) {
                double direction = angle + i * Math.PI * 2 / CHOCOLATE_COUNT;
                BlockPos ground = ground(level, BlockPos.containing(witch.position().add(
                        Math.cos(direction) * 6, 0, Math.sin(direction) * 6)));
                var block = ChocolateBlockEntity.spawn(level, witch, Vec3.atBottomCenterOf(ground));
                if (block == null) {
                    stop();
                    retryAt = level.getGameTime() + 20;
                    return;
                }
                chocolate.add(block);
            }
            witch.getPersistentData().putBoolean(RITUAL_USED, true);
            feedAt = level.getGameTime() + FEED_DELAY;
            feedStarted = false;
        } else {
            chessboard = FrostingChessboardEntity.spawn(level, witch, ground(level, witch.getTarget().blockPosition()));
        }
    }

    private static BlockPos ground(ServerLevel level, BlockPos pos) {
        return pos.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (witch.getTarget() != null) witch.getLookControl().setLookAt(witch.getTarget(), 25.0F, 25.0F);
        if (!ritual) return;
        ServerLevel level = (ServerLevel) witch.level();
        long now = level.getGameTime();
        if (!feedStarted && now >= feedAt) {
            feedStarted = true;
            chocolate.stream().filter(block -> !block.isRemoved()).min(Comparator.comparingDouble(witch::distanceToSqr))
                    .ifPresent(ChocolateBlockEntity::floatToWitch);
        }
        if (now % 6 == 0 && shielded()) {
            Vec3 ward = witch.position().add(0, witch.getBbHeight() * 0.62, 0);
            double offset = (now % 20) / 20.0;
            for (var block : chocolate) {
                if (block.isRemoved()) continue;
                for (int i = 0; i < 6; i++) {
                    Vec3 point = block.position().add(0, 0.52, 0).lerp(ward, (i + offset) / 6.0);
                    level.sendParticles(WARD_DUST, point.x, point.y, point.z, 1, 0.02, 0.04, 0.02, 0.0);
                }
            }
        }
    }

    boolean shielded() {
        return ritual && chocolate.stream().anyMatch(block -> !block.isRemoved())
                && chocolate.stream().noneMatch(ChocolateBlockEntity::wasBroken);
    }

    @Override
    public void stop() {
        if (chessboard != null) chessboard.discard();
        chessboard = null;
        for (var block : chocolate) block.discard();
        chocolate.clear();
        ritual = false;
        nextChessboard = witch.level().getGameTime() + MadokaConfig.ANNEGRET_CHESS_REST.get();
    }
}
