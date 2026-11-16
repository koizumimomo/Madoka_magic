package io.madoka.magic.compat;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.util.MadokaMath;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 仅在安装了 Iron's Spells 'n Spellbooks 时才会被注册的监听器。
 *
 * <p>因为事件参数类型是铁魔法的类，本类<b>只有在
 * {@link IronsCompat#isPresent()} 为 true 时才允许被加载</b>，
 * 由 {@link io.madoka.magic.MadokaMagic} 在构造时按条件注册。</p>
 */
public class IronsTaintEvents {

    /** 铁魔法的法术也算"施法"：签约玩家释放时按配置叠加污染。 */
    @SubscribeEvent
    public void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return;
        }
        float amount = MadokaConfig.TAINT_PER_CAST.get().floatValue() * MadokaMath.taintRate(player);
        if (amount > 0.0F) {
            data.addTaint(amount);
            data.save(player);
            MadokaNetwork.syncTo(player);
        }
    }
}
