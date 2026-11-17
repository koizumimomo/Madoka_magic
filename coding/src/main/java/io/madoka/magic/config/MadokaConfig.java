package io.madoka.magic.config;

import io.madoka.magic.item.ContractWeapon;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * 模组配置。
 *
 * <p>数值以设计提案 v2.0 为准，改这里不用重新编译。</p>
 */
public final class MadokaConfig {
    public record WeaponTuning(ForgeConfigSpec.DoubleValue damageBonus, ForgeConfigSpec.DoubleValue speedBonus,
                               ForgeConfigSpec.IntValue chargeTicks) {}

    private static final Map<ContractWeapon, WeaponTuning> CONTRACT_WEAPONS;
    public static final ForgeConfigSpec.DoubleValue WEAPON_GUARD_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue WEAPON_COUNTER_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue WEAPON_COUNTER_TICKS;

    public static final ForgeConfigSpec SPEC;

    // ---- 污染值 ----
    /** 每次施放法术增加的污染值。 */
    public static final ForgeConfigSpec.DoubleValue TAINT_PER_CAST;
    /** 每次受到敌对生物伤害增加的污染值。 */
    public static final ForgeConfigSpec.DoubleValue TAINT_PER_HIT;
    /** 每次普攻命中增加的污染值。 */
    public static final ForgeConfigSpec.DoubleValue TAINT_PER_MELEE;

    // ---- 悲叹之种 ----
    /** 每点耐久净化的污染值（耐久上限在物品注册时固定为 24，注册早于配置加载，无法走配置）。 */
    public static final ForgeConfigSpec.DoubleValue SEED_TAINT_PER_DURABILITY;

    // ---- 成长（纯度为百分比 0–100） ----
    /** 签约后的初始纯度（%）。 */
    public static final ForgeConfigSpec.DoubleValue INITIAL_PURITY;
    /** 每击杀多少只使魔提升 1% 纯度。 */
    public static final ForgeConfigSpec.IntValue FAMILIAR_KILLS_PER_PURITY;
    /** 击杀普通怪物获得的因果值。 */
    public static final ForgeConfigSpec.DoubleValue CAUSALITY_PER_MOB;
    /** 击杀魔女获得的灵魂宝石纯度（%）。 */
    public static final ForgeConfigSpec.DoubleValue PURITY_PER_WITCH;
    /** 每秒回复的魔力值。 */
    public static final ForgeConfigSpec.DoubleValue MANA_REGEN_PER_SECOND;
    public static final ForgeConfigSpec.IntValue GROWTH_STEP_PERCENT;
    public static final ForgeConfigSpec.DoubleValue GROWTH_HEALTH;
    public static final ForgeConfigSpec.DoubleValue GROWTH_FINAL_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue GROWTH_MAGIC_POWER;
    public static final ForgeConfigSpec.DoubleValue GROWTH_MANA;
    public static final ForgeConfigSpec.DoubleValue WITCH_GROWTH_HEALTH_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue FORM_HEALTH_BONUS;
    public static final ForgeConfigSpec.DoubleValue FORM_ATTACK_BONUS;
    public static final ForgeConfigSpec.DoubleValue FORM_ARMOR_BONUS;
    /** 击杀魔女获得的因果值。 */
    public static final ForgeConfigSpec.DoubleValue CAUSALITY_PER_WITCH;

