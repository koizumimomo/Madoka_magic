package io.madoka.magic.spell;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能注册表。
 *
 * <p><b>元数据来自 JSON</b>（{@code data/madoka_magic/spells/*.json}），
 * 行为实现来自 {@link SpellEffects}。</p>
 */
public final class SpellRegistry {

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, SpellDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private SpellRegistry() {
    }

    public static void replaceDefinitions(Map<String, SpellDefinition> loaded) {
        // 先放新值再删旧键：clear+putAll 会让并发读方看到空表
        DEFINITIONS.putAll(loaded);
        DEFINITIONS.keySet().retainAll(loaded.keySet());
    }

    public static SpellDefinition get(String id) {
        return DEFINITIONS.get(id);
    }

    public static Collection<SpellDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }

    /** 主动技能（进轮盘）。 */
    public static List<SpellDefinition> active() {
        List<SpellDefinition> result = new ArrayList<>();
        for (SpellDefinition def : DEFINITIONS.values()) {
            if (def.active()) {
                result.add(def);
            }
        }
        return result;
    }

    /** 被动技能。 */
    public static List<SpellDefinition> passive() {
        List<SpellDefinition> result = new ArrayList<>();
        for (SpellDefinition def : DEFINITIONS.values()) {
            if (!def.active()) {
                result.add(def);
            }
        }
        return result;
    }

    /**
     * 按等级自动解锁的技能 id（被动技能不进轮盘，不在此列）。
     * <p><b>不带角色过滤</b>的版本，仅供没有角色定义的场合使用。</p>
     */
    public static List<String> defaultUnlocked(int level) {
        List<String> unlocked = new ArrayList<>();
        for (SpellDefinition def : active()) {
            if (level >= def.unlockLevel()) {
                unlocked.add(def.id());
            }
        }
        return unlocked;
    }

    /**
     * 按<b>角色的技能表</b>解锁主动技能。
     *
     * <p>每位魔法少女有自己的技能列表（{@code magical_girls/*.json} 的 {@code skills}），
     * 别的角色的技能不应该出现在轮盘里。</p>
     *
     * @param level 当前等级
     * @param theme 当前宝石对应的角色定义；为 null 时不解锁任何技能
     */
    public static List<String> defaultUnlocked(int level, io.madoka.magic.data.MagicalGirl theme, boolean witch) {
        List<String> unlocked = new ArrayList<>();
        if (theme == null) {
            return unlocked;
        }
        for (String id : theme.getSkills(witch)) {
            SpellDefinition def = DEFINITIONS.get(id);
            if (def == null || !def.active()) {
                continue;
            }
            if (level >= def.unlockLevel()) {
                unlocked.add(id);
            }
        }
        return unlocked;
    }

    /**
     * 某位魔法少女拥有的被动技能定义。
     *
     * <p>被动按<b>角色</b>过滤：献身系的"献身之翼"不会对索取系玩家生效。</p>
     */
    public static List<SpellDefinition> passivesOf(io.madoka.magic.data.PlayerMadokaData data) {
        List<SpellDefinition> result = new ArrayList<>();
        var theme = data.getTheme();
        if (theme == null) {
            return result;
        }
        for (String id : theme.getPassives(data.isWitchPath())) {
            SpellDefinition def = DEFINITIONS.get(id);
            if (def != null && !def.active()) {
                result.add(def);
            }
        }
        return result;
    }

    /** 施放一个技能（服务端）。返回效果是否真正生效。 */
    public static boolean cast(ServerPlayer caster, SpellDefinition def) {
        SpellEffects.ActiveEffect effect = SpellEffects.active(def.type());
        if (effect == null) {
            LOGGER.warn("No active effect registered for spell type '{}' (skill {})", def.type(), def.id());
            return false;
        }
        return effect.execute(caster);
    }

    /** 应用被动效果。 */
    public static void applyPassive(ServerPlayer player, SpellDefinition def) {
        SpellEffects.PassiveEffect effect = SpellEffects.passive(def.type());
        if (effect != null) {
            effect.apply(player);
        }
    }

    /** 移除被动效果。 */
    public static void removePassive(ServerPlayer player, SpellDefinition def) {
        SpellEffects.PassiveEffect effect = SpellEffects.passive(def.type());
        if (effect != null) {
            effect.remove(player);
        }
    }
}
