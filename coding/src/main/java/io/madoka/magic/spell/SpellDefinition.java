package io.madoka.magic.spell;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 一个技能的**元数据定义**，全部来自 JSON
 * （{@code data/madoka_magic/spells/<id>.json}）。
 *
 * <p>代码里只保留「效果实现」；名字、图标、冷却、魔力消耗、解锁等级都在 JSON 里，
 * 加一个新技能只要丢一个 JSON（若需要新行为，再加一段效果实现并注册）。</p>
 *
 * @param id             唯一 id（文件名）
 * @param type           效果实现的注册名（在代码里注册，见 {@link SpellEffects}）
 * @param active         true = 主动技能（进轮盘）；false = 被动
 * @param nameKey        名称翻译键
 * @param descKey        描述翻译键
 * @param icon           图标贴图
 * @param cooldownSecond 冷却（秒）
 * @param manaCost       施法消耗的魔力
 * @param taintOnCast    魔力不足时改加的污染
 * @param unlockLevel    解锁所需等级
 * @param parameters     技能特有的数值参数
 */
public record SpellDefinition(
        String id,
        String type,
        boolean active,
        String nameKey,
        String descKey,
        ResourceLocation icon,
        int cooldownSecond,
        float manaCost,
        float taintOnCast,
        int unlockLevel,
        Map<String, Double> parameters) {

    public SpellDefinition {
        parameters = Map.copyOf(parameters);
    }

    public double parameter(String key) {
        return parameters.getOrDefault(key, 0.0D);
    }

    public int ticks(String key) {
        return (int) Math.min(Integer.MAX_VALUE, parameter(key) * 20.0D);
    }

    public int cooldownTicks() {
        return (int) Math.min(Math.max(0, cooldownSecond) * 20L, Integer.MAX_VALUE);
    }
}
