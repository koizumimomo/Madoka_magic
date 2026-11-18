package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.entity.HalfWitchEntity;
import io.madoka.magic.entity.GluttonyWitchEntity;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchPortalEntity;
import io.madoka.magic.entity.BlackBeeEntity;
import io.madoka.magic.entity.RainThornEntity;
import io.madoka.magic.entity.ArenaCocoonEntity;
import io.madoka.magic.entity.LightArrowEntity;
import io.madoka.magic.entity.AtonementMinions;
import io.madoka.magic.entity.FrameShardEntity;
import io.madoka.magic.entity.NightGearEntity;
import io.madoka.magic.entity.MermaidNoteEntity;
import io.madoka.magic.entity.MourningSkullOrbitEntity;
import io.madoka.magic.entity.CandeloroCutleryEntity;
import io.madoka.magic.entity.DessertFamiliarEntity;
import io.madoka.magic.entity.ChocolateBlockEntity;
import io.madoka.magic.entity.FrostingChessboardEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 实体注册表。 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MadokaMagic.MODID);

    /** 魔女（每个主题一位，各自住在自己的维度）。 */
    public static final RegistryObject<EntityType<MadokaWitchEntity>> WITCH =
            ENTITIES.register("witch", () -> EntityType.Builder
                    .<MadokaWitchEntity>of(MadokaWitchEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .fireImmune()
                    .build("madoka_magic:witch"));

    public static final RegistryObject<EntityType<MadokaWitchEntity>> BUTTERFLY_PRINCESS =
            ENTITIES.register("butterfly_princess", () -> EntityType.Builder
                    .<MadokaWitchEntity>of(MadokaWitchEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(8).build("madoka_magic:butterfly_princess"));

    public static final RegistryObject<EntityType<GluttonyWitchEntity>> GLUTTONY_WITCH =
            ENTITIES.register("gluttony_witch", () -> EntityType.Builder
                    .of(GluttonyWitchEntity::new, MobCategory.MONSTER)
                    .sized(GluttonyWitchEntity.BODY_WIDTH, GluttonyWitchEntity.BODY_WIDTH)
                    .clientTrackingRange(10).updateInterval(2).build("madoka_magic:gluttony_witch"));

    public static final RegistryObject<EntityType<BlackBeeEntity>> BLACK_BEE =
            ENTITIES.register("black_bee", () -> EntityType.Builder
                    .<BlackBeeEntity>of(BlackBeeEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.6F).clientTrackingRange(8).build("madoka_magic:black_bee"));

    public static final RegistryObject<EntityType<RainThornEntity>> RAIN_THORN =
            ENTITIES.register("rain_thorn", () -> EntityType.Builder
                    .of(RainThornEntity::new, MobCategory.MISC)
                    .sized(0.85F, 1.2F).clientTrackingRange(8).noSummon()
                    .build("madoka_magic:rain_thorn"));

    public static final RegistryObject<EntityType<ArenaCocoonEntity>> ARENA_COCOON =
            ENTITIES.register("arena_cocoon", () -> EntityType.Builder
                    .of(ArenaCocoonEntity::new, MobCategory.MISC)
                    .sized(1.05F, 1.65F).clientTrackingRange(10).noSummon()
                    .build("madoka_magic:arena_cocoon"));

    public static final RegistryObject<EntityType<LightArrowEntity>> LIGHT_ARROW =
            ENTITIES.register("light_arrow", () -> EntityType.Builder
                    .of(LightArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:light_arrow"));

    public static final RegistryObject<EntityType<FrameShardEntity>> FRAME_SHARD =
            ENTITIES.register("frame_shard", () -> EntityType.Builder
                    .of(FrameShardEntity::new, MobCategory.MISC)
                    .sized(0.32F, 0.32F).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:frame_shard"));

    public static final RegistryObject<EntityType<NightGearEntity>> NIGHT_GEAR =
            ENTITIES.register("night_gear", () -> EntityType.Builder
                    .of(NightGearEntity::new, MobCategory.MISC)
                    .sized(2.25F, 2.25F).clientTrackingRange(10).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:night_gear"));

    public static final RegistryObject<EntityType<MermaidNoteEntity>> MERMAID_NOTE =
            ENTITIES.register("mermaid_note", () -> EntityType.Builder
                    .of(MermaidNoteEntity::new, MobCategory.MISC)
                    .sized(0.38F, 0.38F).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:mermaid_note"));

    public static final RegistryObject<EntityType<MourningSkullOrbitEntity>> MOURNING_SKULL_ORBIT =
            ENTITIES.register("mourning_skull_orbit", () -> EntityType.Builder
                    .of(MourningSkullOrbitEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(10).updateInterval(20).noSave().noSummon()
                    .build("madoka_magic:mourning_skull_orbit"));

    public static final RegistryObject<EntityType<DessertFamiliarEntity>> DESSERT_FAMILIAR =
            ENTITIES.register("dessert_familiar", () -> EntityType.Builder
                    .of(DessertFamiliarEntity::new, MobCategory.MONSTER)
                    .sized(1.18F, 0.96F).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:dessert_familiar"));

    public static final RegistryObject<EntityType<CandeloroCutleryEntity>> CANDELORO_CUTLERY =
            ENTITIES.register("candeloro_cutlery", () -> EntityType.Builder
                    .of(CandeloroCutleryEntity::new, MobCategory.MISC)
                    .sized(0.32F, 0.32F).clientTrackingRange(8).updateInterval(2).noSave().noSummon()
                    .build("madoka_magic:candeloro_cutlery"));

    public static final RegistryObject<EntityType<FrostingChessboardEntity>> FROSTING_CHESSBOARD =
            ENTITIES.register("frosting_chessboard", () -> EntityType.Builder
                    .of(FrostingChessboardEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.25F).clientTrackingRange(10).updateInterval(20).noSave().noSummon()
                    .build("madoka_magic:frosting_chessboard"));

    public static final RegistryObject<EntityType<ChocolateBlockEntity>> ANNEGRET_CHOCOLATE =
            ENTITIES.register("annegret_chocolate", () -> EntityType.Builder
                    .of(ChocolateBlockEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(1).noSave().noSummon()
                    .build("madoka_magic:annegret_chocolate"));

    public static final RegistryObject<EntityType<AtonementMinions.MourningZombie>> MOURNING_ZOMBIE =
            ENTITIES.register("mourning_zombie", () -> EntityType.Builder
                    .of(AtonementMinions.MourningZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F).clientTrackingRange(8).noSummon().build("madoka_magic:mourning_zombie"));

    public static final RegistryObject<EntityType<AtonementMinions.MourningSkeleton>> MOURNING_SKELETON =
            ENTITIES.register("mourning_skeleton", () -> EntityType.Builder
                    .of(AtonementMinions.MourningSkeleton::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.99F).clientTrackingRange(8).noSummon().build("madoka_magic:mourning_skeleton"));

    /** 半魔女化身（玩家污染值 > 60 时释放的协助者）。 */
    public static final RegistryObject<EntityType<HalfWitchEntity>> HALF_WITCH =
            ENTITIES.register("half_witch", () -> EntityType.Builder
                    .<HalfWitchEntity>of(HalfWitchEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .fireImmune()
                    .build("madoka_magic:half_witch"));

    /** 魔女结界传送门（无模型实体，触碰传送；返回点见 MadokaSavedData）。 */
    public static final RegistryObject<EntityType<WitchPortalEntity>> WITCH_PORTAL =
            ENTITIES.register("witch_portal", () -> EntityType.Builder
                    .<WitchPortalEntity>of(WitchPortalEntity::new, MobCategory.MISC)
                    .sized(1.5F, 2.5F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .noSummon()
                    .fireImmune()
                    .build("madoka_magic:witch_portal"));

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(DESSERT_FAMILIAR.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0D).add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 24.0D).build());
        event.put(MOURNING_ZOMBIE.get(), Zombie.createAttributes().build());
        event.put(MOURNING_SKELETON.get(), Skeleton.createAttributes().build());
        event.put(GLUTTONY_WITCH.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D).add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.8D).build());
        event.put(BUTTERFLY_PRINCESS.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.29D).add(Attributes.FOLLOW_RANGE, 24.0D).build());
        event.put(BLACK_BEE.get(), BlackBeeEntity.createAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D).add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D).build());
        // 注意：这里不能读配置——属性创建事件发生在配置加载之前。
        // 配置值在实体构造时套用（见 MadokaWitchEntity / HalfWitchEntity）。
        event.put(WITCH.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 120.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .build());

        event.put(HALF_WITCH.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .build());
    }
}
