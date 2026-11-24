package io.madoka.magic.item;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.registry.ModItems;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * 契约。
 *
 * <p>由祭坛仪式产出。性质由物品变体决定，旧契约仍从 NBT 读取。右键打开确认界面，
 * 确认后玩家成为魔法少女。</p>
 */
public class ContractItem extends Item {
    private final String theme;

    public ContractItem(Properties properties) {
        this(properties, "");
    }

    public ContractItem(Properties properties, String theme) {
        super(properties);
        this.theme = theme;
    }

    /** NBT 键：宝石性质 id。 */
    public static final String THEME_TAG = "theme";

    public static String getThemeId(ItemStack stack) {
        if (stack.getItem() instanceof ContractItem contract && !contract.theme.isEmpty()) {
            return contract.theme;
        }
        return stack.getTag() != null ? stack.getTag().getString(THEME_TAG) : "";
    }

    public static ItemStack create(String themeId) {
        ItemStack stack = new ItemStack(ModItems.CONTRACTS.getOrDefault(themeId, ModItems.CONTRACT).get());
        if (stack.is(ModItems.CONTRACT.get())) {
            setThemeId(stack, themeId);
        }
        return stack;
    }

    public static void setThemeId(ItemStack stack, String themeId) {
        var tag = stack.getOrCreateTag();
        tag.putString(THEME_TAG, themeId);
    }

    /** NBT 键：契约归属玩家 UUID / 名字。由合成所用的许愿材料继承。 */
    public static final String OWNER_TAG = "madoka_contract_owner";
    public static final String OWNER_NAME_TAG = "madoka_contract_owner_name";

    public static void setOwner(ItemStack stack, UUID player, String name) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(OWNER_TAG, player);
        tag.putString(OWNER_NAME_TAG, name);
    }

    public static UUID getOwnerId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(OWNER_TAG) ? tag.getUUID(OWNER_TAG) : null;
    }

    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(OWNER_NAME_TAG);
    }

    /** 旧契约没有归属信息，保持对所有人可用。 */
    public static boolean canUse(ItemStack stack, Player player) {
        UUID owner = getOwnerId(stack);
        return owner == null || owner.equals(player.getUUID());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!canUse(stack, player)) {
            // fail 不会把交互发包给服务端，所以两侧都提示一次
            player.displayClientMessage(Component.translatable(
                    "message.madoka_magic.contract.not_owner"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && PlayerMadokaData.of(player).isContracted()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.contract.already_contracted"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            // 客户端类只能在客户端侧加载：用 DistExecutor 隔离，避免 DedicatedServer 加载本类时炸掉
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.madoka.magic.client.MadokaClient.openContractConfirm(stack));
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemLore.contract(stack, tooltip);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!this.theme.isEmpty()) {
            return super.getName(stack);
        }
        MagicalGirl theme = MagicalGirlRegistry.get(getThemeId(stack));
        return theme == null ? super.getName(stack) : Component.translatable("item.madoka_magic.contract.named",
                Component.translatableWithFallback(theme.getTranslationKey(), theme.getDisplayName()));
    }

    /**
     * 服务端：确认契约，玩家成为魔法少女。
     */
    public static void confirmContract(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (data.isContracted()) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.contract.already_contracted"), true);
            return;
        }
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.isEmpty() || !(stack.getItem() instanceof ContractItem)) {
            stack = player.getItemInHand(InteractionHand.OFF_HAND);
        }
        if (stack.isEmpty() || !(stack.getItem() instanceof ContractItem)) {
            return;
        }
        if (!canUse(stack, player)) {
            player.displayClientMessage(Component.translatable("message.madoka_magic.contract.not_owner"), true);
            return;
        }
        String themeId = getThemeId(stack);
        MagicalGirl theme = MagicalGirlRegistry.get(themeId);
        if (theme == null) {
            return;
        }
        boolean firstContract = !data.isEverContracted();
        data.setTheme(themeId);
        data.setContracted(true);
        data.setTransformed(false);
        data.setWitchPath(false);
        data.setEverContracted(true);
        data.setPendingWitchChoice(false);
        // 初始纯度：只在纯度仍为 0 时给（换契约不清已有成长）
        var purityAttr = player.getAttribute(MadokaAttributes.GEM_PURITY.get());
        if (purityAttr != null && purityAttr.getBaseValue() <= 0.0D) {
            purityAttr.setBaseValue(MadokaConfig.INITIAL_PURITY.get());
        }
        // 换契约 → 清掉上一个角色的技能
        data.getUnlockedSpells().clear();
        // 按<b>新角色</b>的技能表解锁（不是全局列表）；解锁线看纯度百分比
        for (String id : io.madoka.magic.spell.SpellRegistry.defaultUnlocked(MadokaMath.levelOf(player), theme, false)) {
            data.unlockSpell(id);
        }
        data.save(player);
        PlayerTransformation.applyAttributes(player);
        if (firstContract && player.connection != null && player.connection.connection.channel() != null) {
            player.serverLevel().getChunkSource().move(player);
        }

        stack.shrink(1);

        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
        MadokaNetwork.syncTo(player);
    }
}