    // ---- 魔女 ----
    public static final ForgeConfigSpec.IntValue WITCH_HEALTH;
    public static final ForgeConfigSpec.DoubleValue WITCH_DAMAGE;
    public static final ForgeConfigSpec.IntValue WITCH_XP;
    public static final ForgeConfigSpec.DoubleValue WITCH_COCOON_TRIGGER;
    public static final ForgeConfigSpec.IntValue WITCH_COCOON_TICKS;
    public static final ForgeConfigSpec.DoubleValue WITCH_ARENA_RADIUS;
    public static final ForgeConfigSpec.IntValue MOURNING_SKULL_INTERVAL;
    public static final ForgeConfigSpec.IntValue MOURNING_BLUE_SKULL_COOLDOWN;
    public static final ForgeConfigSpec.IntValue MOURNING_PATH_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue MOURNING_PATH_DAMAGE;
    public static final ForgeConfigSpec.IntValue MOURNING_ORBIT_COOLDOWN;
    public static final ForgeConfigSpec.DoubleValue MOURNING_ORBIT_DAMAGE;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_COOLDOWN;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_CHARGE;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_BEE_STEP;
    public static final ForgeConfigSpec.DoubleValue WITCH_ARENA_COCOON_HEALTH;
    public static final ForgeConfigSpec.DoubleValue WITCH_ARENA_BUTTERFLY_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue WITCH_ARENA_BEE_DAMAGE;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_CHARM_TICKS;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_CHARM_THRESHOLD;
    public static final ForgeConfigSpec.IntValue WITCH_ARENA_CHARM_SLOW_TICKS;
    public static final ForgeConfigSpec.DoubleValue WITCH_ARENA_CHARM_HEAL;
    public static final ForgeConfigSpec.DoubleValue WITCH_BEE_LINK_RADIUS;
    public static final ForgeConfigSpec.IntValue WITCH_RAIN_COOLDOWN;
    public static final ForgeConfigSpec.IntValue WITCH_RAIN_CHARGE;
    public static final ForgeConfigSpec.DoubleValue WITCH_RAIN_RADIUS;
    public static final ForgeConfigSpec.DoubleValue WITCH_RAIN_BANK_CAP;
    public static final ForgeConfigSpec.DoubleValue WITCH_RAIN_PULSE_DAMAGE;
    public static final ForgeConfigSpec.IntValue ATONEMENT_REST;
    public static final ForgeConfigSpec.IntValue ATONEMENT_SUMMON_WARNING;
    public static final ForgeConfigSpec.IntValue ATONEMENT_SUMMON_CAP;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_SUMMON_STAT_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue ATONEMENT_LINE_WARNING;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_LINE_REACH;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_LINE_HALF_WIDTH;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_LINE_DAMAGE;
    public static final ForgeConfigSpec.IntValue ATONEMENT_SHARD_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_SHARD_RANGE;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_SHARD_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue ATONEMENT_SHARD_SPREAD;
    public static final ForgeConfigSpec.IntValue SALVATION_CAST_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SALVATION_ARROW_RADIUS;
    public static final ForgeConfigSpec.IntValue SALVATION_ARROW_TICKS;
    public static final ForgeConfigSpec.IntValue SALVATION_ARROWS_PER_WAVE;
    public static final ForgeConfigSpec.DoubleValue SALVATION_ARROW_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SALVATION_HEAVEN_RADIUS;
    public static final ForgeConfigSpec.IntValue SALVATION_HEAVEN_WARNING;
    public static final ForgeConfigSpec.DoubleValue SALVATION_HEAVEN_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SALVATION_BEAM_RANGE;
    public static final ForgeConfigSpec.DoubleValue SALVATION_BEAM_RADIUS;
    public static final ForgeConfigSpec.IntValue SALVATION_BEAM_WARNING;
    public static final ForgeConfigSpec.IntValue SALVATION_BEAM_RANDOM_MARKS;
    public static final ForgeConfigSpec.DoubleValue SALVATION_BEAM_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SALVATION_CROSS_REACH;
    public static final ForgeConfigSpec.DoubleValue SALVATION_CROSS_HALF_WIDTH;
    public static final ForgeConfigSpec.IntValue SALVATION_CROSS_WARNING;
    public static final ForgeConfigSpec.DoubleValue SALVATION_CROSS_DAMAGE;
    public static final ForgeConfigSpec.IntValue SALVATION_MERCY_BOON_TICKS;
    public static final ForgeConfigSpec.IntValue SALVATION_MERCY_MARK_TICKS;
    public static final ForgeConfigSpec.IntValue OPHELIA_REST;
    public static final ForgeConfigSpec.IntValue OPHELIA_DASH_WARNING;
    public static final ForgeConfigSpec.DoubleValue OPHELIA_DASH_RANGE;
    public static final ForgeConfigSpec.DoubleValue OPHELIA_DASH_SPEED;
    public static final ForgeConfigSpec.DoubleValue OPHELIA_DASH_DAMAGE;
    public static final ForgeConfigSpec.IntValue NUTCRACKER_PROCESSION_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue NUTCRACKER_PROCESSION_RADIUS;
    public static final ForgeConfigSpec.IntValue NUTCRACKER_PROCESSION_WARNING;
    public static final ForgeConfigSpec.DoubleValue NUTCRACKER_PROCESSION_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue NUTCRACKER_CURSE_RADIUS;
    public static final ForgeConfigSpec.IntValue NUTCRACKER_CURSE_INTERVAL;
    public static final ForgeConfigSpec.IntValue NUTCRACKER_CURSE_DURATION;
    public static final ForgeConfigSpec.IntValue MERMAID_RECOVERY;
    public static final ForgeConfigSpec.DoubleValue MERMAID_WHEEL_RANGE;
    public static final ForgeConfigSpec.IntValue MERMAID_WHEEL_WARNING;
    public static final ForgeConfigSpec.DoubleValue MERMAID_WHEEL_SPEED;
    public static final ForgeConfigSpec.DoubleValue MERMAID_WHEEL_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MERMAID_SWEEP_RANGE;
    public static final ForgeConfigSpec.IntValue MERMAID_SWEEP_WARNING;
    public static final ForgeConfigSpec.DoubleValue MERMAID_SWEEP_DAMAGE;
    public static final ForgeConfigSpec.IntValue MERMAID_NOTE_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue MERMAID_NOTE_RANGE;
    public static final ForgeConfigSpec.DoubleValue MERMAID_NOTE_DAMAGE;
    public static final ForgeConfigSpec.IntValue MERMAID_CHORUS_WARNING;
    public static final ForgeConfigSpec.IntValue MERMAID_CHORUS_DURATION;
    public static final ForgeConfigSpec.IntValue MERMAID_CHORUS_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue MERMAID_CHORUS_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue GLUTTONY_SWEEP_RADIUS;
    public static final ForgeConfigSpec.DoubleValue GLUTTONY_SLAM_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_CAST_RANGE;
    public static final ForgeConfigSpec.IntValue CANDELORO_REST;
    public static final ForgeConfigSpec.IntValue CANDELORO_WARNING;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_CAKE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_CAKE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_FORK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_KNIFE_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_TEA_RADIUS;
    public static final ForgeConfigSpec.IntValue CANDELORO_TEA_DURATION;
    public static final ForgeConfigSpec.IntValue CANDELORO_TEA_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue CANDELORO_TEA_DAMAGE;
    public static final ForgeConfigSpec.IntValue NIGHT_SPAWN_INTERVAL;
    public static final ForgeConfigSpec.IntValue NIGHT_ATTACK_REST;
    public static final ForgeConfigSpec.IntValue NIGHT_GEAR_COUNT;
    public static final ForgeConfigSpec.DoubleValue NIGHT_GEAR_SPREAD;
    public static final ForgeConfigSpec.DoubleValue NIGHT_GEAR_RADIUS;
    public static final ForgeConfigSpec.DoubleValue NIGHT_GEAR_DAMAGE;
    public static final ForgeConfigSpec.IntValue NIGHT_LIGHTNING_WARNING;
    public static final ForgeConfigSpec.DoubleValue NIGHT_LIGHTNING_RADIUS;
    public static final ForgeConfigSpec.DoubleValue NIGHT_LIGHTNING_DAMAGE;
    public static final ForgeConfigSpec.IntValue NIGHT_FIREBALL_COUNT;

