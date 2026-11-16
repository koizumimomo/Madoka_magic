package io.madoka.magic.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 客户端每 tick 的处理：打开轮盘、请求同步、弹出魔女化选择。 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var client = Minecraft.getInstance();
        // G 键请求半魔女化。
        while (MadokaKeys.OPEN_BARRIER.consumeClick()) {
            if (ClientMadokaState.isTransformed() && client.screen == null) {
                MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.OpenBarrier());
            }
        }
        // 魔女化后的去留选择
        if (ClientMadokaState.isPendingWitchChoice()
                && client.screen == null) {
            MadokaClient.openWitchChoice();
        }
    }

    @SubscribeEvent
    public static void onInventoryInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventory) {
            event.addListener(MagicalGirlInfoScreen.inventoryTab(inventory));
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen inventory) {
            event.addListener(MagicalGirlInfoScreen.inventoryTab(inventory));
        }
    }

    @SubscribeEvent
    public static void onWheelKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            var key = InputConstants.getKey(event.getKey(), event.getScanCode());
            if (MadokaKeys.TOGGLE_TRANSFORMATION.isActiveAndMatches(key)) {
                toggleTransformation();
            } else if (MadokaKeys.OPEN_WHEEL.isActiveAndMatches(key)) {
                openWheel();
            }
        }
    }

    @SubscribeEvent
    public static void onWheelMouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
            if (MadokaKeys.TOGGLE_TRANSFORMATION.isActiveAndMatches(key)) {
                event.setCanceled(toggleTransformation());
            } else if (MadokaKeys.OPEN_WHEEL.isActiveAndMatches(key) && openWheel()) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean toggleTransformation() {
        var client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator() || client.screen != null
                || client.getOverlay() != null || !client.isWindowActive() || !ClientMadokaState.isContracted()) {
            return false;
        }
        MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.ToggleTransformation());
        return true;
    }

    private static boolean openWheel() {
        var client = Minecraft.getInstance();
        if (client.player == null || !client.player.isAlive() || client.player.isSpectator() || client.screen != null
                || client.getOverlay() != null || !client.isWindowActive() || !ClientMadokaState.isTransformed()) {
            return false;
        }
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsWheelCompat.consumeSharedPress();
        }
        client.setScreen(new SpellWheelScreen());
        return true;
    }

    @SubscribeEvent
    public static void onLoggedIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.RequestSync());
    }

    /** 退出世界/换服务器时清掉客户端缓存的状态。 */
    @SubscribeEvent
    public static void onLoggedOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMadokaState.reset();
    }
}
