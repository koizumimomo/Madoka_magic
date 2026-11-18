package io.madoka.magic.compat;

import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.event.PortalEvents;
import io.madoka.magic.faction.WitchFamiliars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 灾变（L_Ender's Cataclysm）首领的结界维度兼容层。
 *
 * <p>灾变是<b>可选前置</b>：这里不 import 灾变的任何类，只按注册名查实体，
 * 未安装时所有入口都返回空，不会产生硬依赖或 NoClassDefFoundError。</p>
 *
 * <p>主题 id 与灾变实体注册名一致，维度 key 仍是
 * {@code madoka_magic:witch_<themeId>}，复用魔女结界的三套机制：
 * 结界传送门、专属维度、击杀后原地生成出口门。</p>
 *
 * <p>与原版魔女不同，灾变首领的入口<b>不随机生成</b>：结构里的祭坛/墓碑
 * 直接右键开门；从结构 NBT 或自然生成出现、没有祭坛的首领，在它原本的
 * 召唤位置补一个灾变的 {@code boss_respawner} 作为刷怪笼锚点，右键即可。</p>
 */
public final class CataclysmCompat {

    public static final String MODID = "cataclysm";

    private static final String RESPAWNER = "boss_respawner";

    /** 结界主题 → 灾变首领注册名；每只首领一个独立维度。 */
    private static final Map<String, String> BARRIER_BOSSES = Map.of(
            "netherite_monstrosity", "netherite_monstrosity",
            "ignis", "ignis",
            "ender_guardian", "ender_guardian",
            "the_harbinger", "the_harbinger",
            "the_leviathan", "the_leviathan",
            "amethyst_crab", "amethyst_crab",
            "ancient_remnant", "ancient_remnant",
            "maledictus", "maledictus",
            "scylla", "scylla");

    /** 所有会生成首领的实体注册名 → 主题（含战斗阶段变体）。 */
    private static final Map<String, String> ENTITY_THEMES = Map.ofEntries(
            Map.entry("netherite_monstrosity", "netherite_monstrosity"),
            Map.entry("old_netherite_monstrosity", "netherite_monstrosity"),
            Map.entry("ignis", "ignis"),
            Map.entry("ender_guardian", "ender_guardian"),
            Map.entry("the_harbinger", "the_harbinger"),
            Map.entry("the_leviathan", "the_leviathan"),
            Map.entry("amethyst_crab", "amethyst_crab"),
            Map.entry("ancient_remnant", "ancient_remnant"),
            Map.entry("ancient_ancient_remnant", "ancient_remnant"),
            Map.entry("maledictus", "maledictus"),
            Map.entry("scylla", "scylla"),
            Map.entry("scylla_ceraunus", "scylla"));

    /** 原本的召唤方块 → 主题；右键这些方块直接开固定位置的结界门。 */
    private static final Map<String, String> ANCHOR_BLOCKS = Map.of(
            "altar_of_fire", "ignis",
            "altar_of_void", "ender_guardian",
            "altar_of_abyss", "the_leviathan",
            "cursed_tombstone", "maledictus");

    /** 主题 → 使魔（注册名 + 数量）。下界合金巨兽与紫晶蟹没有附属小怪。 */
    private static final Map<String, List<Familiar>> FAMILIARS = Map.of(
            "ignis", List.of(new Familiar("ignited_revenant", 1), new Familiar("ignited_berserker", 1)),
            "ender_guardian", List.of(new Familiar("ender_golem", 1), new Familiar("endermaptera", 2)),
            "the_harbinger", List.of(new Familiar("the_watcher", 1), new Familiar("the_prowler", 1)),
            "the_leviathan", List.of(new Familiar("deepling", 1), new Familiar("deepling_brute", 1),
                    new Familiar("deepling_angler", 1), new Familiar("deepling_priest", 1),
                    new Familiar("deepling_warlock", 1), new Familiar("coralssus", 1, 0.35F)),
            "ancient_remnant", List.of(new Familiar("koboleton", 1), new Familiar("kobolediator", 1),
                    new Familiar("wadjet", 1)),
            "maledictus", List.of(new Familiar("draugr", 1), new Familiar("aptrgangr", 1),
                    new Familiar("royal_draugr", 1), new Familiar("elite_draugr", 1)),
            "scylla", List.of(new Familiar("hippocamtus", 1), new Familiar("clawdian", 1),
                    new Familiar("urchinkin", 1), new Familiar("drowned_host", 1),
                    new Familiar("symbiocto", 1)));

    /** 待补刷怪笼的锚点；世界生成可能在工作线程触发，用并发容器。 */
    private static final Queue<Anchor> PENDING_ANCHORS = new ConcurrentLinkedQueue<>();
    /** 已记录的锚点（按维度 + 主题去重），避免自然生成重复取材。 */
    private static final Map<String, List<BlockPos>> ANCHOR_INDEX = new HashMap<>();

    private CataclysmCompat() {
    }

    public static boolean isPresent() {
        return ModList.get().isLoaded(MODID);
    }

    public static boolean isBarrierTheme(String theme) {
        return theme != null && BARRIER_BOSSES.containsKey(theme);
    }