    // ---- 规则 ----
    /** 契约仪式是否需要祭坛（true = 需要把材料放进祭坛再点纸）。 */
    public static final ForgeConfigSpec.BooleanValue USE_ALTAR;
    /** 是否限制玩家只能使用自己解锁的技能。 */
    public static final ForgeConfigSpec.BooleanValue LOCK_TO_OWN_SPELLS;
    /** 契约材料是否只能从箱子获得。 */
    public static final ForgeConfigSpec.BooleanValue MATERIALS_FROM_CHESTS_ONLY;

    // ---- 阵营 ----
    /** 同阵营伤害减免系数（同阵营互相打，伤害乘这个值）。 */
    public static final ForgeConfigSpec.DoubleValue SAME_FACTION_DAMAGE_MULTIPLIER;
    /** 未签约玩家附近的眷属是否不生成（关掉则只做免伤，怪物照常出现但打不死）。 */
    public static final ForgeConfigSpec.BooleanValue FAMILIARS_HIDE_UNCONTRACTED;
    /** 眷属可见性判定半径（格）。 */
    public static final ForgeConfigSpec.DoubleValue FAMILIAR_CHECK_RADIUS;

    public static final ForgeConfigSpec.DoubleValue HALF_WITCH_DAMAGE_DEALT;
    public static final ForgeConfigSpec.DoubleValue HALF_WITCH_DAMAGE_TAKEN;

    // ---- 魔女结界传送门 ----
    /** 主世界入口随机刷新的检查间隔（tick）。 */
    public static final ForgeConfigSpec.IntValue PORTAL_CHECK_INTERVAL;
    /** 每次检查时每个签约玩家附近刷出入口的概率。 */
    public static final ForgeConfigSpec.DoubleValue PORTAL_SPAWN_CHANCE;
    /** 传送门存活时间（tick）。 */
    public static final ForgeConfigSpec.IntValue PORTAL_LIFETIME_TICKS;
    /** 触碰传送的判定半径（格，在碰撞箱外扩）。 */
    public static final ForgeConfigSpec.DoubleValue PORTAL_TRIGGER_RANGE;
    public static final ForgeConfigSpec.DoubleValue SPELL_RANGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SPELL_MAX_COOLDOWN_REDUCTION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("污染值").push("taint");
        TAINT_PER_CAST = builder
                .comment("每次施放法术增加的污染值")
                .defineInRange("taint_per_cast", 0.3D, 0.0D, 1000.0D);
        TAINT_PER_HIT = builder
                .comment("每次受到敌对生物伤害增加的污染值")
                .defineInRange("taint_per_hit", 3.0D, 0.0D, 1000.0D);
        TAINT_PER_MELEE = builder
                .comment("每次普攻命中增加的污染值")
                .defineInRange("taint_per_melee", 0.2D, 0.0D, 1000.0D);
        builder.pop();

