package io.madoka.magic.event;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.compat.AlexCavesCompat;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.WitchPortalEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 拦截 Alex 的洞穴首领的自然生成：三位首领（暝煌龙、遗弃者、甘草女巫）
 * 在非结界维度尝试生成时不再以原样出现，原地生成对应魔女的结界入口传送门。
 *
 * <p>与凋零拦截同构：仅拦生成，不拦区块读档；
 * 结界维度内部的首领本体不受影响。</p>
 */
@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class AlexCavesSpawnEvents {
    private AlexCavesSpawnEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBossSpawn(EntityJoinLevelEvent event) {
        if (!AlexCavesCompat.isPresent() || event.loadedFromDisk() || event.getLevel().isClientSide()
                || !MadokaDimensions.barrierTheme(event.getLevel().dimension()).isEmpty()) {
            return;
        }
        String theme = AlexCavesCompat.barrierTheme(event.getEntity());
        if (theme.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (event.getLevel() instanceof ServerLevel level) {
            WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_ENTRANCE, theme, null,
                    event.getEntity().blockPosition());
        }
    }
}