    /** 已安装灾变时可用作结界入口的主题；未安装返回空表。 */
    public static List<String> barrierThemes() {
        return isPresent() ? knownBarrierThemes() : List.of();
    }

    public static List<String> knownBarrierThemes() {
        return List.copyOf(BARRIER_BOSSES.keySet());
    }

    /** 该实体对应的结界主题；不是灾变首领返回空串。 */
    public static String barrierTheme(Entity entity) {
        if (!isPresent() || entity == null) {
            return "";
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MODID.equals(id.getNamespace()) ? themeForEntityPath(id.getPath()) : "";
    }

    /** 实体注册名（不含命名空间）→ 主题；给血条识别 {@code entity.cataclysm.*} 用。 */
    public static String themeForEntityPath(String path) {
        return path == null ? "" : ENTITY_THEMES.getOrDefault(path, "");
    }

    public static String familiarTheme(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!MODID.equals(id.getNamespace())) return "";
        return FAMILIARS.entrySet().stream().filter(entry -> entry.getValue().stream()
                        .anyMatch(familiar -> familiar.entityPath().equals(id.getPath())))
                .map(Map.Entry::getKey).findFirst().orElse("");
    }

    /** 在指定维度实例化对应首领；灾变缺失或主题未知时返回 null。 */
    public static Mob createBoss(ServerLevel level, String theme) {
        String path = BARRIER_BOSSES.get(theme);
        if (!isPresent() || path == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.fromNamespaceAndPath(MODID, path)).orElse(null);
        return type != null && type.create(level) instanceof Mob mob ? mob : null;
    }

