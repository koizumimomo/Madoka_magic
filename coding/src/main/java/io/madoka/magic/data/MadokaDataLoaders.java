package io.madoka.magic.data;

import io.madoka.magic.spell.SpellDefinitionLoader;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * 把数据加载器挂到资源重载流程上。
 *
 * <p>注意：1.20.1 的 {@code AddReloadListenerEvent} 既不是 {@code IModBusEvent}，
 * 用 {@code @Mod.EventBusSubscriber} 会被 Forge 的自动注册检查拒绝
 * （"takes an argument that is not a subtype of the base type"），
 * 所以这里改成在主类里<b>编程式</b>挂到 mod 总线与 Forge 总线，
 * 哪边 fire 都能接住。</p>
 */
public final class MadokaDataLoaders {

    private MadokaDataLoaders() {
    }

    /** 注册所有数据加载器。由主类在构造时挂到 Forge 总线（与 Curios 的做法一致）。 */
    public static void addReloadListeners(AddReloadListenerEvent event) {
        io.madoka.magic.data.MagicalGirlRegistry.LOGGER.info("[madoka_magic] reload listener event received");
        event.addListener(new MagicalGirlLoader());
        event.addListener(new SpellDefinitionLoader());
        event.addListener(new CurseStackProfiles.Loader());
        event.addListener(new OriginalCurseDefinitions.Loader());
    }
}
