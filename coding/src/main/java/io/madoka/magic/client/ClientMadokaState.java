package io.madoka.magic.client;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端侧的魔法少女状态缓存。
 *
 * <p>由服务端的 {@link io.madoka.magic.network.MadokaNetwork.SyncState} 推送。
 * HUD（宝石图标、污染值）和轮盘都从这里读。</p>
 */
public final class ClientMadokaState {

    private static float taint;
    private static int level = 1;
    private static String themeId = "";
    private static boolean contracted;
    private static boolean transformed;
    private static boolean witchPath;
    private static boolean everContracted;
    private static boolean keepIdentity;
    private static boolean pendingWitchChoice;
    private static final List<String> unlockedSpells = new ArrayList<>();
    private static List<String> defeatedWitches = List.of();
    private static double mana;
    /** 魔力上限（属性 MAGIC_MANA）。 */
    private static double maxMana;
    /** 纯度或绝望（0~100 百分比）。 */
    private static double progression;
    private static float witchDir = Float.NaN;
    private static float witchDist = -1.0F;
    private static double causality;
    private static double power;
    /** 花期阶段：0 无 / 1 绽放 / 2 花落。 */
    private static int blossomPhase;
    /** 花期当前的伤害加成倍率（1.0 = 无加成）。 */
    private static float blossomBonus = 1.0F;
    /** 执念层数。 */
    private static int obsessionStacks;
    /** 赎罪系：附近敌人的罪责总量。 */
    private static float guiltTotal;
    /** 赎罪系：无法偿还的约定剩余 tick（0 = 没有约定）。 */
    private static int promiseTicks;
    /** 赎罪系：约定池当前积累的伤害量。 */
    private static float promisePool;

    private ClientMadokaState() {
    }

    public static void accept(io.madoka.magic.network.MadokaNetwork.SyncState packet) {
        taint = packet.taint;
        level = packet.level;
        if (!themeId.equals(packet.themeId) || contracted != packet.contracted || witchPath != packet.witchPath) {
            SkillDetails.reset();
        }
        themeId = packet.themeId;
        contracted = packet.contracted;
        transformed = packet.transformed;
        witchPath = packet.witchPath;
        everContracted = packet.everContracted;
        keepIdentity = packet.keepIdentity;
        pendingWitchChoice = packet.pendingWitchChoice;
        unlockedSpells.clear();
        unlockedSpells.addAll(packet.spells);
        defeatedWitches = packet.defeatedWitches;
        mana = packet.mana;
        maxMana = packet.maxMana;
        progression = packet.purity;
        witchDir = packet.witchDir;
        witchDist = packet.witchDist;
        causality = packet.causality;
        power = packet.power;
        blossomPhase = packet.blossomPhase;
        blossomBonus = packet.blossomBonus;
        obsessionStacks = packet.obsessionStacks;
        guiltTotal = packet.guiltTotal;
        promiseTicks = packet.promiseTicks;
        promisePool = packet.promisePool;
    }

    /** 污染值（0~100）。 */
    public static float getTaint() {
        return taint;
    }

    public static int getLevel() {
        return level;
    }

    public static String getThemeId() {
        return themeId;
    }

    public static boolean isContracted() {
        return contracted;
    }

    public static boolean isTransformed() {
        return transformed;
    }

    public static boolean isWitchPath() {
        return witchPath;
    }

    public static boolean isEverContracted() {
        return everContracted;
    }

    public static boolean isKeepIdentity() {
        return keepIdentity;
    }

    public static boolean isPendingWitchChoice() {
        return pendingWitchChoice;
    }

    public static List<String> getUnlockedSpells() {
        return unlockedSpells;
    }

    public static double getMana() {
        return mana;
    }

    public static List<String> getDefeatedWitches() {
        return defeatedWitches;
    }

    /** 魔力上限（属性 MAGIC_MANA）。 */
    public static double getMaxMana() {
        return maxMana;
    }

    public static double getProgression() {
        return progression;
    }

    /** 魔女相对玩家朝向的角度（度），NaN 表示附近没有魔女。 */
    public static float getWitchDirection() {
        return witchDir;
    }

    public static float getWitchDistance() {
        return witchDist;
    }

    public static double getCausality() {
        return causality;
    }

    public static double getPower() {
        return power;
    }

    /** 花期阶段：0 无 / 1 绽放 / 2 花落。 */
    public static int getBlossomPhase() {
        return blossomPhase;
    }

    /** 是否处于花期绽放阶段。 */
    public static boolean isBlooming() {
        return blossomPhase == 1;
    }

    /** 是否处于花落阶段。 */
    public static boolean isFalling() {
        return blossomPhase == 2;
    }

    /** 花期当前的伤害加成倍率（1.0 = 无加成）。 */
    public static float getBlossomBonus() {
        return blossomBonus;
    }

    /** 执念层数。 */
    public static int getObsessionStacks() {
        return obsessionStacks;
    }

    /** 赎罪系：附近敌人的罪责总量（HUD 用）。 */
    public static float getGuiltTotal() {
        return guiltTotal;
    }

    /** 赎罪系：无法偿还的约定剩余 tick（0 = 没有约定）。 */
    public static int getPromiseTicks() {
        return promiseTicks;
    }

    /** 赎罪系：约定池当前积累的伤害量。 */
    public static float getPromisePool() {
        return promisePool;
    }

    /** 是否正处于"无法偿还的约定"中。 */
    public static boolean isPromised() {
        return promiseTicks > 0;
    }

    public static io.madoka.magic.data.MagicalGirl getTheme() {
        return io.madoka.magic.data.MagicalGirlRegistry.get(themeId);
    }

    /** 退出世界/换服务器时清空，避免旧状态残留到下一次登录。 */
    public static void reset() {
        ClientProgression.reset();
        SkillDetails.reset();
        taint = 0.0F;
        level = 1;
        themeId = "";
        contracted = false;
        transformed = false;
        witchPath = false;
        everContracted = false;
        keepIdentity = false;
        pendingWitchChoice = false;
        unlockedSpells.clear();
        defeatedWitches = List.of();
        mana = 0.0D;
        maxMana = 0.0D;
        progression = 0.0D;
        witchDir = Float.NaN;
        witchDist = -1.0F;
        causality = 0.0D;
        power = 0.0D;
        blossomPhase = 0;
        blossomBonus = 1.0F;
        obsessionStacks = 0;
        guiltTotal = 0.0F;
        promiseTicks = 0;
        promisePool = 0.0F;
    }
}
