package io.madoka.magic.event;

import io.madoka.magic.compat.IronsCompat;
import io.madoka.magic.config.MadokaConfig;
import io.madoka.magic.data.MadokaSavedData;
import io.madoka.magic.data.MagicalGirl;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.dimension.MadokaDimensions;
import io.madoka.magic.entity.MadokaWitchEntity;
import io.madoka.magic.entity.WitchCombat;
import io.madoka.magic.entity.WitchPortalEntity;
import io.madoka.magic.entity.WitchTypes;
import io.madoka.magic.item.GriefSeedItem;
import io.madoka.magic.faction.Faction;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.network.PlayerFormState;
import io.madoka.magic.network.TransformationFx;
import io.madoka.magic.registry.ModItems;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.BlossomState;
import io.madoka.magic.spell.DamageContext;
import io.madoka.magic.spell.MadokaPassives;
import io.madoka.magic.spell.PollenTracker;
import io.madoka.magic.spell.PromiseState;
import io.madoka.magic.spell.SpellCasting;
import io.madoka.magic.spell.SpellEffects;
import io.madoka.magic.spell.SpellRegistry;
import io.madoka.magic.spell.ThornTracker;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.List;

/**
 * 污染值、魔女化与伤害倍率。
 *
 * <p>污染值的来源：施法（{@link io.madoka.magic.spell.SpellCasting} 里加）、
 * 普攻命中、受到敌对生物伤害。</p>
 */
public class TaintEvents {
    private static final int WITCH_WARNING_TICKS = 20;

    // ------------------------------------------------------------ 伤害倍率

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (io.madoka.magic.spell.OriginalCurseEffects.isSettlement(event.getSource())) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || event.getSource().is(WitchCombat.FAMILIAR_LINK)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        Entity victim = event.getEntity();

        // 造成伤害：宝石性质的倍率
        if (source instanceof Player attacker) {
            PlayerMadokaData data = PlayerMadokaData.of(attacker);
            if (data.isTransformed()) {
                event.setAmount((float) MadokaMath.outgoingDamage(attacker, event.getAmount(),
                        MadokaMath.outgoingDamageMultiplier(attacker)));
            }
        }
        // 受到伤害：宝石性质的倍率
        if (victim instanceof Player victimPlayer) {
            PlayerMadokaData data = PlayerMadokaData.of(victimPlayer);
            if (data.isTransformed()) {
                event.setAmount((float) (event.getAmount() * MadokaMath.damageTakenMultiplier(victimPlayer)));
            }
        }

        // 阵营减免（最外层）：同阵营伤害 × 0.5
        // 放在最后乘，保证「魔法少女打自家眷属只吃一半」对所有伤害来源一致。
        // 赎罪之刃的"处刑"用 DamageContext 明确要求跳过这一层。
        if (!DamageContext.isBypassingSameFaction() && Faction.isSameFaction(source, victim)) {
            event.setAmount((float) (event.getAmount() * Faction.sameFactionDamageMultiplier()));
        }

