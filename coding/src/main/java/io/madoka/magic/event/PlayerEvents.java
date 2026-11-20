package io.madoka.magic.event;

import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.item.WitchKissItem;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.PlayerFormState;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.BlossomState;
import io.madoka.magic.spell.MadokaPassives;
import io.madoka.magic.spell.SpellRegistry;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Optional;

/**
 * 玩家相关：登录同步、成长、魔力回复、魔女方向、花期结算。
 */
public class PlayerEvents {

    /** 指示器搜索魔女的最大距离。 */
    private static final double WITCH_INDICATOR_RANGE = 128.0D;

    // ------------------------------------------------------------ 登录 / 重生

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMadokaData.of(player).deliverWitchRewards(player);
            var purity = MadokaAttributes.GEM_PURITY.get();
            if (PlayerMadokaData.of(player).isContracted() && player.getAttributes().getSyncableAttributes().stream()
                    .noneMatch(instance -> instance.getAttribute() == purity)) {
                // Older worlds could contain signed players without a saved purity attribute.
                var instance = player.getAttribute(purity);
                if (instance != null) {
                    instance.setBaseValue(MadokaConfig.INITIAL_PURITY.get());
                }
            }
            PlayerTransformation.applyAttributes(player);
            refreshUnlocks(player);
            PlayerFormState.broadcast(player);
            MadokaNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerTransformation.applyAttributes(player);
            refreshUnlocks(player);
            PlayerFormState.broadcast(player);
            MadokaNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerFormState.broadcast(player);
            MadokaNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject
                && PlayerMadokaData.of(subject).isTransformed()) {
            PlayerFormState.sendTo(viewer, subject);
        }
    }

    /** 睡一觉起床 → 花落后的属性下降恢复（睡醒即恢复，不是永久的）。 */
    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlossomState.onWakeUp(player);
            MadokaNetwork.syncTo(player);
        }
    }

    /**
     * 按当前等级刷新已解锁的主动技能。
     * <p>只解锁<b>当前角色的技能表</b>里的技能——别的角色的技能不该进轮盘。</p>
     */
    public static void refreshUnlocks(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isContracted()) {
            return;
        }
        // 解锁线看「纯度百分比」（纯度即等级，floor 后 0–100）
        int purity = MadokaMath.levelOf(player);
        MagicalGirl theme = data.getTheme();
        List<String> defaults = SpellRegistry.defaultUnlocked(purity, theme, data.isWitchPath());
        for (String id : defaults) {
            data.unlockSpell(id);
        }
        PlayerTransformation.refreshGrowth(player, data);
        data.save(player);
    }

    // ------------------------------------------------------------ 每 tick

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        int tick = player.tickCount;
        if (tick % 20 == 0 && player.isAlive()) {
            data.deliverWitchRewards(player);
        }
        if (!player.isAlive() || !data.isContracted()) {
            return;
        }
        PlayerTransformation.tickHalfWitch(player, data);
        if (TaintEvents.tickWitchification(player, data)) {
            return;
        }
        io.madoka.magic.spell.WitchPlayerSpells.tick(player);

        if (tick % 20 == 0) {
            if (data.isTransformed()) {
                PlayerTransformation.refreshGrowth(player, data);
                io.madoka.magic.spell.PromiseState.tick(player);
                double max = MadokaMath.manaMax(player);
                if (data.getMana() < max) {
                    data.restoreMana(Math.min(max - data.getMana(), MadokaMath.manaRegen(player, data)));
                    data.save(player);
                }
                BlossomState.tick(player);
                MadokaPassives.tick(player);
            }
            // 魔女方向（经验条指示器）：128 格 AABB 扫描开销不小，2 秒扫一次足够
            if (tick % 40 == 0 && data.isEverContracted()) {
                updateWitchDirection(player, data);
            }
            // 每秒同步一次
            MadokaNetwork.syncTo(player);
        }
    }

    /** 找最近的魔女，算出相对玩家朝向的角度。 */
    private static void updateWitchDirection(ServerPlayer player, PlayerMadokaData data) {
        Optional<LivingEntity> witch = findNearestWitch(player);
        if (witch.isEmpty()) {
            data.setWitchDirection(Float.NaN, -1.0F);
            data.save(player);
            return;
        }
        Entity e = witch.get();
        double dx = e.getX() - player.getX();
        double dz = e.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        // 相对玩家朝向：0 = 正前方，正数 = 右边
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0D - player.getYRot();
        while (yaw > 180.0D) {
            yaw -= 360.0D;
        }
        while (yaw < -180.0D) {
            yaw += 360.0D;
        }
        data.setWitchDirection((float) yaw, (float) distance);
        data.save(player);
    }

    public static Optional<LivingEntity> findNearestWitch(Player player) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(WITCH_INDICATOR_RANGE),
                        w -> w.isAlive() && !WitchTypes.bossTheme(w).isEmpty())
                .stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
    }

    // ------------------------------------------------------------ 成长

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWitchLoot(LivingDropsEvent event) {
        if (WitchTypes.bossTheme(event.getEntity()).isEmpty()) {
            return;
        }
        event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof GriefSeedItem);
        deliverBarrierDrops(event);
    }

    /**
     * 结界里击杀首领时，原版掉落直接进击杀者背包。
     *
     * <p>首领一死出口门就生成，玩家经常来不及捡就被门送回主世界；
     * 这些掉落不再落地，直接发到击杀者身上（背包满时留在待发放队列）。</p>
     */
    private static void deliverBarrierDrops(LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || MadokaDimensions.barrierTheme(level.dimension()).isEmpty()) {
            return;
        }
        ServerPlayer killer = WitchTypes.finalAttacker(event.getSource());
        if (killer == null || killer.level() != level) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(killer);
        boolean moved = false;
        for (var drop : List.copyOf(event.getDrops())) {
            if (drop.getItem().getItem() instanceof GriefSeedItem) {
                continue;
            }
            data.queueWitchReward(drop.getItem().copy());
            event.getDrops().remove(drop);
            moved = true;
        }
        if (!moved) {
            return;
        }
        if (!killer.isAlive() || !data.deliverWitchRewards(killer)) {
            killer.displayClientMessage(Component.translatable("message.madoka_magic.witch_rewards_pending"), true);
        }
        data.save(killer);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMobDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!Faction.isFamiliar(dead)) {
            return;
        }
        String boss = WitchTypes.bossTheme(dead);
        ServerPlayer killer = boss.isEmpty()
                ? event.getSource().getEntity() instanceof ServerPlayer player ? player : null
                : WitchTypes.finalAttacker(event.getSource());
        if (killer == null) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(killer);
        boolean newWitch = false;
        if (!boss.isEmpty()) {
            var state = dead.getPersistentData();
            if (state.getBoolean("madoka_rewarded")) {
                return;
            }
            state.putBoolean("madoka_rewarded", true);
            // 打过的魔女进冷却池，近期不再顺手刷到同一只。
            data.advanceWitchPortalCooldowns(boss);
            data.coolWitchPortal(boss);
            data.queueWitchReward(GriefSeedItem.fromWitch(boss));
            newWitch = data.recordDefeatedWitch(boss);
            if (newWitch) {
                data.queueWitchReward(WitchKissItem.fromWitch(boss));
            }
            if (!killer.isAlive() || !data.deliverWitchRewards(killer)) {
                killer.displayClientMessage(Component.translatable("message.madoka_magic.witch_rewards_pending"), true);
            }
            data.save(killer);
        }
        if (!data.isContracted()) {
            if (newWitch) {
                MadokaNetwork.syncTo(killer);
            }
            return;
        }

        if (!boss.isEmpty()) {
            // 魔女：纯度直接 +PURITY_PER_WITCH%，因果不变
            MadokaMath.addProgression(killer, MadokaConfig.PURITY_PER_WITCH.get());
            if (dead instanceof MadokaWitchEntity witch) {
                PlayerTransformation.inheritWitchPower(killer, witch);
            }
        } else {
            // Pure progression counts kills; causality additionally applies the configured gain and slime multipliers.
            int kills = data.getFamiliarKillCount() + 1;
            if (kills >= MadokaConfig.FAMILIAR_KILLS_PER_PURITY.get()) {
                kills = 0;
                MadokaMath.addProgression(killer, 1.0D);
            }
            data.setFamiliarKillCount(kills);
            var causalityAttr = killer.getAttribute(MadokaAttributes.MAGIC_CAUSALITY.get());
            if (causalityAttr != null) {
                double gain = MadokaConfig.CAUSALITY_PER_MOB.get() * MadokaConfig.CAUSALITY_GAIN_MULTIPLIER.get();
                if (dead instanceof net.minecraft.world.entity.monster.Slime slime) {
                    gain *= slime.getSize() <= 1 ? 0 : MadokaConfig.SLIME_CAUSALITY_MULTIPLIER.get();
                }
                causalityAttr.setBaseValue(Math.min(MadokaMath.CAUSALITY_CAP, causalityAttr.getBaseValue() + gain));
            }
        }

        // 等级按「灵魂宝石纯度」算 —— 纯度才是击杀积累的那个属性。
        // （曾经误用 MAGIC_MANA（魔力上限）算，等级被钉死在 Lv1~Lv2，
        //   导致大量主动技能永远解锁不了。）
        int newLevel = MadokaMath.levelOf(killer);
        boolean leveledUp = newLevel > data.getLevel();
        if (leveledUp) {
            data.setLevel(newLevel);
            refreshUnlocks(killer);
        }
        data.save(killer);
        if (leveledUp || newWitch) {
            MadokaNetwork.syncTo(killer);
        }
    }
}
