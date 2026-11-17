package io.madoka.magic.client;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.client.particle.DevotionParticle;
import io.madoka.magic.client.renderer.BlackBeeRenderer;
import io.madoka.magic.client.renderer.AtonementRenderers;
import io.madoka.magic.client.renderer.GluttonyWitchRenderer;
import io.madoka.magic.client.renderer.MadokaWitchRenderer;
import io.madoka.magic.client.renderer.MagicalGirlAccessories;
import io.madoka.magic.client.renderer.MourningSkullOrbitRenderer;
import io.madoka.magic.client.renderer.RainThornRenderer;
import io.madoka.magic.client.renderer.WitchPortalRenderer;
import io.madoka.magic.client.renderer.LightArrowRenderer;
import io.madoka.magic.client.renderer.MermaidWheelModel;
import io.madoka.magic.client.renderer.MermaidNoteRenderer;
import io.madoka.magic.client.renderer.CandeloroCombatRenderers;
import io.madoka.magic.client.renderer.NightGearRenderer;
import io.madoka.magic.client.renderer.WitherWitchRenderer;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.List;

/** 客户端入口。 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MadokaClient {

    private MadokaClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(MadokaKeys.OPEN_WHEEL);
        event.register(MadokaKeys.OPEN_BARRIER);
        event.register(MadokaKeys.TOGGLE_TRANSFORMATION);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        DevotionParticle.register(event);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        MadokaWitchRenderer.registerVisualReload(event);
    }

    /** 注册渲染器（祭坛悬浮材料、魔女、结界传送门）。 */
    @SubscribeEvent
    public static void onAddLayers(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) {
        MagicalGirlAccessories.addLayers(event);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MermaidWheelModel.MODEL, MermaidWheelModel::createLayer);
        event.registerLayerDefinition(NightGearRenderer.MODEL, NightGearRenderer::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
        CandeloroCombatRenderers.registerModels(event);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                io.madoka.magic.registry.ModBlockEntities.ALTAR.get(),
                context -> new AltarBlockEntityRenderer());
        event.registerEntityRenderer(
                io.madoka.magic.registry.ModEntities.WITCH.get(),
                MadokaWitchRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.BUTTERFLY_PRINCESS.get(),
                MadokaWitchRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.BLACK_BEE.get(), BlackBeeRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.RAIN_THORN.get(), RainThornRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.LIGHT_ARROW.get(), LightArrowRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.FRAME_SHARD.get(), AtonementRenderers.Shard::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.NIGHT_GEAR.get(), NightGearRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.MERMAID_NOTE.get(), MermaidNoteRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.MOURNING_SKULL_ORBIT.get(), MourningSkullOrbitRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.DESSERT_FAMILIAR.get(), CandeloroCombatRenderers.Dessert::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.CANDELORO_CUTLERY.get(), CandeloroCombatRenderers.Cutlery::new);
        event.registerEntityRenderer(EntityType.WITHER, WitherWitchRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.MOURNING_ZOMBIE.get(), AtonementRenderers.MourningZombie::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.MOURNING_SKELETON.get(), AtonementRenderers.MourningSkeleton::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.ARENA_COCOON.get(),
                io.madoka.magic.client.renderer.ArenaCocoonRenderer::new);
        event.registerEntityRenderer(io.madoka.magic.registry.ModEntities.GLUTTONY_WITCH.get(), GluttonyWitchRenderer::new);
        event.registerEntityRenderer(
                io.madoka.magic.registry.ModEntities.WITCH_PORTAL.get(),
                WitchPortalRenderer::new);
    }

    /** 注册 HUD：左下宝石污染 + 饥饿上方魔力条。 */
    @SubscribeEvent
    public static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.VIGNETTE.id(), "taint_vignette", MadokaHud::renderTaintVignette);
        event.registerAboveAll("madoka_hud", MadokaHud::render);
        event.registerAboveAll("walpurgis_arrival", NightArrivalOverlay::render);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            List<String> contracts = List.copyOf(ModItems.CONTRACTS.keySet());
            List<String> seeds = List.copyOf(ModItems.GRIEF_SEEDS.keySet());
            ItemProperties.register(ModItems.CONTRACT.get(),
                    ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "contract_seal"),
                    (stack, level, entity, seed) -> contracts.indexOf(ContractItem.getThemeId(stack)) + 1);
            ItemProperties.register(ModItems.GRIEF_SEED.get(),
                    ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID, "witch_rune"),
                    (stack, level, entity, seed) -> seeds.indexOf(GriefSeedItem.getOwner(stack)) + 1);
        });
    }

    /** 打开契约确认界面。 */
    public static void openContractConfirm(ItemStack contract) {
        if (!ClientMadokaState.isContracted()) {
            Minecraft.getInstance().setScreen(new ContractConfirmScreen(contract));
        }
    }

    /** 打开魔女化后的去留选择。 */
    public static void openWitchChoice() {
        Minecraft.getInstance().setScreen(new WitchChoiceScreen());
    }

    /** 打开结界入口测试工具的魔女选择界面。 */
    public static void openWitchPortalTest() {
        Minecraft.getInstance().setScreen(new WitchPortalTestScreen());
    }
}
