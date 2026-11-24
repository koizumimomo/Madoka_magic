package io.madoka.magic.client;

import com.mojang.datafixers.util.Either;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.item.CurseRelic;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.item.WeaponEngraving;
import io.madoka.magic.item.WitchKissItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 铭刻 / 契约 / 咒具相关 tooltip。
 *
 * <p>铭刻武器、契约、契约伴生武器与刻名紫水晶簇追加一行闪耀流彩文字：
 * 逐字染色，色相随时间流动、亮度沿文字方向扫过，tooltip 每帧重建，因此会持续闪烁。</p>
 *
 * <p>咒具、悲叹之种与魔女之吻改用描边文字（{@link OutlinedTooltip}）：
 * 填充色 + 8 方向描边 + 一圈微光，不随帧变色。文本沿用每种魔女已有的 lore。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class EngravedTooltip {
    private EngravedTooltip() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (WeaponEngraving.isEngraved(stack)) {
            String name = WeaponEngraving.ownerName(stack);
            if (!name.isEmpty()) {
                event.getToolTip().add(shimmer(Component.translatable(
                        "tooltip.madoka_magic.engraved", name).getString()));
            }
            return;
        }
        if (WeaponEngraving.isMarkedCluster(stack)) {
            String name = WeaponEngraving.ownerName(stack);
            if (!name.isEmpty()) {
                event.getToolTip().add(shimmer(Component.translatable(
                        "tooltip.madoka_magic.cluster_owner", name).getString()));
            }
            event.getToolTip().add(Component.translatable(
                    "tooltip.madoka_magic.cluster_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        String key = shimmerKey(stack);
        if (key != null) {
            List<Component> tooltip = event.getToolTip();
            tooltip.add(Math.min(1, tooltip.size()), shimmer(Component.translatable(key).getString()));
        }
    }

    /** 契约与三把伴生武器的流彩文案 key。 */
    private static String shimmerKey(ItemStack stack) {
        if (stack.getItem() instanceof ContractItem) {
            return "tooltip.madoka_magic.shimmer.contract";
        }
        ContractWeapon weapon = ContractWeapon.of(stack);
        return weapon == null ? null : "tooltip.madoka_magic.shimmer." + weapon.id;
    }

    /** 描边文本只走 tooltip 组件，不进入普通文本行。 */
    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        OutlinedTooltip line = outlinedLine(event.getItemStack());
        if (line == null) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.right(line));
    }

    private static OutlinedTooltip outlinedLine(ItemStack stack) {
        if (stack.getItem() instanceof GriefSeedItem) {
            return OutlinedTooltip.blackOnWhite(lore("grief_seed", GriefSeedItem.getOwner(stack)));
        }
        if (stack.getItem() instanceof WitchKissItem) {
            return OutlinedTooltip.blackOnWhite(lore("witch_kiss", WitchKissItem.getThemeId(stack)));
        }
        if (stack.getItem() instanceof CurseRelic relic) {
            // 咒具由对应魔女的悲叹之种炼成，沿用同一句魔女文案
            return OutlinedTooltip.blackOnWhite(lore("grief_seed", relic.theme()));
        }
        return null;
    }

    private static Component lore(String type, String theme) {
        String prefix = "lore.madoka_magic." + type + ".";
        if (theme == null || theme.isEmpty()) {
            return Component.translatable(prefix + "default");
        }
        return Component.translatableWithFallback(prefix + theme,
                Component.translatable(prefix + "default").getString());
    }

    /** 逐字染色：色相随时间平移，亮度波沿文字扫过，形成闪耀流彩。 */
    private static Component shimmer(String text) {
        double time = System.currentTimeMillis() / 1000.0D;
        MutableComponent root = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            float hue = (float) ((time * 0.16D + i * 0.035D) % 1.0D);
            float brightness = (float) (0.72D + 0.28D * Math.sin(time * 3.4D - i * 0.55D));
            int rgb = hsb(hue, 0.55F, brightness);
            root.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(true)));
        }
        return root;
    }

    private static int hsb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        int sector = (int) h;
        float f = h - sector;
        float p = brightness * (1.0F - saturation);
        float q = brightness * (1.0F - saturation * f);
        float t = brightness * (1.0F - saturation * (1.0F - f));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = brightness; g = t; b = p; }
            case 1 -> { r = q; g = brightness; b = p; }
            case 2 -> { r = p; g = brightness; b = t; }
            case 3 -> { r = p; g = q; b = brightness; }
            case 4 -> { r = t; g = p; b = brightness; }
            default -> { r = brightness; g = p; b = q; }
        }
        return channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }
}