        builder.comment("悲叹之种").push("tear");
        SEED_TAINT_PER_DURABILITY = builder
                .comment("悲叹之种每点耐久净化的污染值（耐久上限固定 24）")
                .defineInRange("seed_taint_per_durability", 1.0D, 0.0D, 100.0D);
        builder.pop();

        builder.comment("成长（纯度为百分比 0-100）").push("growth");
        INITIAL_PURITY = builder
                .comment("签约后的初始纯度（%）")
                .defineInRange("initial_purity", 10.0D, 0.0D, 100.0D);
        FAMILIAR_KILLS_PER_PURITY = builder
                .comment("每击杀多少只使魔提升 1% 纯度")
                .defineInRange("familiar_kills_per_purity", 10, 1, 1000);
        CAUSALITY_PER_MOB = builder
                .comment("击杀普通怪物获得的因果值")
                .defineInRange("causality_per_mob", 1.0D, 0.0D, 1000.0D);
        PURITY_PER_WITCH = builder
                .comment("击杀魔女获得的灵魂宝石纯度（%）")
                .defineInRange("purity_per_witch", 5.0D, 0.0D, 100.0D);
        MANA_REGEN_PER_SECOND = builder
                .comment("每秒回复的魔力值")
                .defineInRange("mana_regen_per_second", 5.0D, 0.0D, 1000.0D);
        GROWTH_STEP_PERCENT = builder.defineInRange("step_percent", 5, 1, 100);
        GROWTH_HEALTH = builder.defineInRange("health_per_step", 0.15D, 0.0D, 1.0D);
        GROWTH_FINAL_DAMAGE = builder.defineInRange("final_damage_per_step", 2.0D, 0.0D, 100.0D);
        GROWTH_MAGIC_POWER = builder.defineInRange("magic_power_per_step", 0.10D, 0.0D, 1.0D);
        GROWTH_MANA = builder.defineInRange("max_mana_per_step", 15.0D, 0.0D, 100.0D);
        WITCH_GROWTH_HEALTH_MULTIPLIER = builder.defineInRange("witch_health_multiplier", 2.0D, 0.0D, 5.0D);
        CAUSALITY_PER_WITCH = builder
                .comment("击杀魔女获得的因果值")
                .defineInRange("causality_per_witch", 100.0D, 0.0D, 100000.0D);
        builder.pop();

        builder.comment("变身形态的额外属性").push("transformation");
        FORM_HEALTH_BONUS = builder.defineInRange("max_health_bonus", 10.0D, 0.0D, 1024.0D);
        FORM_ATTACK_BONUS = builder.defineInRange("attack_damage_bonus", 3.0D, 0.0D, 2048.0D);
        FORM_ARMOR_BONUS = builder.defineInRange("armor_bonus", 4.0D, 0.0D, 30.0D);
        builder.pop();

