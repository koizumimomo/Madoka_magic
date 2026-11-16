package io.madoka.magic.faction;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.GluttonyWitchEntity;
import io.madoka.magic.entity.AtonementMinions;
import io.madoka.magic.entity.DessertFamiliarEntity;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class WitchFamiliars {
    public static final String ENCOUNTER_TAG = "madoka_barrier_encounter";

    private WitchFamiliars() {}

    public static boolean isEncounterVisitor(Entity member, ServerPlayer player) {
        if (member.level() != player.level()) {
            return false;
        }
        var data = member.getPersistentData();
        if (!data.hasUUID(ENCOUNTER_TAG)) {
            return true;
        }
        var visit = MadokaSavedData.get(player.serverLevel()).getEnter(player.getUUID());
        return visit != null && data.getUUID(ENCOUNTER_TAG).equals(visit.getEncounter());
    }

    public static String themeOf(Mob mob) {
        if (mob instanceof DessertFamiliarEntity) {
            return "candeloro";
        }
        if (AtonementMinions.isMinion(mob)) {
            return "atonement";
        }
        if (mob instanceof GluttonyWitchEntity fragment && fragment.isFragment()) {
            return WitchTypes.GLUTTONY;
        }
        if (mob.getMobType() == MobType.UNDEAD) {
            return WitchTypes.WITHER;
        }
        for (String theme : MadokaDimensions.BARRIERS) {
            if (mob.getType().is(pool(theme))) {
                return theme;
            }
        }
        return "";
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        String barrier = MadokaDimensions.barrierTheme(level.dimension());
        if (barrier.isEmpty()) {
            if (AtonementMinions.isMinion(mob) || mob instanceof DessertFamiliarEntity || mob.getType() == ModEntities.BLACK_BEE.get()
                    || mob.getType() == ModEntities.BUTTERFLY_PRINCESS.get()) {
                event.setCanceled(true);
            }
            return;
        }
        String boss = WitchTypes.bossTheme(mob);
        String familiar = themeOf(mob);
        boolean companion = mob.getType() == ModEntities.HALF_WITCH.get()
                || familiar.isEmpty() && mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null
                || mob instanceof Bee && mob.getType() == EntityType.BEE
                        && mob.getPersistentData().hasUUID("madoka_swarm_owner");
        if (!barrier.equals(boss.isEmpty() ? familiar : boss) && !companion) {
            event.setCanceled(true);
        }
    }

    public static void spawn(ServerLevel level, BlockPos arena, String theme, UUID encounter) {
        if (!MadokaDimensions.hasBarrier(theme)) {
            return;
        }
        var types = java.util.stream.StreamSupport.stream(BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(pool(theme)).spliterator(), false)
                .map(net.minecraft.core.Holder::value)
                .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type))).toList();
        int perType = "take".equals(theme) ? 3 : "sacrifice".equals(theme) ? 2 : 1;
        int regularCount = types.size() * perType;
        int count = regularCount + ("take".equals(theme) && types.contains(ModEntities.BLACK_BEE.get()) ? 1 : 0);
        for (int i = 0; i < count; i++) {
            EntityType<?> type = i < regularCount ? types.get(i % types.size()) : ModEntities.BLACK_BEE.get();
            var entity = type.create(level);
            if (!(entity instanceof Mob mob) || !theme.equals(themeOf(mob)) || !WitchTypes.bossTheme(mob).isEmpty()) {
                continue;
            }
            double angle = i * Math.PI * 2.0D / count;
            int x = arena.getX() + (int) Math.round(Math.cos(angle) * 12.0D);
            int z = arena.getZ() + (int) Math.round(Math.sin(angle) * 12.0D);
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (mob instanceof Guardian) {
                y = prepareGuardianPool(level, new BlockPos(x, y, z));
            }
            var pos = new BlockPos(x, y, z);
            mob.moveTo(x + 0.5D, y, z + 0.5D, 0, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            if (mob instanceof AbstractPiglin piglin) {
                piglin.setImmuneToZombification(true);
            } else if (mob instanceof Hoglin hoglin) {
                hoglin.setImmuneToZombification(true);
            }
            mob.getPersistentData().putUUID(ENCOUNTER_TAG, encounter);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }

    private static int prepareGuardianPool(ServerLevel level, BlockPos surface) {
        BlockPos center = surface.below(3);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 2, 3))) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return center.getY();
    }

    private static TagKey<EntityType<?>> pool(String theme) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "familiars/" + theme));
    }
}
