package io.madoka.magic.client;

import io.madoka.magic.MadokaMagic;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class NightArrivalOverlay {
    private static final long DARK_MILLIS = 330;
    private static final long FADE_MILLIS = 420;
    private static long started = -1;

    private NightArrivalOverlay() {}

    public static void start() {
        started = Util.getMillis();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && started >= 0 && Util.getMillis() - started >= DARK_MILLIS + FADE_MILLIS) {
            started = -1;
            var client = Minecraft.getInstance();
            client.gui.setTimes(6, 46, 14);
            client.gui.setTitle(Component.translatable("event.madoka_magic.walpurgis_arrival"));
            client.gui.setSubtitle(Component.empty());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        started = -1;
    }

    static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        if (started >= 0) {
            long elapsed = Util.getMillis() - started;
            float fade = Mth.clamp((elapsed - DARK_MILLIS) / (float) FADE_MILLIS, 0.0F, 1.0F);
            int alpha = Math.round(255 * (1 - fade));
            graphics.fill(0, 0, width, height, alpha << 24);
        }
    }
}