        builder.comment("魔女").push("witch");
        WITCH_HEALTH = builder
                .comment("魔女默认生命值。各存档可在 serverconfig/madoka_magic-witches.toml 统一配置或按魔女覆盖。")
                .defineInRange("witch_health", 120, 1, Integer.MAX_VALUE);
        WITCH_DAMAGE = builder
                .comment("魔女伤害值")
                .defineInRange("witch_damage", 8.0D, 0.0D, 1000.0D);
        WITCH_XP = builder
                .comment("魔女给予的经验")
                .defineInRange("witch_xp", 200, 0, Integer.MAX_VALUE);
        WITCH_COCOON_TRIGGER = builder.defineInRange("butterfly_cocoon_health_ratio", 0.70D, 0.05D, 1.0D);
        WITCH_COCOON_TICKS = builder.defineInRange("butterfly_cocoon_max_ticks", 240, 20, 1200);
        WITCH_ARENA_RADIUS = builder.defineInRange("arena_skill_radius", 22.0D, 8.0D, 40.0D);
        WITCH_ARENA_COOLDOWN = builder.defineInRange("arena_skill_cooldown_ticks", 180, 60, 2400);
        WITCH_ARENA_CHARGE = builder.defineInRange("arena_cocoon_charge_ticks", 120, 60, 600);
        WITCH_ARENA_BEE_STEP = builder.defineInRange("queen_comb_blast_step_ticks", 22, 12, 100);
        WITCH_ARENA_COCOON_HEALTH = builder.defineInRange("arena_cocoon_health", 6.0D, 1.0D, 100.0D);
        WITCH_ARENA_BUTTERFLY_DAMAGE = builder.defineInRange("butterfly_arena_blast_damage", 8.0D, 1.0D, 60.0D);
        WITCH_ARENA_BEE_DAMAGE = builder.defineInRange("queen_arena_blast_damage", 5.0D, 1.0D, 60.0D);
        WITCH_ARENA_CHARM_TICKS = builder.defineInRange("queen_charm_memory_ticks", 760, 40, 4800);
        WITCH_ARENA_CHARM_THRESHOLD = builder.defineInRange("queen_charm_threshold", 3, 1, 10);
        WITCH_ARENA_CHARM_SLOW_TICKS = builder.defineInRange("queen_charm_slow_ticks", 60, 1, 300);
        WITCH_ARENA_CHARM_HEAL = builder.defineInRange("queen_charm_heal_ratio", 0.04D, 0.0D, 0.3D);
        WITCH_BEE_LINK_RADIUS = builder.defineInRange("bee_queen_link_radius", 32.0D, 4.0D, 64.0D);
        WITCH_RAIN_COOLDOWN = builder.defineInRange("sorrow_rain_cooldown_ticks", 240, 20, 2400);
        WITCH_RAIN_CHARGE = builder.defineInRange("sorrow_rain_charge_ticks", 100, 20, 600);
        WITCH_RAIN_RADIUS = builder.defineInRange("sorrow_rain_radius", 12.0D, 4.0D, 24.0D);
        WITCH_RAIN_BANK_CAP = builder.defineInRange("sorrow_rain_stored_damage_cap", 24.0D, 0.0D, 200.0D);
        WITCH_RAIN_PULSE_DAMAGE = builder.defineInRange("sorrow_rain_base_pulse_damage", 2.0D, 0.0D, 20.0D);
        builder.push("mourning");
        MOURNING_SKULL_INTERVAL = builder.comment("中央头沿用原版射击 AI 的发射间隔（刻）。")
                .defineInRange("skull_interval_ticks", 52, 20, 200);
        MOURNING_BLUE_SKULL_COOLDOWN = builder.comment("三颗头共用的蓝色颅骨冷却（刻）。")
                .defineInRange("blue_skull_cooldown_ticks", 110, 40, 400);
        MOURNING_PATH_COOLDOWN = builder.defineInRange("flower_path_cooldown_ticks", 170, 60, 1200);
        MOURNING_PATH_DAMAGE = builder.defineInRange("flower_path_damage", 6.0D, 0.0D, 60.0D);
        MOURNING_ORBIT_COOLDOWN = builder.defineInRange("skull_orbit_cooldown_ticks", 240, 90, 1200);
        MOURNING_ORBIT_DAMAGE = builder.defineInRange("skull_orbit_damage", 8.0D, 0.0D, 60.0D);
        builder.pop();
        builder.push("atonement");
        ATONEMENT_REST = builder.defineInRange("skill_rest_ticks", 100, 30, 600);
        ATONEMENT_SUMMON_WARNING = builder.defineInRange("summon_warning_ticks", 42, 20, 120);
        ATONEMENT_SUMMON_CAP = builder.defineInRange("nearby_summon_cap", 3, 2, 12);
        ATONEMENT_SUMMON_STAT_MULTIPLIER = builder.defineInRange("summon_stat_multiplier", 1.25D, 1.0D, 5.0D);
        ATONEMENT_LINE_WARNING = builder.defineInRange("line_warning_ticks", 38, 20, 120);
        ATONEMENT_LINE_REACH = builder.defineInRange("line_reach_each_side", 14.0D, 3.0D, 32.0D);
        ATONEMENT_LINE_HALF_WIDTH = builder.defineInRange("line_half_width", 1.35D, 0.4D, 4.0D);
        ATONEMENT_LINE_DAMAGE = builder.defineInRange("line_damage", 9.0D, 0.0D, 60.0D);
        ATONEMENT_SHARD_INTERVAL = builder.defineInRange("shard_interval_ticks", 30, 10, 120);
        ATONEMENT_SHARD_RANGE = builder.defineInRange("shard_range", 18.0D, 4.0D, 32.0D);
        ATONEMENT_SHARD_DAMAGE = builder.defineInRange("shard_damage", 4.0D, 0.0D, 32.0D);
        ATONEMENT_SHARD_SPREAD = builder.defineInRange("shard_inaccuracy", 12.0D, 0.0D, 32.0D);
        builder.pop();
        builder.push("salvation");
        SALVATION_CAST_INTERVAL = builder.defineInRange("rest_ticks", 70, 30, 1200);
        SALVATION_ARROW_RADIUS = builder.defineInRange("arrow_rain_radius", 20.0D, 6.0D, 40.0D);
        SALVATION_ARROW_TICKS = builder.defineInRange("arrow_rain_ticks", 100, 20, 300);
        SALVATION_ARROWS_PER_WAVE = builder.defineInRange("arrows_per_wave", 9, 1, 24);
        SALVATION_ARROW_DAMAGE = builder.defineInRange("arrow_damage", 4.0D, 0.0D, 40.0D);
        SALVATION_HEAVEN_RADIUS = builder.defineInRange("heaven_radius", 18.0D, 8.0D, 40.0D);
        SALVATION_HEAVEN_WARNING = builder.defineInRange("heaven_warning_ticks", 85, 30, 240);
        SALVATION_HEAVEN_DAMAGE = builder.defineInRange("heaven_damage", 10.0D, 0.0D, 60.0D);
        SALVATION_BEAM_RANGE = builder.defineInRange("beam_target_range", 32.0D, 8.0D, 48.0D);
        SALVATION_BEAM_RADIUS = builder.defineInRange("beam_impact_radius", 1.6D, 0.75D, 4.0D);
        SALVATION_BEAM_WARNING = builder.defineInRange("beam_warning_ticks", 32, 15, 120);
        SALVATION_BEAM_RANDOM_MARKS = builder.defineInRange("beam_random_marks", 11, 1, 32);
        SALVATION_BEAM_DAMAGE = builder.defineInRange("beam_damage", 8.0D, 0.0D, 60.0D);
        SALVATION_CROSS_REACH = builder.comment("十字射线从交点到各臂终点的距离（格）")
                .defineInRange("cross_reach", 15.0D, 4.0D, 28.0D);
        SALVATION_CROSS_HALF_WIDTH = builder.defineInRange("cross_half_width", 0.95D, 0.35D, 2.5D);
        SALVATION_CROSS_WARNING = builder.defineInRange("cross_warning_ticks", 40, 20, 120);
        SALVATION_CROSS_DAMAGE = builder.defineInRange("cross_damage", 9.0D, 0.0D, 60.0D);
        SALVATION_MERCY_BOON_TICKS = builder.defineInRange("mercy_boon_ticks", 160, 20, 1200);
        SALVATION_MERCY_MARK_TICKS = builder.defineInRange("mercy_mark_ticks", 900, 20, 7200);
        builder.pop();
        builder.push("nutcracker");
        NUTCRACKER_PROCESSION_INTERVAL = builder.defineInRange("procession_rest_ticks", 190, 60, 1200);
        NUTCRACKER_PROCESSION_RADIUS = builder.defineInRange("procession_half_width", 13.0D, 7.0D, 24.0D);
        NUTCRACKER_PROCESSION_WARNING = builder.defineInRange("procession_warning_ticks", 32, 20, 100);
        NUTCRACKER_PROCESSION_DAMAGE = builder.defineInRange("procession_damage", 5.0D, 0.0D, 40.0D);
        NUTCRACKER_CURSE_RADIUS = builder.defineInRange("curse_radius", 12.0D, 3.0D, 24.0D);
        NUTCRACKER_CURSE_INTERVAL = builder.defineInRange("curse_pulse_ticks", 40, 20, 120);
        NUTCRACKER_CURSE_DURATION = builder.defineInRange("curse_effect_ticks", 70, 20, 200);
        builder.pop();
        builder.push("mermaid");
        MERMAID_RECOVERY = builder.defineInRange("march_rest_ticks", 145, 40, 1200);
        MERMAID_WHEEL_RANGE = builder.defineInRange("wheel_field_half_width", 12.0D, 6.0D, 24.0D);
        MERMAID_WHEEL_WARNING = builder.defineInRange("wheel_warning_ticks", 24, 12, 100);
        MERMAID_WHEEL_SPEED = builder.defineInRange("wheel_blocks_per_tick", 0.72D, 0.25D, 1.0D);
        MERMAID_WHEEL_DAMAGE = builder.defineInRange("wheel_damage", 5.0D, 0.0D, 40.0D);
        MERMAID_SWEEP_RANGE = builder.defineInRange("sword_sweep_radius", 8.0D, 3.0D, 20.0D);
        MERMAID_SWEEP_WARNING = builder.defineInRange("sword_warning_ticks", 28, 14, 120);
        MERMAID_SWEEP_DAMAGE = builder.defineInRange("sword_damage", 9.0D, 0.0D, 60.0D);
        MERMAID_NOTE_INTERVAL = builder.defineInRange("note_attack_interval_ticks", 48, 15, 200);
        MERMAID_NOTE_RANGE = builder.defineInRange("note_attack_range", 16.0D, 5.0D, 20.0D);
        MERMAID_NOTE_DAMAGE = builder.defineInRange("note_attack_damage", 4.0D, 0.0D, 60.0D);
        MERMAID_CHORUS_WARNING = builder.defineInRange("chorus_warning_ticks", 24, 14, 120);
        MERMAID_CHORUS_DURATION = builder.defineInRange("chorus_duration_ticks", 100, 20, 200);
        MERMAID_CHORUS_INTERVAL = builder.defineInRange("chorus_wave_interval_ticks", 12, 6, 40);
        MERMAID_CHORUS_DAMAGE = builder.defineInRange("chorus_note_damage", 4.5D, 0.0D, 60.0D);
        builder.pop();
        builder.push("ophelia");
        OPHELIA_REST = builder.defineInRange("dash_rest_ticks", 100, 30, 1200);
        OPHELIA_DASH_WARNING = builder.defineInRange("dash_warning_ticks", 30, 18, 100);
        OPHELIA_DASH_RANGE = builder.defineInRange("dash_range", 19.0D, 6.0D, 28.0D);
        OPHELIA_DASH_SPEED = builder.defineInRange("dash_blocks_per_tick", 1.1D, 0.4D, 1.6D);
        OPHELIA_DASH_DAMAGE = builder.defineInRange("dash_damage", 10.0D, 0.0D, 60.0D);
        builder.pop();
        GLUTTONY_SWEEP_RADIUS = builder.defineInRange("gluttony_sweep_radius", 7.0D, 3.0D, 24.0D);
        GLUTTONY_SLAM_RADIUS = builder.defineInRange("gluttony_slam_radius", 5.0D, 3.0D, 24.0D);
        builder.push("candeloro");
        CANDELORO_CAST_RANGE = builder.defineInRange("cast_range", 21.0D, 8.0D, 38.0D);
        CANDELORO_REST = builder.defineInRange("rest_ticks", 78, 20, 1200);
        CANDELORO_WARNING = builder.defineInRange("warning_ticks", 34, 16, 120);
        CANDELORO_CAKE_RADIUS = builder.defineInRange("cake_slam_radius", 2.5D, 1.0D, 6.0D);
        CANDELORO_CAKE_DAMAGE = builder.defineInRange("cake_slam_damage", 7.0D, 1.0D, 60.0D);
        CANDELORO_FORK_DAMAGE = builder.defineInRange("fork_damage", 5.0D, 1.0D, 60.0D);
        CANDELORO_KNIFE_DAMAGE = builder.defineInRange("knife_damage", 8.0D, 1.0D, 60.0D);
        CANDELORO_TEA_RADIUS = builder.defineInRange("tea_stain_radius", 3.0D, 1.0D, 7.0D);
        CANDELORO_TEA_DURATION = builder.defineInRange("tea_duration_ticks", 100, 20, 300);
        CANDELORO_TEA_INTERVAL = builder.defineInRange("tea_pulse_ticks", 20, 10, 100);
        CANDELORO_TEA_DAMAGE = builder.defineInRange("tea_pulse_damage", 3.0D, 1.0D, 40.0D);
        builder.pop();
        builder.push("walpurgisnacht");
        NIGHT_SPAWN_INTERVAL = builder.comment("主世界自然登场间隔（游戏刻）。设为 0 可关闭自然登场。")
                .defineInRange("spawn_interval_ticks", 120000, 0, Integer.MAX_VALUE);
        NIGHT_ATTACK_REST = builder.defineInRange("rest_ticks", 62, 20, 1200);
        NIGHT_GEAR_COUNT = builder.defineInRange("gears_per_volley", 4, 1, 16);
        NIGHT_GEAR_SPREAD = builder.defineInRange("gear_landing_spread", 7.0D, 0.0D, 16.0D);
        NIGHT_GEAR_RADIUS = builder.defineInRange("gear_impact_radius", 2.5D, 0.5D, 8.0D);
        NIGHT_GEAR_DAMAGE = builder.defineInRange("gear_impact_damage", 9.0D, 0.0D, 64.0D);
        NIGHT_LIGHTNING_WARNING = builder.defineInRange("lightning_warning_ticks", 38, 15, 120);
        NIGHT_LIGHTNING_RADIUS = builder.defineInRange("lightning_radius", 2.7D, 0.75D, 8.0D);
        NIGHT_LIGHTNING_DAMAGE = builder.defineInRange("lightning_damage", 10.0D, 0.0D, 64.0D);
        NIGHT_FIREBALL_COUNT = builder.comment("连发总数，最后一发使用恶魂的爆炸火球。")
                .defineInRange("fireballs_per_volley", 10, 2, 24);
        builder.pop();
        builder.pop();

