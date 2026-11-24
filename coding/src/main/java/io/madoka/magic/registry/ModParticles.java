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
    public static final RegistryObject<SimpleParticleType> MOURNING_FOOTPRINT = TYPES.register(
            "mourning_footprint", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> MOURNING_ROSE = TYPES.register(
            "mourning_rose", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus bus) {
        // Initialize the enums before attaching their deferred registrations.
        Devotion.values();
        Take.values();
        Witch.values();
        CircleStreak.values();
        TYPES.register(bus);
    }

    /** 变身法阵的上升光点：对应颜色的单像素短线条，每系一枚。 */
    public enum CircleStreak {
        DEVOTION("circle_streak_devotion"), ATONEMENT("circle_streak_atonement"), TAKE("circle_streak_take");

        private final RegistryObject<SimpleParticleType> particle;

        CircleStreak(String id) {
            particle = TYPES.register(id, () -> new SimpleParticleType(false));
        }

        public SimpleParticleType get() {
            return particle.get();
        }
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

    /** 魔女途径技能的紫黑色粒子。 */
    public enum Witch {
        SLASH("witch_slash"), MOTE("witch_mote"), MARK("witch_mark"),
        VENOM("witch_venom"), PULSE("witch_pulse"), TEAR("witch_tear");

        private final RegistryObject<SimpleParticleType> particle;

        Witch(String id) {
            particle = TYPES.register(id, () -> new SimpleParticleType(false));
        }

        public SimpleParticleType get() {
            return particle.get();
        }
    }
}
