package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MadokaMagic.MODID);

    public static final RegistryObject<MobEffect> MERCY_MARK = EFFECTS.register("mercy_mark",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xefa7cd) {});

    private ModEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }
}
