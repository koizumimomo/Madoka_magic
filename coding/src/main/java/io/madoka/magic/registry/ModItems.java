package io.madoka.magic.registry;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.ContractItem;
import io.madoka.magic.item.ContractWeapon;
import io.madoka.magic.item.ContractWeaponItem;
import io.madoka.magic.item.CreativeTestItem;
import io.madoka.magic.item.SoulGemItem;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.item.WishMaterialItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 物品注册表。
 *
 * <ul>
 *   <li>{@code contract} 契约 —— 由祭坛仪式产出，右键后确认成为魔法少女</li>
 *   <li>{@code grief_seed} 悲叹之种 —— 净化污染值</li>
 * </ul>
 */
public final class ModItems {

    // The item registry is populated before the common config is loaded.
    private static final int GRIEF_SEED_DURABILITY = 24;

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MadokaMagic.MODID);

    public static final RegistryObject<Item> CONTRACT =
            ITEMS.register("contract", () -> new ContractItem(new Item.Properties().stacksTo(1)));

    // Legacy registration keeps old inventory and chest stacks readable.
    public static final RegistryObject<Item> SOUL_GEM =
            ITEMS.register("soul_gem", () -> new SoulGemItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> GRIEF_SEED =
            ITEMS.register("grief_seed", () -> new GriefSeedItem(new Item.Properties().durability(GRIEF_SEED_DURABILITY)));

    public static final Map<String, RegistryObject<Item>> CONTRACTS = registerVariants("contract_",
            List.of("sacrifice", "take", "atonement"),
            theme -> new ContractItem(new Item.Properties().stacksTo(1), theme));

    public static final Map<ContractWeapon, RegistryObject<Item>> CONTRACT_WEAPONS = registerContractWeapons();

    private static Map<ContractWeapon, RegistryObject<Item>> registerContractWeapons() {
        var weapons = new EnumMap<ContractWeapon, RegistryObject<Item>>(ContractWeapon.class);
        for (ContractWeapon weapon : ContractWeapon.values()) {
            weapons.put(weapon, ITEMS.register(weapon.id,
                    () -> new ContractWeaponItem(new Item.Properties().stacksTo(1))));
        }
        return Collections.unmodifiableMap(weapons);
    }

    public static final RegistryObject<Item> WISH_BLACK_TULIP = ITEMS.register("wish_black_tulip", WishMaterialItem::new);
    public static final RegistryObject<Item> WISH_HONEY_BOTTLE = ITEMS.register("wish_honey_bottle", WishMaterialItem::new);
    public static final RegistryObject<Item> WISH_ERYNGIUM = ITEMS.register("wish_eryngium", WishMaterialItem::new);

    public static final Map<String, RegistryObject<Item>> GRIEF_SEEDS = registerVariants("grief_seed_",
            List.of("sacrifice", "take", "atonement", "homulilly",
                    "candeloro", "oktavia", "ophelia", "walpurgisnacht", "wither", WitchTypes.GLUTTONY,
                    "netherite_monstrosity", "ignis", "ender_guardian", "the_harbinger", "the_leviathan",
                    "amethyst_crab", "ancient_remnant", "maledictus", "scylla"),
            theme -> new GriefSeedItem(new Item.Properties().durability(GRIEF_SEED_DURABILITY), theme));

    private static Map<String, RegistryObject<Item>> registerVariants(String prefix, List<String> themes,
                                                                     Function<String, Item> factory) {
        Map<String, RegistryObject<Item>> variants = new LinkedHashMap<>();
        for (String theme : themes) {
            variants.put(theme, ITEMS.register(prefix + theme, () -> factory.apply(theme)));
        }
        return Collections.unmodifiableMap(variants);
    }

    /** 首次击败对应种类魔女的收藏纪念品。 */
    public static final RegistryObject<Item> WITCH_KISS =
            ITEMS.register("witch_kiss", () -> new io.madoka.magic.item.WitchKissItem(new Item.Properties().stacksTo(16)));

    /** 黑色郁金香：献身系的契约材料。 */
    public static final RegistryObject<Item> BLACK_TULIP =
            ITEMS.register("black_tulip", () -> new BlockItem(ModBlocks.BLACK_TULIP.get(), new Item.Properties()));

    /** 蜂蜜瓶：索取系的契约材料。 */
    public static final RegistryObject<Item> HONEY_BOTTLE =
            ITEMS.register("honey_bottle", () -> new io.madoka.magic.item.HoneyBottleItem(new Item.Properties().stacksTo(16)));

    /** 蓝刺芹：赎罪系（蓝刺芹系魔法少女）的契约材料。 */
    public static final RegistryObject<Item> ERYNGIUM =
            ITEMS.register("eryngium", () -> new BlockItem(ModBlocks.ERYNGIUM.get(), new Item.Properties()));

    public static final RegistryObject<Item> ALTAR_ITEM = ITEMS.register("altar",
            () -> new BlockItem(ModBlocks.ALTAR.get(), new Item.Properties()));

    public static final RegistryObject<Item> DISPLAY_RING = ITEMS.register("display_ring",
            () -> new BlockItem(ModBlocks.DISPLAY_RING.get(), new Item.Properties()));

    public static final RegistryObject<Item> TEST_RELEASE_CONTRACT = ITEMS.register("test_release_contract",
            () -> new CreativeTestItem(CreativeTestItem.Action.RELEASE_CONTRACT));
    public static final RegistryObject<Item> TEST_MAX_PURITY = ITEMS.register("test_max_purity",
            () -> new CreativeTestItem(CreativeTestItem.Action.MAX_PURITY));
    public static final RegistryObject<Item> TEST_WITCH_PORTAL = ITEMS.register("test_witch_portal",
            () -> new io.madoka.magic.item.WitchPortalTestItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
