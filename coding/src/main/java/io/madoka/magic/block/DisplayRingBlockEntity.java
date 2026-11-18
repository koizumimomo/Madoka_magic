package io.madoka.magic.block;

import io.madoka.magic.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class DisplayRingBlockEntity extends BlockEntity {
    private static final String ITEM_TAG = "DisplayItem";
    private ItemStack item = ItemStack.EMPTY;

    public DisplayRingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DISPLAY_RING.get(), pos, state);
    }

    public ItemStack getDisplayedItem() {
        return item;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.85, 0, 0.85).expandTowards(0, 1.8, 0);
    }

    boolean insertOne(ItemStack held) {
        if (held.isEmpty() || !item.isEmpty()) return false;
        item = held.copyWithCount(1);
        syncItem();
        return true;
    }

    ItemStack removeItem() {
        ItemStack previous = item;
        item = ItemStack.EMPTY;
        if (!previous.isEmpty()) syncItem();
        return previous;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(ITEM_TAG, item.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        item = tag.contains(ITEM_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(ITEM_TAG)).copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncItem() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }
}