        builder.comment("阵营").push("faction");
        SAME_FACTION_DAMAGE_MULTIPLIER = builder
                .comment("同阵营伤害减免系数（同阵营互相打，伤害乘这个值）")
                .defineInRange("same_faction_damage_multiplier", 0.5D, 0.0D, 1.0D);
        FAMILIARS_HIDE_UNCONTRACTED = builder
                .comment("隐藏未曾签约的玩家视角里的使魔，并抑制无契约者附近的常规刷怪")
                .define("familiars_hide_uncontracted", true);
        FAMILIAR_CHECK_RADIUS = builder
                .comment("常规使魔刷怪寻找契约者的半径（格）")
                .defineInRange("familiar_check_radius", 64.0D, 8.0D, 256.0D);
        builder.pop();

        builder.comment("半魔女化").push("half_witch");
        HALF_WITCH_DAMAGE_DEALT = builder.comment("半魔女形态造成伤害的倍率")
                .defineInRange("damage_dealt", 0.85D, 0.05D, 1.0D);
        HALF_WITCH_DAMAGE_TAKEN = builder.comment("半魔女形态承受伤害的倍率")
                .defineInRange("damage_taken", 0.30D, 0.05D, 1.0D);
        builder.pop();

        builder.comment("魔女结界传送门").push("portal");
        PORTAL_CHECK_INTERVAL = builder
                .comment("主世界入口随机刷新的检查间隔（tick）")
                .defineInRange("portal_check_interval", 200, 20, 72000);
        PORTAL_SPAWN_CHANCE = builder
                .comment("每次检查时每个签约玩家附近刷出入口的概率")
                .defineInRange("portal_spawn_chance", 0.02D, 0.0D, 1.0D);
        PORTAL_LIFETIME_TICKS = builder
                .comment("传送门存活时间（tick）")
                .defineInRange("portal_lifetime_ticks", 2400, 200, Integer.MAX_VALUE);
        PORTAL_TRIGGER_RANGE = builder
                .comment("触碰传送的判定半径（格，在碰撞箱外扩）")
                .defineInRange("portal_trigger_range", 0.6D, 0.2D, 8.0D);
        builder.pop();

