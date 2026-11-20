package io.madoka.magic;

import io.madoka.magic.compat.CataclysmCompat;
import io.madoka.magic.compat.IronsCompat;
import io.madoka.magic.compat.IronsTaintEvents;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.config.WitchAttributeConfig;
import io.madoka.magic.event.PlayerEvents;
import io.madoka.magic.event.PortalEvents;
import io.madoka.magic.event.TaintEvents;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.registry.ModBlockEntities;
import io.madoka.magic.registry.ModBlocks;
import io.madoka.magic.registry.ModEntities;
import io.madoka.magic.registry.ModItems;
import io.madoka.magic.registry.ModParticles;
import io.madoka.magic.spell.SpellRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 魔法少女小圆模组主入口。
 *
 * <p>本模组是<b>独立模组</b>：硬依赖只有 GeckoLib（做模型用）。
 * Iron's Spells 'n Spellbooks 与 Curios 都是可选前置。</p>
 */
@Mod(MadokaMagic.MODID)
public class MadokaMagic {

    /** 必须与 gradle.properties 中的 mod_id 一致。 */
    public static final String MODID = "madoka_magic";

    public MadokaMagic(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        // 注册阶段的内容（物品、方块、方块实体、实体、属性）走 Mod 事件总线
        ModItems.register(modBus);
        io.madoka.magic.registry.ModCreativeTabs.register(modBus);
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModEntities.register(modBus);
        ModParticles.register(modBus);
        io.madoka.magic.registry.ModEffects.register(modBus);
        io.madoka.magic.ritual.AltarRecipe.register(modBus);
        MadokaAttributes.register(modBus);

        // 数据兜底：先从 mod 自己的 jar 里把定义读进来（保证任何环境都有数据），
        // 服务器启动时如果资源包里有同名定义，会被 reload 覆盖（可被资源包改写）。
        io.madoka.magic.data.MagicalGirlRegistry.replace(
                io.madoka.magic.data.MagicalGirlLoader.loadFromJar());
        io.madoka.magic.spell.SpellRegistry.replaceDefinitions(
                io.madoka.magic.spell.SpellDefinitionLoader.loadFromJar());
        io.madoka.magic.data.MagicalGirlRegistry.LOGGER.info(
                "[madoka_magic] loaded {} magical girl(s) and {} spell(s) from jar",
                io.madoka.magic.data.MagicalGirlRegistry.all().size(),
                io.madoka.magic.spell.SpellRegistry.all().size());

        // 数据加载器（魔法少女定义 / 技能定义）
        // AddReloadListenerEvent 是 Forge 总线事件（不是 mod 总线），照 Curios 的做法挂
        MinecraftForge.EVENT_BUS.addListener(io.madoka.magic.data.MadokaDataLoaders::addReloadListeners);
        // 祭坛仪式合成配方（data/madoka_magic/ritual_recipes/*.json）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.AddReloadListenerEvent event) ->
                event.addListener(new io.madoka.magic.ritual.RitualRecipes()));

        // 技能效果实现（技能元数据在 data/madoka_magic/spells/*.json）
        io.madoka.magic.spell.MadokaSpellEffects.bootstrap();
        io.madoka.magic.data.PassiveTree.bootstrap();
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.AddReloadListenerEvent event) ->
                event.addListener(new io.madoka.magic.data.PassiveTree.Loader()));

        // 网络
        MadokaNetwork.register();

        // 配置文件：run\config\madoka_magic-common.toml
        context.registerConfig(ModConfig.Type.COMMON, MadokaConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, WitchAttributeConfig.SPEC, WitchAttributeConfig.FILE_NAME);

        // 运行期的游戏逻辑
        MinecraftForge.EVENT_BUS.register(new TaintEvents());
        MinecraftForge.EVENT_BUS.register(new PlayerEvents());
        MinecraftForge.EVENT_BUS.register(new PortalEvents());
        // 双手紫水晶仪式：把去皮原木变成契约祭坛
        MinecraftForge.EVENT_BUS.register(new io.madoka.magic.event.AltarCraftEvents());

        // 可选兼容：只有玩家装了铁魔法，才注册依赖铁魔法事件类的监听器。
        // 本模组不装铁魔法也必须能正常加载。
        if (IronsCompat.isPresent()) {
            MinecraftForge.EVENT_BUS.register(new IronsTaintEvents());
        }
        // 可选兼容：灾变首领改为专属结界维度，并取消其原生成。
        if (CataclysmCompat.isPresent()) {
            MinecraftForge.EVENT_BUS.register(new CataclysmCompat.Events());
        }
    }

    /** Register the potted variants after the blocks are available. */
    @Mod.EventBusSubscriber(modid = MadokaMagic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void commonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                ModBlocks.registerPottedFlowers();
                // Curios 可选：没装就跳过，免得 CuriosSlotRegistration 类加载失败。
                if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                    io.madoka.magic.compat.CuriosSlotRegistration.register();
                }
            });
        }
    }
}