    /**
     * 该生物是否属于此灾变首领的结界维度。
     * <p>只放行灾变自己的生物：首领召唤物、随从都是同一命名空间，
     * 其它模组的生物（包括魔女眷属）一律拦下。</p>
     */
    public static boolean belongsToBarrier(Entity entity, String theme) {
        if (!isPresent() || !isBarrierTheme(theme) || entity == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && MODID.equals(id.getNamespace());
    }

    /** 需要整片水域的竞技场（利维坦）。 */
    public static boolean isAquatic(String theme) {
        return "the_leviathan".equals(theme);
    }

    /** 首领出生高度：水生首领沉到水面下 10 格。 */
    public static int bossSpawnY(ServerLevel level, BlockPos anchor, int surfaceY, String theme) {
        if (!isAquatic(theme)) {
            return surfaceY;
        }
        return Math.max(level.getMinBuildHeight() + 12, surfaceY - 10);
    }

    /** 为水生竞技场在玩家落点铺一块海晶石平台（其余主题用维度自带地板）。 */
    public static void prepareArena(ServerLevel level, BlockPos start, int surfaceY, String theme) {
        if (!isAquatic(theme)) {
            return;
        }
        BlockState floor = Blocks.PRISMARINE.defaultBlockState();
        int y = surfaceY - 1;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlockAndUpdate(new BlockPos(start.getX() + dx, y, start.getZ() + dz), floor);
            }
        }
    }

    /**
     * 生成该首领的使魔。
     * <p>数量很少：每种 1 只（末影螨 2 只），珊瑚巨像与渊灵同场也只各 1 只。
     * 水生主题直接沉在首领附近的水里，其余主题落在竞技场地面。</p>
     */
    public static void spawnFamiliars(ServerLevel level, BlockPos arena, int bossY, String theme, UUID encounter) {
        List<Familiar> familiars = FAMILIARS.get(theme);
        if (familiars == null || familiars.isEmpty()) {
            return;
        }
        boolean aquatic = isAquatic(theme);
        for (Familiar familiar : familiars) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                    ResourceLocation.fromNamespaceAndPath(MODID, familiar.entityPath())).orElse(null);
            if (type == null) {
                continue;
            }
            for (int i = 0; i < familiar.count(); i++) {
                if (level.random.nextFloat() > familiar.chance()) {
                    continue;
                }
                if (!(type.create(level) instanceof Mob mob)) {
                    continue;
                }
                double angle = level.random.nextDouble() * Math.PI * 2.0D;
                double distance = 6.0D + level.random.nextDouble() * 10.0D;
                int x = arena.getX() + (int) Math.round(Math.cos(angle) * distance);
                int z = arena.getZ() + (int) Math.round(Math.sin(angle) * distance);
                level.getChunk(x >> 4, z >> 4);
                int y = aquatic
                        ? bossY + level.random.nextInt(7) - 3
                        : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                mob.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                mob.setPersistenceRequired();
                mob.getPersistentData().putUUID(WitchFamiliars.ENCOUNTER_TAG, encounter);
                level.addFreshEntity(mob);
            }
        }
    }

    // ------------------------------------------------------------ 锚点

    /**
     * 记录一个「首领原本会出现」的位置，稍后在服务端 tick 里补上刷怪笼。
     * <p>世界生成在工作线程里触发，不能直接放方块，所以先入队。
     * 同一维度、同一主题 64 格内只记一次，避免自然生成反复取材。</p>
     */
    private static void captureAnchor(ResourceKey<Level> dimension, BlockPos pos, String theme) {
        String entityPath = BARRIER_BOSSES.get(theme);
        if (entityPath == null) {
            return;
        }
        BlockPos anchor = pos.immutable();
        String key = dimension.location() + "|" + theme;
        synchronized (ANCHOR_INDEX) {
            List<BlockPos> known = ANCHOR_INDEX.computeIfAbsent(key, ignored -> new ArrayList<>());
            for (BlockPos existing : known) {
                if (existing.distSqr(anchor) <= 64.0D * 64.0D) {
                    return;
                }
            }
            known.add(anchor);
        }
        PENDING_ANCHORS.add(new Anchor(dimension, anchor, entityPath));
    }

    private static void flushAnchors(MinecraftServer server) {
        if (PENDING_ANCHORS.isEmpty()) {
            return;
        }
        for (Iterator<Anchor> iterator = PENDING_ANCHORS.iterator(); iterator.hasNext(); ) {
            Anchor anchor = iterator.next();
            ServerLevel level = server.getLevel(anchor.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (!level.hasChunkAt(anchor.pos())) {
                continue;
            }
            placeSpawner(level, anchor);
            iterator.remove();
        }
    }

    private static void placeSpawner(ServerLevel level, Anchor anchor) {
        BlockPos pos = anchor.pos();
        if (!replaceable(level, pos)) {
            if (!replaceable(level, pos.above())) {
                return;
            }
            pos = pos.above();
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(MODID, RESPAWNER));
        if (block == Blocks.AIR) {
            return;
        }
        BlockState state = block.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) {
            CompoundTag tag = new CompoundTag();
            tag.putString("EntityType", MODID + ":" + anchor.entityPath());
            // 原版刷怪笼右键空手也能激活；塞一个生存拿不到的隐形物品当钥匙，
            // 既防止绕过拦截，也不会在刷怪笼里显示突兀的方块。
            CompoundTag key = new CompoundTag();
            key.putString("id", "minecraft:structure_void");
            key.putByte("Count", (byte) 1);
            tag.put("Item", key);
            entity.load(tag);
            entity.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    private static boolean replaceable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /** 右键祭坛/刷怪笼 → 在该位置开一道固定入口门，不消耗任何祭品。 */
    private static String anchorTheme(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null || !MODID.equals(id.getNamespace())) {
            return "";
        }
        String theme = ANCHOR_BLOCKS.get(id.getPath());
        if (theme != null) {
            return theme;
        }
        if (!RESPAWNER.equals(id.getPath())) {
            return "";
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return "";
        }
        CompoundTag tag = entity.saveWithoutMetadata();
        ResourceLocation spawn = ResourceLocation.tryParse(tag.getString("EntityType"));
        return spawn == null || !MODID.equals(spawn.getNamespace()) ? "" : themeForEntityPath(spawn.getPath());
    }

    /** 使魔配置；chance 用来让珊瑚巨像这类「更加少量」的成员偶尔缺席。 */
    private record Familiar(String entityPath, int count, float chance) {
        Familiar(String entityPath, int count) {
            this(entityPath, count, 1.0F);
        }
    }

    private record Anchor(ResourceKey<Level> dimension, BlockPos pos, String entityPath) {
    }

    /**
     * 取消灾变首领的原生成：结构、自然生成、刷怪蛋、/summon 都不再产生首领，
     * 只有它自己的结界维度例外。
     */
    public static final class Events {

        /** 世界生成与刷怪蛋走 finalizeSpawn；取消后 WorldGenRegion 会拒绝加入实体。 */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            String theme = barrierTheme(event.getEntity());
            if (theme.isEmpty()) {
                return;
            }
            ServerLevel level = event.getLevel().getLevel();
            if (level.dimension().equals(MadokaDimensions.witchLevel(theme))) {
                return;
            }
            event.setSpawnCancelled(true);
            event.setCanceled(true);
            // 结构/NBT 首领原本的位置没有祭坛，记下来补刷怪笼。
            if (event.getSpawnType() == MobSpawnType.STRUCTURE || event.getSpawnType() == MobSpawnType.NATURAL) {
                captureAnchor(level.dimension(),
                        BlockPos.containing(event.getX(), event.getY(), event.getZ()), theme);
            }
        }

        /** /summon 等直接加入世界的路径不走 finalizeSpawn，这里补一道。 */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
                return;
            }
            String theme = barrierTheme(mob);
            if (theme.isEmpty()) {
                return;
            }
            if (event.getLevel().dimension().equals(MadokaDimensions.witchLevel(theme))) {
                return;
            }
            event.setCanceled(true);
        }

        /** 右键灾变祭坛/墓碑/重生刷怪笼：拦住原版用途，改为固定位置的结界入口。 */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)
                    || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            String theme = anchorTheme(level, event.getPos());
            if (theme.isEmpty()) {
                return;
            }
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
            PortalEvents.openFixedEntrance(level, player, event.getPos().above(), theme);
        }

        /** 世界生成时记录的位置，等区块加载完成后再补刷怪笼。 */
        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            flushAnchors(event.getServer());
        }
    }
}
