package io.madoka.magic.block;

import io.madoka.magic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 契约祭坛。
 *
 * <p>右击放置材料，再用手持催化材料完成仪式。</p>
 */
public class AltarBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = makeShapes();

    public AltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    private static Map<Direction, VoxelShape> makeShapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        var south = Shapes.or(box(0.5, 8, 2.5, 15.5, 9, 13.5), box(0.5, 0, 2.6, 4.5, 8, 13.4),
                box(14.5, 0, 2.6, 15.5, 8, 3.6), box(14.5, 0, 12.4, 15.5, 8, 13.4),
                box(0.5, 9, 2.5, 15.5, 12.5, 3), box(4.5, 6.5, 12.4, 14.5, 8, 13.4),
                box(4.5, 6.5, 2.6, 14.5, 8, 3.6));
        VoxelShape current = south;
        Direction front = Direction.SOUTH;
        for (int i = 0; i < 4; i++) {
            shapes.put(front, current);
            VoxelShape rotated = Shapes.empty();
            for (var part : current.toAabbs()) {
                rotated = Shapes.or(rotated, Shapes.box(1 - part.maxZ, part.minY, part.minX,
                        1 - part.minZ, part.maxY, part.maxX));
            }
            current = rotated;
            front = front.getClockWise();
        }
        return Map.copyOf(shapes);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            // 空手右键：按放入的顺序把材料取回来（参考灵灾的精魂祭坛）
            ItemStack taken = altar.takeFirst();
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) {
                    player.drop(taken, false);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.4F);
            }
            return InteractionResult.CONSUME;
        }
        var result = altar.performRitual(serverLevel, pos, player, held);
        if (result != AltarBlockEntity.RitualResult.NOT_A_CATALYST) {
            if (result == AltarBlockEntity.RitualResult.NO_RECIPE) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.altar.no_recipe"), true);
            } else if (result == AltarBlockEntity.RitualResult.INSUFFICIENT_HEALTH) {
                player.displayClientMessage(Component.translatable("message.madoka_magic.altar.insufficient_health"), true);
            }
            return InteractionResult.CONSUME;
        }
        // 其他材料 → 放进槽位（悬浮在祭坛上方）
        if (altar.addMaterial(held)) {
            held.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.6F);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.ALTAR.get().create(pos, state);
    }
}
