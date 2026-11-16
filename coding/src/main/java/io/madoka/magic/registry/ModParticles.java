package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MadokaMagic.MODID);

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        // Initialize the enum before attaching its deferred registrations.
        Devotion.values();
        Take.values();
        TYPES.register(bus);
    }

    public enum Devotion {
        SLASH("butterfly_slash"),
        BUTTERFLY("butterfly_mote"),
        MARK("butterfly_mark"),
        TULIP("tulip_burst"),
        POLLEN("pollen_pulse"),
        BLOOM("blossom_open"),
        CLOSE("blossom_close"),
        FALL("blossom_fall"),
        PETAL("tulip_petal"),
        WITHER_LIFE("wither_life"),
        WITHER_TAINT("wither_taint");

        private final RegistryObject<SimpleParticleType> particle;

        Devotion(String id) {
            particle = TYPES.register(id, () -> new SimpleParticleType(false));
        }

        public SimpleParticleType get() {
            return particle.get();
        }
    }

    public enum Take {
        HONEY("honey_mote"), SEED("dandelion_seed"), ILLUSION("honey_illusion");

        private final RegistryObject<SimpleParticleType> particle;

        Take(String id) {
            particle = TYPES.register(id, () -> new SimpleParticleType(false));
        }

        public SimpleParticleType get() {
            return particle.get();
        }
    }
}
