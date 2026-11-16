package io.madoka.magic.item;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MagicalGirlRegistry;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

/**
 * 悲叹之种（grief_seed）—— 净化污染值，耐久制。
 *
 * <p>耐久上限 24（注册时写死，见 {@code ModItems.GRIEF_SEED}）。
 * 右键使用：消耗耐久 = min(剩余耐久, 当前污染向上取整)，
 * 每 1 点耐久净化 {@link MadokaConfig#SEED_TAINT_PER_DURABILITY} 点污染；
 * 耐久耗尽物品消失。</p>
 *
 * <p>物品变体记录魔女来源，旧悲叹之种仍从 {@code theme} NBT 读取。</p>
 *
 * <p>耐久直接走 {@link ItemStack#setDamageValue} 而非 hurtAndBreak，
 * 配合 {@link #isEnchantable}/{@link #isBookEnchantable} 双保险，免疫耐久附魔。</p>
 */
public class GriefSeedItem extends Item {
    private final String witch;

    /** 通用悲叹之种的魔女来源，用于命名与贴图。 */
    public static final String OWNER_TAG = "theme";
    private static final String PLAYER_SOURCE = "witchPlayerName";

    public GriefSeedItem(Properties properties) {
        this(properties, "");
    }

    public GriefSeedItem(Properties properties, String witch) {
        super(properties);
        this.witch = witch;
    }

    /** 打上魔女主题印记（传 null/空串则清除）。 */
    public static void setOwner(ItemStack stack, String theme) {
        if (theme == null || theme.isEmpty()) {
            stack.removeTagKey(OWNER_TAG);
        } else {
            stack.getOrCreateTag().putString(OWNER_TAG, theme);
        }
    }

    /** 读取魔女主题印记；没有返回空串。 */
    public static String getOwner(ItemStack stack) {
        if (stack.getItem() instanceof GriefSeedItem seed && !seed.witch.isEmpty()) {
            return seed.witch;
        }
        return stack.hasTag() ? stack.getTag().getString(OWNER_TAG) : "";
    }

    public static ItemStack fromWitch(String witch) {
        ItemStack seed = new ItemStack(ModItems.GRIEF_SEEDS.getOrDefault(witch, ModItems.GRIEF_SEED).get());
        if (seed.is(ModItems.GRIEF_SEED.get())) {
            setOwner(seed, witch);
        }
        return seed;
    }

    public static ItemStack fromPlayerWitch(String witch, String name) {
        ItemStack seed = fromWitch(witch);
        seed.getOrCreateTag().putString(PLAYER_SOURCE, name);
        return seed;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String name = stack.hasTag() ? stack.getTag().getString(PLAYER_SOURCE) : "";
        if (!name.isBlank()) {
            tooltip.add(Component.translatable("item.madoka_magic.grief_seed.player_source", name)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static Component witchName(String witch) {
        var theme = MagicalGirlRegistry.get(witch);
        String fallback = theme == null ? "？" : theme.getDisplayName();
        return Component.translatableWithFallback("witch.madoka_magic." + witch + ".name", fallback);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        float current = data.getTaint();
        if (current <= 0.0F) {
            return InteractionResultHolder.pass(stack);
        }
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        if (remaining <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        // 消耗耐久 = min(剩余耐久, 当前污染向上取整)，每 1 点耐久净化配置值点污染
        int cost = Math.min(remaining, (int) Math.ceil(current));
        float purified = (float) (cost * MadokaConfig.SEED_TAINT_PER_DURABILITY.get());
        float removed = data.removeTaint(purified);
        if (removed <= 0.0F) {
            return InteractionResultHolder.pass(stack);
        }
        data.save(player);
        // 直接扣 damageValue，不走 hurtAndBreak：耐久附魔（Unbreaking）对悲叹之种无效
        int newDamage = stack.getDamageValue() + cost;
        if (newDamage >= stack.getMaxDamage()) {
            stack.shrink(1);
        } else {
            stack.setDamageValue(newDamage);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);
        MadokaNetwork.syncTo(player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public Component getName(ItemStack stack) {
        String theme = getOwner(stack);
        if (theme.isEmpty()) {
            return super.getName(stack);
        }
        return Component.translatable("item.madoka_magic.grief_seed.themed",
                witchName(theme));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }
}