        builder.push("contract_weapons");
        var weapons = new EnumMap<ContractWeapon, WeaponTuning>(ContractWeapon.class);
        for (var weapon : ContractWeapon.values()) {
            builder.push(weapon.id);
            weapons.put(weapon, new WeaponTuning(
                    builder.defineInRange("attack_damage_bonus", weapon.defaultDamage, 0.0D, 60.0D),
                    builder.defineInRange("attack_speed_bonus", weapon.defaultSpeed, -3.9D, 8.0D),
                    builder.defineInRange("charge_ticks", weapon.defaultChargeTicks, 0, 100)));
            builder.pop();
        }
        CONTRACT_WEAPONS = Map.copyOf(weapons);
        WEAPON_GUARD_REDUCTION = builder.defineInRange("thorn_guard_reduction", 0.40D, 0.0D, 0.95D);
        WEAPON_COUNTER_MULTIPLIER = builder.defineInRange("thorn_counter_multiplier", 1.45D, 1.0D, 5.0D);
        WEAPON_COUNTER_TICKS = builder.defineInRange("thorn_counter_window_ticks", 60, 1, 200);
        builder.pop();

        builder.push("spells");
        SPELL_RANGE_MULTIPLIER = builder
                .comment("主动技能的射程、作用范围和对应特效倍率")
                .defineInRange("range_multiplier", 2.5D, 0.25D, 5.0D);
        SPELL_MAX_COOLDOWN_REDUCTION = builder
                .comment("魔法强度带来的主动技能冷却缩减上限（0.4 = 最多缩短 40%）")
                .defineInRange("max_cooldown_reduction", 0.4D, 0.0D, 1.0D);
        builder.pop();

        builder.comment("规则").push("rules");
        USE_ALTAR = builder
                .comment("是否需要祭坛进行契约仪式")
                .define("use_altar", true);
        LOCK_TO_OWN_SPELLS = builder
                .comment("魔法少女只能使用自己解锁的技能")
                .define("lock_to_own_spells", true);
        MATERIALS_FROM_CHESTS_ONLY = builder
                .comment("契约材料是否只从箱子获得（关掉则普通掉落物也能当材料）")
                .define("materials_from_chests_only", true);
        builder.pop();

        SPEC = builder.build();
    }

    public static WeaponTuning weapon(ContractWeapon weapon) {
        return CONTRACT_WEAPONS.get(weapon);
    }

    private MadokaConfig() {
    }
}
