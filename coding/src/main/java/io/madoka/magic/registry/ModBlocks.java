package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.block.AltarBlock;
import io.madoka.magic.block.WildflowerBlock;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/** 方块注册表。 */
public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MadokaMagic.MODID);

    /** 契约祭坛：放入材料，最后用纸右键完成仪式。 */
    public static final RegistryObject<Block> ALTAR = BLOCKS.register("altar",
            () -> new AltarBlock(BlockBehaviour.Properties.copy(Blocks.STONE).strength(3.0F).noOcclusion().sound(SoundType.WOOD)));

    public record BarrierFloor(RegistryObject<Block> block, Block previous) {}

    public static final Map<String, BarrierFloor> BARRIER_FLOORS = Map.of(
            "sacrifice", barrierFloor("sacrifice", Blocks.POLISHED_ANDESITE),
            "take", barrierFloor("take", Blocks.POLISHED_ANDESITE),
            "atonement", barrierFloor("atonement", Blocks.POLISHED_ANDESITE),
            "wither", barrierFloor("wither", Blocks.POLISHED_BLACKSTONE),
            "gluttony", barrierFloor("gluttony", Blocks.GREEN_TERRACOTTA),
            "kriemhild_gretchen", barrierFloor("kriemhild_gretchen", Blocks.PINK_TERRACOTTA),
            "homulilly", barrierFloor("homulilly", Blocks.PURPLE_TERRACOTTA),
            "candeloro", barrierFloor("candeloro", Blocks.YELLOW_TERRACOTTA),
            "oktavia", barrierFloor("oktavia", Blocks.BLUE_TERRACOTTA),
            "ophelia", barrierFloor("ophelia", Blocks.RED_TERRACOTTA));

    public static final RegistryObject<Block> BLACK_TULIP = BLOCKS.register("black_tulip",
            () -> new WildflowerBlock(MobEffects.WEAKNESS, 7, BlockBehaviour.Properties.copy(Blocks.ORANGE_TULIP)));

    public static final RegistryObject<Block> ERYNGIUM = BLOCKS.register("eryngium",
            () -> new WildflowerBlock(MobEffects.JUMP, 6, BlockBehaviour.Properties.copy(Blocks.CORNFLOWER)));

    public static final RegistryObject<Block> POTTED_BLACK_TULIP = BLOCKS.register("potted_black_tulip",
            () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, BLACK_TULIP,
                    BlockBehaviour.Properties.copy(Blocks.POTTED_ORANGE_TULIP)));

    public static final RegistryObject<Block> POTTED_ERYNGIUM = BLOCKS.register("potted_eryngium",
            () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, ERYNGIUM,
                    BlockBehaviour.Properties.copy(Blocks.POTTED_CORNFLOWER)));

    private ModBlocks() {
    }

    private static BarrierFloor barrierFloor(String theme, Block previous) {
        return new BarrierFloor(BLOCKS.register("barrier_floor_" + theme,
                () -> new Block(BlockBehaviour.Properties.copy(previous).lootFrom(() -> previous))), previous);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    public static void registerPottedFlowers() {
        FlowerPotBlock pot = (FlowerPotBlock) Blocks.FLOWER_POT;
        pot.addPlant(BLACK_TULIP.getId(), POTTED_BLACK_TULIP);
        pot.addPlant(ERYNGIUM.getId(), POTTED_ERYNGIUM);
    }
}
