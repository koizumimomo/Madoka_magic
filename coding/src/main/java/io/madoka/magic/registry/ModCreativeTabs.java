package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.WitchKissItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 创造模式标签栏。 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MadokaMagic.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.madoka_magic"))
                    .icon(() -> new ItemStack(ModItems.CONTRACTS.get("sacrifice").get()))
                    .displayItems((params, output) -> {
                        ModItems.CONTRACTS.values().forEach(item -> output.accept(item.get()));
                        ModItems.GRIEF_SEEDS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.WITCH_KISS.get());
                        WitchKissItem.THEMES.stream()
                                .filter(theme -> WitchTypes.NIGHT.equals(theme) || MadokaDimensions.hasBarrier(theme))
                                .forEach(theme -> output.accept(WitchKissItem.fromWitch(theme)));
                        // 契约材料
                        output.accept(ModItems.BLACK_TULIP.get());
                        output.accept(ModItems.HONEY_BOTTLE.get());
                        output.accept(ModItems.ERYNGIUM.get());
                        // 祭坛
                        output.accept(ModItems.ALTAR_ITEM.get());
                        output.accept(ModItems.DISPLAY_RING.get());
                        output.accept(ModItems.TEST_RELEASE_CONTRACT.get());
                        output.accept(ModItems.TEST_MAX_PURITY.get());
                        output.accept(ModItems.TEST_WITCH_PORTAL.get());
                        output.accept(ModItems.TEST_WITCH_FORM.get());
                        output.accept(ModItems.TEST_RAISE_TAINT.get());
                        ModItems.CURSE_WEAPONS.values().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
