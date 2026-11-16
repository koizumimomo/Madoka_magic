package io.madoka.magic.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 魔法少女的玩家数据。
 *
 * <p>存在 {@link Player#getPersistentData()} 里（Forge 的实体持久化 NBT），
 * 服务端权威。客户端需要的那部分通过 {@link io.madoka.magic.network.MadokaNetwork} 同步。</p>
 *
 * <p><b>污染值不是属性</b>。魔力值、纯度、因果值、魔法强度是实体属性
 * （{@link io.madoka.magic.registry.MadokaAttributes}），可以被装备与效果改；
 * 污染值与"当前魔力"需要状态语义，所以放在这里。</p>
 */
public class PlayerMadokaData {

    /** 存于 player persistent data 的键。 */
    public static final String KEY = "madoka_magic";

    /** 污染值上限，满值即魔女化。 */
    public static final float TAINT_MAX = 100.0F;
    /** 污染值达到这个数可以半魔女化。 */
    public static final float HALF_WITCH_THRESHOLD = 60.0F;
    public static final float TAINT_WARNING_THRESHOLD = 90.0F;
    /** 脱战判定：5 秒内没受到来自生物的伤害即视为战斗结束。 */
    public static final int COMBAT_TIMEOUT_TICKS = 5 * 20;

    /** 花期阶段。 */
    public enum BlossomPhase {
        /** 未开启。 */
        NONE,
        /** 绽放：伤害加成随战斗持续时间增长。 */
        BLOOM,
        /** 花落：加成消失、属性下降、污染上升，直到战斗结束。 */
        FALL
    }

    /**
     * 污染值。
     * <p>用小数存储：设计里「施法 +0.3」「普攻 +0.2」是小于 1 的增量，
     * 用 int 存会被四舍五入抹成 0。</p>
     */
    private float taint;
    private int halfWitchTicks;
    private long witchDeathTick = -1L;
    private int hardcoreRespawnMode = -1;
    /** 当前魔力（施法资源）。 */
    private double mana;
    private double despair = -1.0D;
    private int level = 1;
    private String themeId = "";
    private boolean contracted;
    private boolean transformed;
    private boolean witchPath;
    private boolean everContracted;
    private boolean keepIdentity;
    private boolean pendingWitchChoice;
    /** 最近一次同步的魔女方向（相对玩家朝向的角度，单位：度），供经验条指示器使用。 */
    private float witchDirection = Float.NaN;
    private float witchDistance = -1.0F;
    private final Set<String> unlockedSpells = new LinkedHashSet<>();
    private final Set<String> defeatedWitches = new LinkedHashSet<>();
    private final List<ItemStack> pendingWitchRewards = new ArrayList<>();

    // ---- 战斗状态（花期的判定依据） ----
    /** 最后一次受到来自生物的伤害时的游戏时间；-1 表示从未进入战斗。 */
    private long lastCombatTick = -1L;
    /** 本次战斗开始时的游戏时间。 */
    private long combatStartTick = -1L;

    // ---- 花期状态 ----
    /** 花期阶段。 */
    private BlossomPhase blossomPhase = BlossomPhase.NONE;
    /** 进入绽放阶段时的游戏时间。 */
    private long blossomStartTick = -1L;
    /** 是否已经花落过（花落后属性下降是否仍然生效）。 */
    private boolean weakened;

    // ---- 执念（通用被动） ----
    /** 执念层数。 */
    private int obsessionStacks;
    /** 最近一次击杀的时间，用于层数衰减。 */
    private long lastKillTick = -1L;

    // ---- 击杀计数（纯度节奏） ----
    /** 使魔击杀计数：每满 {@code MadokaConfig.FAMILIAR_KILLS_PER_PURITY} 只，纯度 +1% 并清零。 */
    private int familiarKillCount;

    // ---- 无法偿还的约定（赎罪系 C-4） ----
    /** 约定目标的 UUID 字符串；空串表示没有约定。 */
    private String promiseTarget = "";
    /** 约定到期的游戏时间。 */
    private long promiseEndTick = -1L;
    /** 约定池（期间被减免、准备返还的伤害量）。 */
    private float promisePool;

    /** 脱战后保留的索取之瞳加成。临时战斗加成由服务端独立结算。 */
    private StolenStats selfishEye = StolenStats.ZERO;
    private StolenStats pendingSelfishEye = StolenStats.ZERO;
    private float selfishEyeAbsorption;

    public record StolenStats(double attack, double health, double mana) {
        public static final StolenStats ZERO = new StolenStats(0.0D, 0.0D, 0.0D);

        public StolenStats plus(StolenStats other) {
            return new StolenStats(attack + other.attack, health + other.health, mana + other.mana);
        }

        public StolenStats scale(double ratio) {
            return new StolenStats(attack * ratio, health * ratio, mana * ratio);
        }

        private CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putDouble("attack", attack);
            tag.putDouble("health", health);
            tag.putDouble("mana", mana);
            return tag;
        }

        private static StolenStats load(CompoundTag tag) {
            return new StolenStats(nonNegative(tag.getDouble("attack")),
                    nonNegative(tag.getDouble("health")), nonNegative(tag.getDouble("mana")));
        }

        private static double nonNegative(double amount) {
            return Double.isFinite(amount) ? Math.max(0.0D, amount) : 0.0D;
        }
    }

    // ---------------------------------------------------------------- 读写

    /**
     * 按 tick 缓存的快照（WeakHashMap：玩家实体回收时条目自动失效）。
     *
     * <p>伤害事件等热路径每 tick 会多次调用 {@link #of(Player)}，若每次都全量反序列化 NBT
     * 开销很大；而且多个调用方各自「读快照 → 改 → save」会后写覆盖先写。
     * 同一 tick 内共享同一份快照可以同时解决这两个问题（同 tick 内的修改彼此可见）。</p>
     */
    private static final java.util.WeakHashMap<Player, CachedData> CACHE = new java.util.WeakHashMap<>();

    private record CachedData(long gameTime, PlayerMadokaData data) {
    }

    public static PlayerMadokaData of(Player player) {
        long gameTime = player.level().getGameTime();
        CachedData cached = CACHE.get(player);
        if (cached != null && cached.gameTime() == gameTime) {
            return cached.data();
        }
        CompoundTag tag = player.getPersistentData();
        PlayerMadokaData data = new PlayerMadokaData();
        if (tag.contains(KEY, Tag.TAG_COMPOUND)) {
            data.load(tag.getCompound(KEY));
        }
        CACHE.put(player, new CachedData(gameTime, data));
        return data;
    }

    /** 取玩家数据并写回（用于修改后调用 {@link #save(Player)}）。 */
    public static PlayerMadokaData get(Player player) {
        PlayerMadokaData data = of(player);
        data.save(player);
        return data;
    }

    public void save(Player player) {
        player.getPersistentData().put(KEY, serialize());
    }

    /** 序列化成 NBT（复制数据用，例如死亡重生）。 */
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        if (!pendingWitchRewards.isEmpty()) {
            var rewards = new ListTag();
            pendingWitchRewards.forEach(stack -> rewards.add(stack.save(new CompoundTag())));
            tag.put("witchRewards", rewards);
        }
        tag.putFloat("taint", taint);
        if (halfWitchTicks > 0) {
            tag.putInt("halfWitchTicks", halfWitchTicks);
        }
        if (witchDeathTick >= 0) {
            tag.putLong("witchDeathTick", witchDeathTick);
        }
        if (hardcoreRespawnMode >= 0) {
            tag.putInt("hardcoreRespawnMode", hardcoreRespawnMode);
        }
        tag.putDouble("mana", mana);
        if (despair >= 0.0D) {
            tag.putDouble("despair", despair);
        }
        tag.putInt("level", level);
        tag.putString("theme", themeId);
        tag.putBoolean("contracted", contracted);
        tag.putBoolean("transformed", transformed);
        tag.putBoolean("witchPath", witchPath);
        tag.putBoolean("ever", everContracted);
        tag.putBoolean("keep", keepIdentity);
        tag.putBoolean("pending", pendingWitchChoice);
        tag.putFloat("witchDir", witchDirection);
        tag.putFloat("witchDist", witchDistance);
        tag.putLong("lastCombat", lastCombatTick);
        tag.putLong("combatStart", combatStartTick);
        tag.putString("blossom", blossomPhase.name());
        tag.putLong("blossomStart", blossomStartTick);
        tag.putBoolean("weakened", weakened);
        tag.putInt("obsStacks", obsessionStacks);
        tag.putLong("lastKill", lastKillTick);
        tag.putInt("familiarKills", familiarKillCount);
        tag.putString("promiseTarget", promiseTarget);
        tag.putLong("promiseEnd", promiseEndTick);
        tag.putFloat("promisePool", promisePool);
        if (!selfishEye.equals(StolenStats.ZERO)) {
            tag.put("selfishEye", selfishEye.serialize());
        }
        if (hasPendingSelfishEye()) {
            tag.put("pendingSelfishEye", pendingSelfishEye.serialize());
            tag.putFloat("selfishEyeAbsorption", selfishEyeAbsorption);
        }
        ListTag list = new ListTag();
        for (String id : unlockedSpells) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("spells", list);
        ListTag witches = new ListTag();
        defeatedWitches.forEach(id -> witches.add(StringTag.valueOf(id)));
        tag.put("defeatedWitches", witches);
        return tag;
    }

    public void load(CompoundTag tag) {
        pendingWitchRewards.clear();
        var rewards = tag.getList("witchRewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < rewards.size(); i++) {
            ItemStack stack = ItemStack.of(rewards.getCompound(i));
            if (!stack.isEmpty()) {
                pendingWitchRewards.add(stack);
            }
        }
        taint = tag.getFloat("taint");
        mana = tag.getDouble("mana");
        despair = -1.0D;
        if (tag.contains("despair", Tag.TAG_ANY_NUMERIC)) {
            setDespair(tag.getDouble("despair"));
        }
        level = Math.max(1, tag.getInt("level"));
        themeId = tag.getString("theme");
        contracted = tag.getBoolean("contracted");
        transformed = contracted && tag.getBoolean("transformed");
        witchPath = contracted && tag.getBoolean("witchPath");
        halfWitchTicks = transformed && !witchPath ? Math.max(0, tag.getInt("halfWitchTicks")) : 0;
        witchDeathTick = tag.contains("witchDeathTick", Tag.TAG_LONG) ? tag.getLong("witchDeathTick") : -1L;
        hardcoreRespawnMode = tag.contains("hardcoreRespawnMode", Tag.TAG_INT) ? tag.getInt("hardcoreRespawnMode") : -1;
        everContracted = tag.getBoolean("ever");
        keepIdentity = tag.getBoolean("keep");
        pendingWitchChoice = tag.getBoolean("pending");
        witchDirection = tag.getFloat("witchDir");
        witchDistance = tag.getFloat("witchDist");
        lastCombatTick = tag.contains("lastCombat") ? tag.getLong("lastCombat") : -1L;
        combatStartTick = tag.contains("combatStart") ? tag.getLong("combatStart") : -1L;
        blossomPhase = parsePhase(tag.getString("blossom"));
        blossomStartTick = tag.contains("blossomStart") ? tag.getLong("blossomStart") : -1L;
        weakened = tag.getBoolean("weakened");
        obsessionStacks = tag.getInt("obsStacks");
        lastKillTick = tag.contains("lastKill") ? tag.getLong("lastKill") : -1L;
        familiarKillCount = tag.getInt("familiarKills");
        promiseTarget = tag.contains("promiseTarget") ? tag.getString("promiseTarget") : "";
        promiseEndTick = tag.contains("promiseEnd") ? tag.getLong("promiseEnd") : -1L;
        promisePool = tag.getFloat("promisePool");
        selfishEye = StolenStats.load(tag.getCompound("selfishEye"));
        pendingSelfishEye = StolenStats.load(tag.getCompound("pendingSelfishEye"));
        float absorption = tag.getFloat("selfishEyeAbsorption");
        selfishEyeAbsorption = Float.isFinite(absorption) ? Math.max(0.0F, absorption) : 0.0F;
        unlockedSpells.clear();
        ListTag list = tag.getList("spells", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            unlockedSpells.add(list.getString(i));
        }
        defeatedWitches.clear();
        ListTag witches = tag.getList("defeatedWitches", Tag.TAG_STRING);
        for (int i = 0; i < witches.size(); i++) {
            recordDefeatedWitch(witches.getString(i));
        }
    }

    private static BlossomPhase parsePhase(String name) {
        try {
            return name == null || name.isEmpty() ? BlossomPhase.NONE : BlossomPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BlossomPhase.NONE;
        }
    }

    // ---------------------------------------------------------------- 污染值

    public float getTaint() {
        return taint;
    }

    /** 增加污染值，返回实际增加量。 */
    public float addTaint(float amount) {
        if (amount <= 0.0F || witchPath || isHalfWitch()) {
            return 0.0F;
        }
        float before = taint;
        taint = Math.min(TAINT_MAX, taint + amount);
        return taint - before;
    }

    public float removeTaint(float amount) {
        if (amount <= 0.0F || mustWitchify()) {
            return 0.0F;
        }
        float before = taint;
        taint = Math.max(0.0F, taint - amount);
        return before - taint;
    }

    public void setTaint(float value) {
        taint = Math.max(0.0F, Math.min(TAINT_MAX, value));
        if (!isWitchReady()) {
            witchDeathTick = -1L;
        }
    }

    public boolean isHalfWitchReady() {
        return taint >= HALF_WITCH_THRESHOLD;
    }

    public boolean isWitchReady() {
        return taint >= TAINT_MAX;
    }

    public boolean mustWitchify() {
        return contracted && !witchPath && isWitchReady();
    }

    public long getWitchDeathTick() {
        return witchDeathTick;
    }

    public void setWitchDeathTick(long tick) {
        witchDeathTick = tick;
    }

    public int getHardcoreRespawnMode() {
        return hardcoreRespawnMode;
    }

    public void setHardcoreRespawnMode(int mode) {
        hardcoreRespawnMode = mode;
    }

    public boolean isHalfWitch() {
        return isTransformed() && !witchPath && halfWitchTicks > 0;
    }

    public void startHalfWitch() {
        halfWitchTicks = Math.round(taint * 20.0F);
        setTaint(0.0F);
    }

    /** The timer uses the pollution spent when entering this form. */
    public void tickHalfWitch() {
        halfWitchTicks--;
    }

    // ---------------------------------------------------------------- 魔力

    /** 当前魔力。 */
    public double getMana() {
        return mana;
    }

    public void setMana(double mana) {
        this.mana = Math.max(0.0D, mana);
    }

    /** 消耗魔力，返回实际消耗量（不足时返回 0）。 */
    public double spendMana(double cost) {
        if (cost <= 0.0D) {
            return 0.0D;
        }
        double spent = Math.min(mana, cost);
        mana -= spent;
        return spent;
    }

    /** 恢复魔力。 */
    public void restoreMana(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        mana += amount;
    }

    // ---------------------------------------------------------------- 等级

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    // ---------------------------------------------------------------- 身份

    public String getThemeId() {
        return themeId;
    }

    /** 当前宝石对应的魔法少女定义；没有则返回 null。 */
    public MagicalGirl getTheme() {
        return MagicalGirlRegistry.get(themeId);
    }

    public void setTheme(String themeId) {
        this.themeId = themeId == null ? "" : themeId;
    }

    /** 当前是否是魔法少女（拥有灵魂宝石）。 */
    public boolean isContracted() {
        return contracted;
    }

    public void setContracted(boolean contracted) {
        this.contracted = contracted;
        if (contracted) {
            everContracted = true;
        } else {
            setTransformed(false);
        }
    }

    public boolean isTransformed() {
        return contracted && transformed;
    }

    public void setTransformed(boolean transformed) {
        this.transformed = contracted && transformed;
        if (!this.transformed) {
            halfWitchTicks = 0;
        }
    }

    public boolean isWitchPath() {
        return witchPath;
    }

    public void setWitchPath(boolean witchPath) {
        this.witchPath = witchPath;
        if (!witchPath) {
            despair = -1.0D;
        }
    }

    public double getDespair() {
        return despair;
    }

    public void setDespair(double value) {
        if (Double.isFinite(value)) {
            despair = Math.max(0.0D, Math.min(100.0D, value));
        }
    }

    /**
     * 是否曾经签约过。
     * <p>眷属（所有怪物）的可见性与掉落权限按这个判定，
     * 不是按"当前有没有契约"——否则魔女化之后玩家连怪物都看不见也打不死。</p>
     */
    public boolean isEverContracted() {
        return everContracted;
    }

    public void setEverContracted(boolean everContracted) {
        this.everContracted = everContracted;
    }

    /** 魔女化后选择"保留魔法少女身份"。 */
    public boolean isKeepIdentity() {
        return keepIdentity;
    }

    public void setKeepIdentity(boolean keepIdentity) {
        this.keepIdentity = keepIdentity;
    }

    /** 魔女化后正在等待玩家选择去留。 */
    public boolean isPendingWitchChoice() {
        return pendingWitchChoice;
    }

    public void setPendingWitchChoice(boolean pendingWitchChoice) {
        this.pendingWitchChoice = pendingWitchChoice;
    }

    // ---------------------------------------------------------------- 魔女方向

    /** 魔女相对玩家朝向的角度（度），NaN 表示附近没有魔女。 */
    public float getWitchDirection() {
        return witchDirection;
    }

    public float getWitchDistance() {
        return witchDistance;
    }

    public void setWitchDirection(float direction, float distance) {
        this.witchDirection = direction;
        this.witchDistance = distance;
    }

    // ---------------------------------------------------------------- 战斗状态

    /** 最后一次受到来自生物的伤害时的游戏时间；-1 表示从未进入战斗。 */
    public long getLastCombatTick() {
        return lastCombatTick;
    }

    /** 本次战斗开始时的游戏时间；-1 表示当前不在战斗中。 */
    public long getCombatStartTick() {
        return combatStartTick;
    }

    /**
     * 记录一次"受到来自生物的伤害"。
     * <p>花期的判定只看这个——<b>打出伤害不算</b>，只有挨打才算在战斗。</p>
     */
    public void markCombat(long gameTime) {
        if (lastCombatTick < 0L || gameTime - lastCombatTick > COMBAT_TIMEOUT_TICKS) {
            // 超时 → 视为新的一场战斗
            combatStartTick = gameTime;
        }
        lastCombatTick = gameTime;
    }

    /** 是否仍在战斗中（最近 COMBAT_TIMEOUT_TICKS 内挨过打）。 */
    public boolean isInCombat(long gameTime) {
        return lastCombatTick >= 0L && gameTime - lastCombatTick <= COMBAT_TIMEOUT_TICKS;
    }

    /** 战斗已经持续了多少 tick；不在战斗中返回 0。 */
    public long getCombatTicks(long gameTime) {
        if (!isInCombat(gameTime) || combatStartTick < 0L) {
            return 0L;
        }
        return Math.max(0L, gameTime - combatStartTick);
    }

    /** 清除战斗状态（脱战时用）。 */
    public void clearCombat() {
        lastCombatTick = -1L;
        combatStartTick = -1L;
    }

    // ---------------------------------------------------------------- 花期

    public BlossomPhase getBlossomPhase() {
        return blossomPhase;
    }

    public void setBlossomPhase(BlossomPhase phase) {
        this.blossomPhase = phase == null ? BlossomPhase.NONE : phase;
    }

    public long getBlossomStartTick() {
        return blossomStartTick;
    }

    public void setBlossomStartTick(long tick) {
        this.blossomStartTick = tick;
    }

    /** 是否处于"属性下降"状态（花落后生效，睡一觉起床恢复）。 */
    public boolean isWeakened() {
        return weakened;
    }

    public void setWeakened(boolean weakened) {
        this.weakened = weakened;
    }

    // ---------------------------------------------------------------- 执念

    public int getObsessionStacks() {
        return obsessionStacks;
    }

    public void setObsessionStacks(int stacks) {
        this.obsessionStacks = Math.max(0, stacks);
    }

    public long getLastKillTick() {
        return lastKillTick;
    }

    public void setLastKillTick(long tick) {
        this.lastKillTick = tick;
    }

    // ---------------------------------------------------------------- 击杀计数

    public int getFamiliarKillCount() {
        return familiarKillCount;
    }

    public void setFamiliarKillCount(int count) {
        this.familiarKillCount = Math.max(0, count);
    }

    // ---------------------------------------------------------------- 无法偿还的约定（赎罪系 C-4）

    /** 当前是否有生效中的约定。 */
    public boolean isPromised() {
        return !promiseTarget.isEmpty() && promiseEndTick > 0L;
    }

    public String getPromiseTarget() {
        return promiseTarget;
    }

    public void setPromiseTarget(String uuid) {
        this.promiseTarget = uuid == null ? "" : uuid;
    }

    public long getPromiseEndTick() {
        return promiseEndTick;
    }

    public void setPromiseEndTick(long tick) {
        this.promiseEndTick = tick;
    }

    public float getPromisePool() {
        return promisePool;
    }

    public void setPromisePool(float pool) {
        this.promisePool = Math.max(0.0F, pool);
    }

    /** 清空约定（到期、失败、死亡都走这里）。 */
    public void clearPromise() {
        promiseTarget = "";
        promiseEndTick = -1L;
        promisePool = 0.0F;
    }

    // ---------------------------------------------------------------- 技能

    public StolenStats getSelfishEye() {
        return selfishEye;
    }

    public boolean hasPendingSelfishEye() {
        return !pendingSelfishEye.equals(StolenStats.ZERO) || selfishEyeAbsorption > 0.0F;
    }

    public void setPendingSelfishEye(StolenStats retained, float absorption) {
        pendingSelfishEye = retained;
        selfishEyeAbsorption = absorption;
    }

    /** 把本场留存结算一次，并返回应移除的临时黄心。 */
    public float settleSelfishEye() {
        selfishEye = selfishEye.plus(pendingSelfishEye);
        float absorption = selfishEyeAbsorption;
        setPendingSelfishEye(StolenStats.ZERO, 0.0F);
        return absorption;
    }

    public Set<String> getUnlockedSpells() {
        return unlockedSpells;
    }

    public boolean hasSpell(String id) {
        return unlockedSpells.contains(id);
    }

    public void unlockSpell(String id) {
        unlockedSpells.add(id);
    }

    public boolean recordDefeatedWitch(String themeId) {
        return !themeId.isBlank() && defeatedWitches.add(themeId);
    }

    public void queueWitchReward(ItemStack stack) {
        pendingWitchRewards.add(stack);
    }

    public boolean deliverWitchRewards(Player player) {
        var inventory = player.getInventory();
        boolean changed = false;
        var rewards = pendingWitchRewards.iterator();
        while (rewards.hasNext()) {
            ItemStack stack = rewards.next();
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot < 0) {
                slot = inventory.getFreeSlot();
            }
            if (slot >= 0) {
                int count = stack.getCount();
                inventory.add(slot, stack);
                changed |= stack.getCount() != count;
                if (stack.isEmpty()) {
                    rewards.remove();
                }
            }
        }
        if (changed) {
            save(player);
        }
        return pendingWitchRewards.isEmpty();
    }

    public List<String> getDefeatedWitches() {
        return List.copyOf(defeatedWitches);
    }
}
