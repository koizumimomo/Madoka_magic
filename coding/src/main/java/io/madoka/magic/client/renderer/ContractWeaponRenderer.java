package io.madoka.magic.client.renderer;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.ClientMadokaState;
import io.madoka.magic.event.ContractWeaponEvents.Input;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.network.MadokaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 契约武器的输入处理：手持契约武器右键蓄力/格挡。
 *
 * <p>武器本身是真实物品，由原版渲染；这里只负责把右键意图发给服务端。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class ContractWeaponRenderer {
    private static int useSlot = -1;
    private static boolean waitForRelease;
    /** 折页右键直发斩击的按住去重。 */
    private static boolean castLatch;

    private ContractWeaponRenderer() {}

    private static ContractWeapon weapon(AbstractClientPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        ContractWeapon held = ContractWeapon.of(player.getMainHandItem());
        if (held == null) {
            return null;
        }
        if (player == Minecraft.getInstance().player) {
            return ClientMadokaState.isContracted() && ClientMadokaState.isTransformed()
                    && ContractWeapon.forTheme(ClientMadokaState.getThemeId()) == held ? held : null;
        }
        return ContractWeapon.forTheme(TransformationVisuals.magicalGirlTheme(player)) == held ? held : null;
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        var client = Minecraft.getInstance();
        var player = client.player;
        if (weapon(player) == null || !player.getOffhandItem().isEmpty() || player.isShiftKeyDown()) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
        if (weapon(player) == ContractWeapon.BUTTERFLY || ClientMadokaState.isWitchPath()) {
            // 折页/魔女途：右键直接施放当前形态首个技能，可空放，不需要蓄力
            if (!castLatch) {
                castLatch = true;
                send(Input.CAST);
                player.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        if (!waitForRelease) {
            useSlot = player.getInventory().selected;
            waitForRelease = true;
            send(Input.START);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var client = Minecraft.getInstance();
        // 折页直发斩击：松开右键即复位，与蓄力流程（waitForRelease）无关
        if (castLatch && !client.options.keyUse.isDown()) {
            castLatch = false;
        }
        if (!waitForRelease) {
            return;
        }
        var player = client.player;
        if (useSlot >= 0 && (client.screen != null || client.getOverlay() != null || !client.isWindowActive()
                || weapon(player) == null || !player.getOffhandItem().isEmpty() || player.getInventory().selected != useSlot)) {
            useSlot = -1;
            if (client.getConnection() != null) {
                send(Input.CANCEL);
            }
        }
        if (!client.options.keyUse.isDown()) {
            castLatch = false;
            if (useSlot >= 0 && client.getConnection() != null) {
                send(Input.RELEASE);
                if (weapon(player) != ContractWeapon.THORN) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
            useSlot = -1;
            waitForRelease = false;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        useSlot = -1;
        waitForRelease = false;
        castLatch = false;
    }

    private static void send(Input input) {
        MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.WeaponInput(input));
    }
}
