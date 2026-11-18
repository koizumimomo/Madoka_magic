package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.block.AltarBlockEntity;
import io.madoka.magic.block.DisplayRingBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方块实体注册表。 */
public final class ModBlockEntities {

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MadokaMagic.MODID);

    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<AltarBlockEntity>> ALTAR =
            BLOCK_ENTITIES.register("altar", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                    .of(AltarBlockEntity::new, ModBlocks.ALTAR.get()).build(null));

    public static final RegistryObject<BlockEntityType<DisplayRingBlockEntity>> DISPLAY_RING =
            BLOCK_ENTITIES.register("display_ring", () -> BlockEntityType.Builder
                    .of(DisplayRingBlockEntity::new, ModBlocks.DISPLAY_RING.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
