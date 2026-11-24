package io.madoka.magic.client.renderer;

import com.mojang.math.Axis;
import com.mojang.blaze3d.systems.RenderSystem;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.ClientCurseStacks;
import io.madoka.magic.client.CurseGaugeLayout;
import io.madoka.magic.registry.ModItems;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** A central astrolabe with a resource arc: uncharged needles become four-point stars. */
public final class CurseStackHud {
    public static final int WIDTH = CurseGaugeLayout.WIDTH, HEIGHT = CurseGaugeLayout.HEIGHT;
    private static final ResourceLocation ATLAS = texture("curse_stacks");
    private static final ResourceLocation FRAME = texture("curse_gauge_frame");
    private static final ResourceLocation DIAL = texture("curse_star_dial");

    private CurseStackHud() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "textures/gui/" + name + ".png");
    }

    public static void render(GuiGraphics graphics, int x, int y, boolean preview) {
        var view = ClientCurseStacks.view();
        float gain = ClientCurseStacks.gainAge(), complete = ClientCurseStacks.completeAge(), spent = ClientCurseStacks.spentAge();
        float opacity = ClientCurseStacks.opacity();
        if (preview) {
            long cycle = Util.getMillis() / 9000;
            int rank = (int) (cycle % 3) + 1;
            int max = 6 - rank;
            double t = (Util.getMillis() % 9000) / 1000.0;
            int count = Math.min(max, (int) t);
            var item = ModItems.CURSE_WEAPONS.get("the_harbinger");
            var id = item == null ? ForgeRegistries.ITEMS.getKey(Items.AMETHYST_SHARD) : item.getId();
            view = new ClientCurseStacks.View("preview", id.toString(), "the_harbinger",
                    "hud.madoka_magic.curse.omen", rank, count, max);
            gain = (float) (t - Math.floor(t));
            complete = (float) (t - max);
            spent = 100;
            opacity = 1;
        }
        if (view == null) return;
        var style = RitualVisualStyle.forTheme(view.theme());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            graphics.setColor(1, 1, 1, opacity);
            graphics.blit(FRAME, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
            graphics.blit(DIAL, 24, 21, 0, 0, 48, 48, 48, 48);
            for (int i = 0; i < view.max(); i++) {
                boolean filled = i < view.stacks();
                float pop = filled && i == view.stacks() - 1 && gain < 0.22f
                        ? 1 + 0.25f * (1 - gain / 0.22f) : 1;
                pose.pushPose();
                pose.translate(CurseGaugeLayout.x(i, view.max()), CurseGaugeLayout.y(i, view.max()), 0);
                if (!filled) pose.mulPose(Axis.ZP.rotation((float) (CurseGaugeLayout.angle(i, view.max()) + Math.PI / 2)));
                pose.scale(pop, pop, 1);
                tint(graphics, filled ? style.crown() : 0xa8a2ad, opacity);
                graphics.blit(ATLAS, -7, -9, 14, 18, filled ? 32 : 0, 0, 32, 32, 128, 32);
                pose.popPose();
            }
            if (view.ready()) {
                float progress = complete < 0 ? 1 : Mth.clamp(complete / 0.42f, 0, 1);
                seal(graphics, style.crown(), (1 - progress) * 8, opacity * (0.84f
                        + 0.12f * (float) Math.sin(Util.getMillis() / 550.0)));
                if (complete >= 0.36f && complete < 0.62f) {
                    float flash = (float) Math.sin((complete - 0.36f) / 0.26f * Math.PI);
                    tint(graphics, 0xffefd3, opacity * flash * 0.7f);
                    graphics.blit(ATLAS, 32, 29, 96, 0, 32, 32, 128, 32);
                }
            } else if (spent < 0.38f) {
                seal(graphics, style.crown(), spent / 0.38f * 10, opacity * (1 - spent / 0.38f));
            }
            graphics.setColor(1, 1, 1, 1);
            var font = Minecraft.getInstance().font;
            int alpha = Math.max(4, (int) (opacity * 255)) << 24;
            String counter = view.stacks() + "/" + view.max();
            graphics.drawString(font, counter, (WIDTH - font.width(counter)) / 2, 59, alpha | 0xffe8b7, true);
            Component label = Component.translatable(view.nameKey());
            graphics.drawString(font, label, (WIDTH - font.width(label)) / 2, 77, alpha | 0xe8dce9, true);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    private static void seal(GuiGraphics graphics, int color, float spread, float opacity) {
        tint(graphics, color, opacity);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                int dx = Math.round((column == 0 ? -1 : 1) * spread);
                int dy = Math.round((row == 0 ? -1 : 1) * spread);
                graphics.blit(ATLAS, 20 + column * 28 + dx, 17 + row * 28 + dy,
                        28, 28, 64 + column * 16, row * 16, 16, 16, 128, 32);
            }
        }
    }

    private static void tint(GuiGraphics graphics, int color, float opacity) {
        graphics.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, opacity);
    }
}
