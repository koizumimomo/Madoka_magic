package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.item.GriefSeedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 悲叹之种不该被砂轮当成可修复的耐久物品。 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class GriefSeedGuardEvents {

    private GriefSeedGuardEvents() {}

    @SubscribeEvent
    public static void onGrindstonePlace(GrindstoneEvent.OnPlaceItem event) {
        if (event.getTopItem().getItem() instanceof GriefSeedItem
                || event.getBottomItem().getItem() instanceof GriefSeedItem) {
            // 没有取消入口，输出置空后砂轮不会处理它。
            event.setOutput(ItemStack.EMPTY);
        }
    }
}