        // 无法偿还的约定（赎罪系 C-4）：目标对玩家造成的伤害只吃 30%，其余进约定池。
        // 放在所有倍率之后结算，保证"约定池 = 玩家实际少吃的那部分"。
        if (victim instanceof ServerPlayer victimPlayer && source != null && !event.isCanceled()) {
            event.setAmount(PromiseState.reduceIncomingDamage(victimPlayer, source, event.getAmount()));
        }
    }

    /**
     * 荆棘之拥（赎罪系 C-2）：攻击被束缚者的人，会受到自身伤害的一部分反噬。
     *
     * <p>"真实伤害"用 {@code damageSources().magic()} 结算（无视护甲），但有数值上限。
     * 荆棘自己的每秒持续伤害用 {@link ThornTracker#isInternalDamage()} 排除，
     * 否则会把玩家自己每秒弹一次。</p>
     */
    @SubscribeEvent
    public void onLivingDamageThorns(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F || event.getSource().is(WitchCombat.FAMILIAR_LINK)
                || ThornTracker.isInternalDamage() || ThornTracker.isReflecting()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!ThornTracker.isBound(victim)) {
            return;
        }
        Entity attackerEntity = event.getSource().getEntity();
        if (!(attackerEntity instanceof LivingEntity attacker) || attacker == victim) {
            return;
        }
        float reflect = ThornTracker.reflect(event.getAmount());
        if (reflect <= 0.0F) {
            return;
        }
        // 反刺结算期间关掉反刺，避免两个都被束缚的敌人互相反弹到栈溢出
        ThornTracker.beginReflect();
        try {
            attacker.hurt(attacker.level().damageSources().magic(), reflect);
        } finally {
            ThornTracker.endReflect();
        }
    }

    // ------------------------------------------------------------ 战斗状态

    /**
     * 玩家挨打 → 记录战斗时间 + 污染累积。
     *
     * <p>这里是「受击方是玩家」的分支。注意 {@code event.getEntity()} 是<b>受击方</b>，
     * 所以这个方法<b>不能</b>用来触发「玩家造成伤害」的回调 —— 那是另一个方法。</p>
     */
    @SubscribeEvent
    public void onPlayerHurt(LivingDamageEvent event) {
        if (io.madoka.magic.spell.OriginalCurseEffects.isSettlement(event.getSource())) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return;
        }

        // 受到来自生物的伤害 → 记录战斗时间（花期的判定依据）
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            data.markCombat(player.level().getGameTime());
            data.save(player);

            float amount = MadokaConfig.TAINT_PER_HIT.get().floatValue() * MadokaMath.taintRate(player);
            if (amount > 0.0F) {
                data.addTaint(amount);
                data.save(player);
                MadokaNetwork.syncTo(player);
            }

            // 赎罪系「罪之刻印」在这里记账：谁打了她、打了多少。
            // 只遍历当前角色的被动 —— 别的角色的被动不该生效。
            for (var def : SpellRegistry.passivesOf(data)) {
                SpellEffects.PassiveEffect passive = SpellEffects.passive(def.type());
                if (passive != null) {
                    passive.onTakeDamage(player, attacker, event.getAmount());
                }
            }
        }
    }

    /**
     * 玩家造成伤害 → 触发被动的「造成伤害时」回调。
     *
     * <p>献身之翼在这里给敌人挂蝴蝶标记 —— 它是「先挂标记再收割」链路的第一环。
     * 用 {@link LivingDamageEvent}（而非 {@link LivingHurtEvent}）是因为
     * 前者在伤害已实际结算后触发，此时"命中敌人"才成立。</p>
     *
     * <p>只遍历<b>当前角色的</b>被动 —— 别的角色的被动不该生效。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDealDamage(LivingDamageEvent event) {
        if (io.madoka.magic.spell.OriginalCurseEffects.isSettlement(event.getSource())) return;
        if (event.getAmount() <= 0.0F || event.getSource().is(WitchCombat.FAMILIAR_LINK)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        PlayerMadokaData attackerData = PlayerMadokaData.of(attacker);
        if (!attackerData.isTransformed()) {
            return;
        }
        for (var def : SpellRegistry.passivesOf(attackerData)) {
            var passive = io.madoka.magic.spell.SpellEffects.passive(def.type());
            if (passive != null) {
                passive.onDealDamage(attacker, event.getEntity(), event.getAmount());
            }
        }
    }

    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            return;
        }
        float amount = MadokaConfig.TAINT_PER_MELEE.get().floatValue() * MadokaMath.taintRate(player);
        if (amount > 0.0F) {
            data.addTaint(amount);
            data.save(player);
        }
    }

    // ------------------------------------------------------------ 魔女化

    /** Returns true while the threshold death is pending or has just happened. */
    public static boolean tickWitchification(ServerPlayer player, PlayerMadokaData data) {
        if (!data.mustWitchify() || data.getTheme() == null || player.isSpectator()
                || deferInsideBarrier(player, data)) {
            return false;
        }
        long now = player.serverLevel().getGameTime();
        if (data.getWitchDeathTick() < 0) {
            data.setWitchDeathTick(now + WITCH_WARNING_TICKS);
            data.save(player);
            warnWitchification(player);
            MadokaNetwork.syncTo(player);
        } else if (now >= data.getWitchDeathTick()) {
            player.kill();
        }
        return true;
    }

    private static boolean deferInsideBarrier(ServerPlayer player, PlayerMadokaData data) {
        if (MadokaDimensions.barrierTheme(player.level().dimension()).isEmpty()) {
            return false;
        }
        if (data.getWitchDeathTick() >= 0) {
            data.setWitchDeathTick(-1);
            data.save(player);
        }
        return true;
    }

    private static void warnWitchification(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.madoka_magic.witchification_warning"), true);
    }

    @SubscribeEvent
    public void onHardcoreWitchRespawn(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !event.getServer().isHardcore()) {
            return;
        }
        respawnMarkedPlayers(event.getServer().getPlayerList());
    }

    private static void respawnMarkedPlayers(PlayerList players) {
        for (var dead : List.copyOf(players.getPlayers())) {
            int mode = PlayerMadokaData.of(dead).getHardcoreRespawnMode();
            if (mode < 0 || dead.hasDisconnected()) {
                continue;
            }
            // The client-command path switches the new player to spectator on hardcore.
            ServerPlayer player = dead;
            if (dead.isDeadOrDying()) {
                player = players.respawn(dead, false);
                player.connection.player = player;
            }
            player.setGameMode(GameType.byId(mode));
            var data = PlayerMadokaData.of(player);
            data.setHardcoreRespawnMode(-1);
            data.save(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onWitchAssistance(LivingDamageEvent event) {
        if (event.getAmount() > 0.0F && event.getEntity() instanceof MadokaWitchEntity witch && !witch.level().isClientSide) {
            witch.markAssistedBy(event.getSource().getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onWitchSkillDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof MadokaWitchEntity witch && witch.level() instanceof ServerLevel level) {
            event.setAmount(witch.combat().handleDamage(level, event.getSource(), event.getAmount()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();

        // 被动回调：击杀时触发（执念叠层）
        // 只遍历<b>当前角色的</b>被动 —— 别的角色的被动不该生效。
        Player killer = event.getSource().getEntity() instanceof Player p ? p : null;
        if (killer instanceof ServerPlayer serverKiller) {
            PlayerMadokaData killerData = PlayerMadokaData.of(serverKiller);
            if (killerData.isTransformed()) {
                for (var def : SpellRegistry.passivesOf(killerData)) {
                    var passive = io.madoka.magic.spell.SpellEffects.passive(def.type());
                    if (passive != null) {
                        passive.onKill(serverKiller, dead);
                    }
                }
            }
        }

        if (!(dead instanceof ServerPlayer player)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        // 玩家死亡：赎罪系的约定与荆棘全部作废（约定失败 → 池子清空）
        PromiseState.clear(player);
        ThornTracker.clear(player.getUUID());
        PollenTracker.clear(player.getUUID());
        boolean wasTransformed = data.isTransformed();
        if (wasTransformed) {
            if (data.isWitchPath() && data.getTheme() != null) {
                ServerPlayer finisher = WitchTypes.finalAttacker(event.getSource());
                if (finisher != null && !finisher.getUUID().equals(player.getUUID())) {
                    player.spawnAtLocation(GriefSeedItem.fromPlayerWitch(data.getThemeId(), player.getGameProfile().getName()));
                }
            }
            PlayerTransformation.revert(player);
            PlayerFormState.broadcast(player);
        }
        if (!data.mustWitchify() || player.isSpectator() || deferInsideBarrier(player, data)) {
            return;
        }

        MagicalGirl theme = data.getTheme();
        if (theme == null) {
            return;
        }
        if (data.getWitchDeathTick() < 0) {
            warnWitchification(player);
        }
        if (player.serverLevel().getServer().isHardcore()) {
            data.setHardcoreRespawnMode(player.gameMode.getGameModeForPlayer().getId());
        }
        // 污染值清零，宝石碎裂
        data.setTaint(0.0F);
        data.setContracted(false);
        data.setPendingWitchChoice(true);
        // 花期状态清空
        BlossomState.stop(player);
        data.save(player);
        if (wasTransformed) {
            TransformationFx.send(player, theme.getId(), TransformationFx.Kind.WITCH);
        }

        // 死亡位置生成入口传送门（魔女不在主世界生成，玩家触门进维度时确保她在场）
        ServerLevel level = (ServerLevel) player.level();
        WitchPortalEntity entrance = WitchPortalEntity.spawn(level, WitchPortalEntity.KIND_ENTRANCE,
                theme.getId(), player.getUUID(), player.position());
        if (entrance != null) {
            MadokaSavedData.get(level).setEntrance(player.getUUID(),
                    level.dimension().location(), player.blockPosition());
        }

        SpellCasting.clearCooldowns(player.getUUID());
    }

    /**
     * 魔女化后的去留选择。
     *
     * @param keep true = 保留魔法少女身份（回去打自己的魔女化身，可半魔女化 / 增强自身）
     *             false = 放弃身份，重新做一张别的契约
     */
    public static void applyWitchChoice(ServerPlayer player, boolean keep) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isPendingWitchChoice()) {
            return;
        }
        data.setPendingWitchChoice(false);
        if (keep) {
            data.setKeepIdentity(true);
            data.setContracted(true);
            data.setEverContracted(true);
        } else {
            data.setKeepIdentity(false);
            data.setContracted(false);
            data.setWitchPath(false);
            data.setTheme("");
            data.getUnlockedSpells().clear();
        }
        PlayerTransformation.applyAttributes(player);
        data.save(player);
        MadokaNetwork.syncTo(player);
    }

    // ------------------------------------------------------------ 死亡复制

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        PlayerMadokaData old = PlayerMadokaData.of(event.getOriginal());
        PlayerMadokaData data = PlayerMadokaData.of(event.getEntity());
        data.load(old.serialize());
        for (var attribute : List.of(MadokaAttributes.GEM_PURITY.get(), MadokaAttributes.MAGIC_CAUSALITY.get(),
                MadokaAttributes.MAGIC_POWER.get())) {
            var previous = event.getOriginal().getAttribute(attribute);
            var current = event.getEntity().getAttribute(attribute);
            if (previous != null && current != null) {
                current.load(previous.save());
            }
        }
        // 死亡 = 约定失败：数据虽然被复制过来，但约定与荆棘都要清掉
        if (event.isWasDeath()) {
            data.setTransformed(false);
            data.setWeakened(false);
            data.clearPromise();
            ThornTracker.clear(event.getOriginal().getUUID());
        }
        data.save(event.getEntity());
    }
}
