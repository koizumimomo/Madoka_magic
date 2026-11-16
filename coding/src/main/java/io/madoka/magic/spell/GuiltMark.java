package io.madoka.magic.spell;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * 赎罪系 · 罪之刻印（C-1）。
 *
 * <p>挂在<b>敌人身上</b>的"罪责账本"：敌人每次对赎罪系玩家造成伤害，
 * 就把伤害量记到它自己头上。罪责是赎罪之刃（消费）与无法偿还的约定（立约条件）的资源。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>罪责是<b>双向记忆</b>——赎罪系记得谁打过她。所以账本记在敌人身上，而不是玩家身上。</li>
 *   <li>同一敌人对同一玩家最多累计到其<b>最大生命的 50%</b>（设计 §C-1 的"上限"）。</li>
 *   <li>按玩家 UUID 分开记账，多人游戏里两个赎罪系玩家互不干扰。</li>
 * </ul>
 *
 * <p>存储用实体的 persistent data（Forge 的 {@code ForgeData}），怪物跨区块、
 * 存档重载都不丢。</p>
 */
public final class GuiltMark {

    /** NBT 键。 */
    public static final String KEY = "madoka_guilt";

    /** 罪责上限：目标最大生命的这个比例。 */
    public static final float CAP_RATIO = 0.5F;

    /** HUD 统计罪责总量时扫描的半径（格）。 */
    public static final double HUD_SCAN_RADIUS = 16.0D;

    /** 一份罪责记录。 */
    public record Guilt(float amount, long lastTick, int consumed) {
        public static final Guilt NONE = new Guilt(0.0F, -1L, 0);
    }

    private GuiltMark() {
    }

    /** 取（或创建）实体上的账本根 compound。 */
    private static CompoundTag ledger(LivingEntity target) {
        CompoundTag root = target.getPersistentData();
        CompoundTag mark = root.getCompound(KEY);
        root.put(KEY, mark);
        return mark;
    }

    private static CompoundTag players(LivingEntity target) {
        CompoundTag mark = ledger(target);
        CompoundTag players = mark.getCompound("players");
        mark.put("players", players);
        return players;
    }

    /**
     * 记一笔罪责。
     *
     * @param target    欠债的敌人
     * @param player    债主（赎罪系玩家）
     * @param amount    它这次造成的伤害
     * @param gameTime  当前游戏时间
     */
    public static void record(LivingEntity target, UUID player, float amount, long gameTime) {
        if (target == null || player == null || amount <= 0.0F) {
            return;
        }
        CompoundTag players = players(target);
        String key = player.toString();
        CompoundTag entry = players.contains(key, Tag.TAG_COMPOUND) ? players.getCompound(key) : new CompoundTag();
        float cap = target.getMaxHealth() * CAP_RATIO;
        float current = entry.getFloat("amount");
        entry.putFloat("amount", Math.min(cap, current + amount));
        entry.putLong("last", gameTime);
        players.put(key, entry);
        ledger(target).put("players", players);
    }

    /** 读一份罪责记录（没有则返回 {@link Guilt#NONE}）。 */
    public static Guilt get(LivingEntity target, UUID player) {
        if (target == null || player == null) {
            return Guilt.NONE;
        }
        CompoundTag root = target.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) {
            return Guilt.NONE;
        }
        CompoundTag players = root.getCompound(KEY).getCompound("players");
        String key = player.toString();
        if (!players.contains(key, Tag.TAG_COMPOUND)) {
            return Guilt.NONE;
        }
        CompoundTag entry = players.getCompound(key);
        return new Guilt(Math.max(0.0F, entry.getFloat("amount")), entry.getLong("last"), entry.getInt("consumed"));
    }

    /** 该敌人目前欠这位玩家多少罪责。 */
    public static float getAmount(LivingEntity target, UUID player) {
        return get(target, player).amount();
    }

    /**
     * 结算（消费）该敌人欠这位玩家的全部罪责。
     *
     * @return 实际消费掉的罪责量；没有罪责返回 0
     */
    public static float consume(LivingEntity target, UUID player) {
        if (target == null || player == null) {
            return 0.0F;
        }
        CompoundTag root = target.getPersistentData();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) {
            return 0.0F;
        }
        CompoundTag mark = root.getCompound(KEY);
        CompoundTag players = mark.getCompound("players");
        String key = player.toString();
        if (!players.contains(key, Tag.TAG_COMPOUND)) {
            return 0.0F;
        }
        CompoundTag entry = players.getCompound(key);
        float amount = Math.max(0.0F, entry.getFloat("amount"));
        if (amount <= 0.0F) {
            return 0.0F;
        }
        entry.putFloat("amount", 0.0F);
        entry.putInt("consumed", entry.getInt("consumed") + 1);
        players.put(key, entry);
        mark.put("players", players);
        root.put(KEY, mark);
        return amount;
    }

    /** 找附近罪责最高、且超过阈值的敌人（赎罪之刃的锁定目标）。 */
    public static LivingEntity findHighest(ServerPlayer player, double radius, float minAmount) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        LivingEntity best = null;
        float bestAmount = minAmount;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> SpellCasting.canAffect(player, e))) {
            float amount = getAmount(mob, id);
            if (amount > bestAmount) {
                bestAmount = amount;
                best = mob;
            }
        }
        return best;
    }

    /**
     * 附近所有敌人的罪责总量（给 HUD 用）。
     *
     * <p>罪责记在敌人身上，所以"玩家累计的罪责值"只能现算：
     * 每次同步状态时扫一遍附近 16 格的敌人求和。</p>
     */
    public static float totalFor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID id = player.getUUID();
        float total = 0.0F;
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SpellCasting.range(HUD_SCAN_RADIUS)),
                e -> SpellCasting.canAffect(player, e))) {
            total += getAmount(mob, id);
        }
        return total;
    }

    /** 清空某个敌人身上的全部账本（目标死亡/重置时）。 */
    public static void clear(LivingEntity target) {
        if (target != null) {
            target.getPersistentData().remove(KEY);
        }
    }
}
