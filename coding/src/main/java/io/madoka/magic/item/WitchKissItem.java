package io.madoka.magic.item;

import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.stream.Stream;

/** 首次击败对应魔女的收藏品。 */
public class WitchKissItem extends Item {
    public static final List<String> THEMES = Stream.of(MadokaDimensions.BARRIERS,
            CataclysmCompat.knownBarrierThemes(), AlexCavesCompat.knownBarrierThemes(), List.of(WitchTypes.NIGHT))
            .flatMap(List::stream).sorted().toList();

    public WitchKissItem(Properties properties) {
        super(properties);
    }

    public static final String THEME_TAG = "theme";

    public static ItemStack fromWitch(String themeId) {
        ItemStack stack = new ItemStack(ModItems.WITCH_KISS.get());
        stack.getOrCreateTag().putString(THEME_TAG, themeId);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ItemLore.kiss(stack, tooltip);
    }

    public static String getThemeId(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getString(THEME_TAG) : "";
    }

    public static int modelIndex(ItemStack stack) {
        return THEMES.indexOf(getThemeId(stack)) + 1;
    }

    @Override
    public Component getName(ItemStack stack) {
        String themeId = getThemeId(stack);
        if (themeId.isEmpty()) return Component.translatable("item.madoka_magic.witch_kiss.unbound");
        return Component.translatable("item.madoka_magic.witch_kiss",
                GriefSeedItem.witchName(themeId));
    }
}
