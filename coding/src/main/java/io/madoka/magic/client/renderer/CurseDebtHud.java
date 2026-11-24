package io.madoka.magic.client.renderer;

import io.madoka.magic.client.ClientCurseDebt;
import io.madoka.magic.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/** The debt is an amount, so its display has an icon and a number. */
public final class CurseDebtHud {
    public static final int WIDTH = 112, HEIGHT = 30;

    private CurseDebtHud() {}

    public static void render(GuiGraphics graphics, int x, int y, boolean preview) {
        String id = ClientCurseDebt.item();
        double amount = ClientCurseDebt.amount();
        if (preview) {
            var item = ModItems.CURSE_WEAPONS.get("atonement");
            id = (item == null ? ForgeRegistries.ITEMS.getKey(Items.PAPER) : item.getId()).toString();
            amount = 12.5 + 2.5 * (Util.getMillis() / 1000 % 4);
        } else if (!ClientCurseDebt.visible()) return;
        var itemId = ResourceLocation.tryParse(id);
        var item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        if (item != null) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 3, y + 3, 0);
            pose.scale(1.5f, 1.5f, 1);
            graphics.renderItem(new ItemStack(item), 0, 0);
            pose.popPose();
        }
        String value = String.format(Locale.ROOT, amount >= 10000 ? "%.0f" : "%.1f", amount);
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1.25f, 80f / Math.max(1, font.width(value)));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 32, y + 10, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, value, 0, 0, amount > 0 ? 0xffbcecff : 0xff8996a6, true);
        pose.popPose();
    }
}
