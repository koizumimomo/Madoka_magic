package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.dimension.MadokaDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * Alex 的洞穴三魔女的刷新规则。
 *
 * <ul>
 *   <li><b>封禁原版生成</b>：暝煌龙、遗弃者、甘草女巫在非结界维度一律不再
 *       以本体形式出现（甘草女巫原版刷得很多，不封会把世界填满门）；</li>
 *   <li><b>可驯服生物</b>：恐龙、糖果独角兽、射线猫等不接入结界体系，
 *       原栖息地保留原版生成、驯服与骑乘玩法；</li>
 *   <li><b>随机入口</b>：玩家身处对应洞穴生物群系时，按固定概率刷出该魔女的
 *       结界入口传送门——三位概率一致，甘草女巫不再因刷得多而更常见。</li>
 * </ul>
 *
 * <p>入口复用 {@link PortalEvents#openNearbyEntrance}（8–16 格随机地面，
 * 自动回收同名玩家旧入口），生成本体仍走结界维度内的 createBoss。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class AlexCavesSpawnEvents {
    private AlexCavesSpawnEvents() {}

    /** 洞穴生物群系注册名（不含命名空间）→ 结界主题。三位概率一致。 */
    private static final Map<String, String> BIOME_THEMES = Map.of(
            "primordial_caves", "brunhilde",
            "forlorn_hollows", "sieglinde",
            "candy_cavity", "annegret");

    /** 玩家身处对应洞穴时，每秒刷出入口门的概率分母（1/N，N=600 即均值 10 分钟）。 */
    private static final int SPAWN_CHANCE = 600;

    /**
     * 封禁三位首领在非结界维度的所有生成（含区块读档、刷怪蛋、结构）。
     * 可驯服生物不属于结界体系，保留原版生成与驯服玩法。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!AlexCavesCompat.isPresent() || event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        String theme = AlexCavesCompat.barrierTheme(entity);
        if (!theme.isEmpty()
                && !event.getLevel().dimension().equals(MadokaDimensions.witchLevel(theme))) {
            event.setCanceled(true);
        }
    }

    /**
     * 对应洞穴内的随机入口刷新。每秒掷一次骰子，
     * 命中且身处三种洞穴之一时开门。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.level().isClientSide
                || !AlexCavesCompat.isPresent()
                || !MadokaDimensions.barrierTheme(player.level().dimension()).isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // 按玩家 id 错开检查 tick，全体不同帧掷骰
        if (level.getGameTime() % 20 != player.getId() % 20
                || level.random.nextInt(SPAWN_CHANCE) != 0) {
            return;
        }
        String theme = themeAt(level, player.blockPosition());
        if (!theme.isEmpty()) {
            PortalEvents.openNearbyEntrance(level, player, theme);
        }
    }

    /** 玩家所处位置对应的结界主题；不在三种洞穴里返回空串。 */
    private static String themeAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).unwrapKey()
                .map(key -> {
                    ResourceLocation id = key.location();
                    return AlexCavesCompat.MODID.equals(id.getNamespace())
                            ? BIOME_THEMES.getOrDefault(id.getPath(), "") : "";
                })
                .orElse("");
    }

    /** 供交接文档与调试用：某实体是否为被封禁的洞穴首领。 */
    public static boolean isBannedBoss(Entity entity) {
        return AlexCavesCompat.isPresent() && !AlexCavesCompat.barrierTheme(entity).isEmpty();
    }
}
