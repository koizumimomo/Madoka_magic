package io.madoka.magic.block;

import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.item.RitualItemAppearance;
import io.madoka.magic.item.WishMaterialItem;
import io.madoka.magic.network.RitualCraftFx;
import io.madoka.magic.registry.ModBlockEntities;
import io.madoka.magic.ritual.AltarRecipe;
import io.madoka.magic.ritual.RitualRecipe;
import io.madoka.magic.ritual.RitualRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 契约祭坛的方块实体。
 *
 * <p>四个材料槽。用手持催化材料右击，触发匹配的仪式。</p>
 */
public class AltarBlockEntity extends BlockEntity {
    enum RitualResult { SUCCESS, NO_RECIPE, NOT_A_CATALYST, INSUFFICIENT_HEALTH }

    /** 材料槽数量。 */
    public static final int SLOT_COUNT = 4;

    private static final int[] SLOTS = {0, 1, 2, 3};

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> handler = LazyOptional.of(() -> items);

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ALTAR.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.5D, 1.0D, 0.5D);
    }

    @Nullable
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        return cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER
                ? handler.cast() : super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        handler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.deserializeNBT(tag.getCompound("Items"));
    }

    /** 进区块/登录时发给客户端的完整数据（悬浮材料渲染要用）。 */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    /** 数据变化时推给附近客户端的更新包。 */
    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 存档 + 同步客户端（渲染器读的是客户端副本，不写它就会一直显示旧内容）。 */
    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** 放入材料。返回是否成功。 */
    public boolean addMaterial(ItemStack stack) {
        for (int slot : SLOTS) {
            if (items.getStackInSlot(slot).isEmpty()) {
                items.setStackInSlot(slot, stack.copyWithCount(1));
                setChangedAndSync();
                return true;
            }
        }
        return false;
    }

    /** 槽位里的材料。 */
    public ItemStack[] getMaterials() {
        ItemStack[] result = new ItemStack[SLOT_COUNT];
        for (int slot : SLOTS) {
            result[slot] = items.getStackInSlot(slot);
        }
        return result;
    }

    /** 供渲染器使用：直接拿到内部的物品处理器。 */
    public ItemStackHandler getItems() {
        return items;
    }

    /**
     * 按放入顺序取下一个东西（先进先出）。
     * <p>参考灵灾（Malum）的精魂祭坛：右键空手可以把祭坛上的材料一个一个取回来，
     * 顺序与放上去的顺序一致。</p>
     *
     * @return 取出的物品（空则返回空栈）
     */
    public ItemStack takeFirst() {
        for (int slot : SLOTS) {
            ItemStack result = items.extractItem(slot, 1, false);
            if (!result.isEmpty()) {
                if (items.getStackInSlot(slot).isEmpty()) {
                    for (int next = slot + 1; next < SLOT_COUNT; next++) {
                        items.setStackInSlot(next - 1, items.getStackInSlot(next));
                    }
                    items.setStackInSlot(SLOT_COUNT - 1, ItemStack.EMPTY);
                }
                setChangedAndSync();
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 祭坛上现在有几件材料。 */
    public int getItemCount() {
        int count = 0;
        for (int slot : SLOTS) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 播放仪式并产出结果。
     * <p>优先使用同步给客户端的祭坛配方，随后兼容旧数据包。</p>
     *
     */
    RitualResult performRitual(ServerLevel serverLevel, BlockPos pos, Player player, ItemStack held) {
        var recipes = serverLevel.getRecipeManager().getAllRecipesFor(AltarRecipe.TYPE.get()).stream()
                .filter(recipe -> recipe.catalyst().test(held)).toList();
        if (recipes.isEmpty() && !held.is(Items.PAPER) && !held.is(Items.AMETHYST_SHARD)) {
            return RitualResult.NOT_A_CATALYST;
        }
        SimpleContainer materials = new SimpleContainer(SLOT_COUNT);
        for (int slot : SLOTS) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                materials.setItem(slot, stack.copyWithCount(1));
            }
        }
        if (materials.isEmpty()) {
            return RitualResult.NO_RECIPE;
        }

        AltarRecipe recipe = recipes.stream().filter(candidate -> candidate.matches(materials, serverLevel))
                .min(java.util.Comparator.comparing(AltarRecipe::getId)).orElse(null);
        ItemStack result = recipe == null ? (held.is(Items.PAPER) ? legacyResult(materials) : ItemStack.EMPTY)
                : recipe.assemble(materials, serverLevel.registryAccess());
        if (result.isEmpty() || !canCraftContract(result, materials)) {
            return RitualResult.NO_RECIPE;
        }
        boolean wish = result.getItem() instanceof WishMaterialItem;
        float healthCost = recipe == null ? 0.0F : player.getMaxHealth() * recipe.lifeCost();
        if (healthCost >= player.getHealth()) {
            return RitualResult.INSUFFICIENT_HEALTH;
        }
        if (wish) {
            WishMaterialItem.bind(result, player);
        }
        NonNullList<ItemStack> remainders = recipe == null || wish
                ? NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY) : recipe.getRemainingItems(materials);

        if (healthCost > 0.0F) {
            player.setHealth(player.getHealth() - healthCost);
        }
        held.shrink(1);
        for (int slot : SLOTS) {
            items.extractItem(slot, 1, false);
        }
        setChangedAndSync();

        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                giveResult(serverLevel, player, pos, remainder);
            }
        }
        if (result.getItem() instanceof ContractItem && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.madoka_magic.contract_created",
                    result.getHoverName()), false);
        }
        var appearance = RitualItemAppearance.of(result);
        giveResult(serverLevel, player, pos, result);
        playRitualEffects(serverLevel, pos, appearance);
        return RitualResult.SUCCESS;
    }

    private static void playRitualEffects(ServerLevel level, BlockPos pos, @Nullable RitualItemAppearance appearance) {
        if (appearance != null) {
            RitualCraftFx.send(level, pos, appearance);
        } else {
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 1.0D;
            double z = pos.getZ() + 0.5D;
            for (int i = 0; i < 30; i++) {
                level.sendParticles(ParticleTypes.ENCHANT,
                        x + Math.cos(i * 0.6D) * 1.2D, y + i * 0.05D, z + Math.sin(i * 0.6D) * 1.2D,
                        3, 0.1D, 0.1D, 0.1D, 0.02D);
            }
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.4D, 0.4D, 0.4D, 0.05D);
        }
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.3F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6F, 1.6F);
    }

    private static boolean canCraftContract(ItemStack result, SimpleContainer materials) {
        if (!(result.getItem() instanceof ContractItem)) {
            return true;
        }
        var theme = MagicalGirlRegistry.get(ContractItem.getThemeId(result));
        if (theme == null) {
            return true;
        }
        var required = ForgeRegistries.ITEMS.getValue(theme.getContractMaterial());
        if (!(required instanceof WishMaterialItem)) {
            return true;
        }
        for (int slot : SLOTS) {
            ItemStack stack = materials.getItem(slot);
            if (stack.is(required) && WishMaterialItem.hasWish(stack)) {
                return true;
            }
        }
        return false;
    }

    private static void giveResult(ServerLevel level, Player player, BlockPos pos, ItemStack result) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.getInventory().add(result)) {
                serverPlayer.drop(result, false);
            }
        } else {
            net.minecraft.world.entity.item.ItemEntity entity =
                    new net.minecraft.world.entity.item.ItemEntity(level,
                            pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, result);
            level.addFreshEntity(entity);
        }
    }

    private ItemStack legacyResult(SimpleContainer altar) {
        List<ItemStack> materials = new ArrayList<>();
        for (int slot : SLOTS) {
            if (!altar.getItem(slot).isEmpty()) {
                materials.add(altar.getItem(slot));
            }
        }
        if (materials.size() == 1) {
            MagicalGirl theme = MagicalGirlRegistry.byContractMaterialItem(materials.get(0));
            if (theme != null) {
                return ContractItem.create(theme.getId());
            }
        }
        RitualRecipe legacy = RitualRecipes.findMatch(materials);
        return legacy == null ? ItemStack.EMPTY : legacy.output().copy();
    }
}
