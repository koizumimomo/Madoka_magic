package io.madoka.magic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.ClientMadokaState;
import io.madoka.magic.event.ContractWeaponEvents.Input;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT)
public final class ContractWeaponRenderer {
    private static final Map<ContractWeapon, ItemStack> STACKS = new EnumMap<>(ContractWeapon.class);
    private static int useSlot = -1;
    private static boolean waitForRelease;

    private ContractWeaponRenderer() {}

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new HeldWeaponLayer(renderer));
            }
        }
    }

    private static ContractWeapon weapon(AbstractClientPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty()) {
            return null;
        }
        if (player == Minecraft.getInstance().player) {
            return ClientMadokaState.isContracted() && ClientMadokaState.isTransformed() && !ClientMadokaState.isWitchPath()
                    ? ContractWeapon.forTheme(ClientMadokaState.getThemeId()) : null;
        }
        return ContractWeapon.forTheme(TransformationVisuals.magicalGirlTheme(player));
    }

    private static ItemStack stack(ContractWeapon weapon) {
        return STACKS.computeIfAbsent(weapon, type -> new ItemStack(ModItems.CONTRACT_WEAPONS.get(type).get()));
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
        if (!waitForRelease) {
            useSlot = player.getInventory().selected;
            waitForRelease = true;
            send(Input.START);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !waitForRelease) {
            return;
        }
        var client = Minecraft.getInstance();
        var player = client.player;
        if (useSlot >= 0 && (client.screen != null || client.getOverlay() != null || !client.isWindowActive()
                || weapon(player) == null || !player.getOffhandItem().isEmpty() || player.getInventory().selected != useSlot)) {
            useSlot = -1;
            if (client.getConnection() != null) {
                send(Input.CANCEL);
            }
        }
        if (!client.options.keyUse.isDown()) {
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
    }

    private static void send(Input input) {
        MadokaNetwork.CHANNEL.sendToServer(new MadokaNetwork.WeaponInput(input));
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()) {
            return;
        }
        ContractWeapon weapon = weapon(player);
        if (weapon == null) {
            return;
        }
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? -1.0F : 1.0F;
        float swing = event.getSwingProgress();
        float sweep = Mth.sin(Mth.sqrt(swing) * Mth.PI);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * (0.47F - 0.4F * sweep),
                -0.52F - 0.6F * event.getEquipProgress() + 0.2F * Mth.sin(Mth.sqrt(swing) * Mth.TWO_PI),
                -0.83F - 0.2F * Mth.sin(swing * Mth.PI));
        pose.mulPose(Axis.YP.rotationDegrees(side * 24.0F * (sweep - Mth.sin(swing * swing * Mth.PI))));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -20.0F * sweep));
        pose.mulPose(Axis.XP.rotationDegrees(-80.0F * sweep));
        if (useSlot >= 0) {
            pose.translate(-side * 0.16F, 0.10F, 0.06F);
            pose.mulPose(Axis.ZP.rotationDegrees(side * (weapon == ContractWeapon.THORN ? 50 : 24)));
        }
        client.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack(weapon),
                left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                left, pose, event.getMultiBufferSource(), weaponLight(event.getPackedLight()));
        pose.popPose();
        event.setCanceled(true);
    }

    private static int weaponLight(int light) {
        return LightTexture.pack(Math.max(8, LightTexture.block(light)), LightTexture.sky(light));
    }

    private static final class HeldWeaponLayer extends ItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        HeldWeaponLayer(PlayerRenderer renderer) {
            super(renderer, Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer());
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
            ContractWeapon weapon = weapon(player);
            if (weapon == null || player.isInvisibleTo(Minecraft.getInstance().player)) {
                return;
            }
            var arm = player.getMainArm();
            pose.pushPose();
            if (getParentModel().young) {
                pose.translate(0, 0.75F, 0);
                pose.scale(0.5F, 0.5F, 0.5F);
            }
            renderArmWithItem(player, stack(weapon), arm == HumanoidArm.LEFT
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    arm, pose, buffers, weaponLight(light));
            pose.popPose();
        }
    }
}
