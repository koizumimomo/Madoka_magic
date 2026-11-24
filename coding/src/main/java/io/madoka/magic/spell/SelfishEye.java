package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerMadokaData.StolenStats;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MadokaMagic.MODID)
public final class SelfishEye {
    private static final String ID = "selfish_eye";
    private static final UUID PERMANENT = UUID.fromString("db853cc9-66aa-4d25-9605-6938b23e739f");
    private static final UUID TEMPORARY = UUID.fromString("1e8c66cd-0eb7-41d6-ae41-ed81b40a5ff8");
    private static final Map<ServerPlayer, Combat> COMBATS = new HashMap<>();

    private SelfishEye() {
    }

    private static final class Combat {
        final UUID drainModifier = UUID.randomUUID();
        final Map<LivingEntity, AttributeInstance> targets = new HashMap<>();
        final SpellDefinition definition;
        StolenStats stolen = StolenStats.ZERO;
        long end;
        float absorption;
        float otherAbsorption;

        Combat(SpellDefinition definition) {
            this.definition = definition;
        }

        void reconcileAbsorption(float current) {
            absorption = Math.min(absorption, Math.max(0.0F, current - otherAbsorption));
            otherAbsorption = current - absorption;
        }

        void snapshot(PlayerMadokaData data) {
            double retention = Math.min(1.0D, definition.parameter("retention_ratio"));
            data.setPendingSelfishEye(stolen.scale(retention), absorption);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getAmount() <= 0.0F || !(event.getSource().getEntity() instanceof LivingEntity source)
                || source == event.getEntity()) {
            return;
        }
        if (source instanceof ServerPlayer attacker && attacker.isAlive()) {
            Combat combat = touch(attacker);
            if (combat != null) {
                steal(attacker, event, combat);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (OriginalCurseEffects.isSettlement(event.getSource())) return;
        if (event.getAmount() > 0.0F && event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
            touch(victim);
        }
    }

    private static Combat touch(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        SpellDefinition definition = definition(data);
        if (definition == null) {
            return null;
        }
        long now = player.serverLevel().getGameTime();
        Combat combat = COMBATS.get(player);
        if (combat != null && now >= combat.end) {
            finish(player);
            combat = null;
        }
        if (combat == null) {
            if (data.hasPendingSelfishEye()) {
                finish(player);
            }
            combat = new Combat(definition);
            COMBATS.put(player, combat);
        }
        combat.end = now + combat.definition.ticks("combat_timeout_seconds");
        return combat;
    }

    private static void steal(ServerPlayer player, LivingDamageEvent event, Combat combat) {
        LivingEntity target = event.getEntity();
        double ratio = Math.min(1.0D, combat.definition.parameter("steal_ratio")
                * (1.0D + io.madoka.magic.data.PassiveTree.bonus(player, "selfish_eye")));
        double attack = stealAttack(target, combat, ratio);
        float health = (float) (target.getHealth() * ratio);
        double mana = stealMana(target, ratio);
        combat.stolen = combat.stolen.plus(new StolenStats(attack, health, mana));

        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), TEMPORARY, combat.stolen.attack(),
                AttributeModifier.Operation.ADDITION);
        setModifier(player.getAttribute(MadokaAttributes.MAGIC_MANA.get()), TEMPORARY, combat.stolen.mana(),
                AttributeModifier.Operation.ADDITION);
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (mana > 0.0D) {
            data.restoreMana(Math.min(mana, MadokaMath.manaMax(player) - data.getMana()));
        }
        if (health > 0.0F) {
            event.setAmount(event.getAmount() + health);
            float healing = Math.min(health, Math.max(0.0F, player.getMaxHealth() - player.getHealth()));
            if (healing > 0.0F) {
                player.heal(healing);
            }
            if (health > healing) {
                float current = player.getAbsorptionAmount();
                combat.reconcileAbsorption(current);
                combat.absorption += health - healing;
                player.setAbsorptionAmount(current + health - healing);
            }
        }
        combat.snapshot(data);
        data.save(player);
    }

    private static double stealAttack(LivingEntity target, Combat combat, double ratio) {
        AttributeInstance attack = target.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null || attack.getValue() <= 0.0D || ratio <= 0.0D) {
            return 0.0D;
        }
        double before = attack.getValue();
        AttributeModifier existing = attack.getModifier(combat.drainModifier);
        double multiplier = existing == null ? 1.0D : 1.0D + existing.getAmount();
        setModifier(attack, combat.drainModifier, multiplier * (1.0D - ratio) - 1.0D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        combat.targets.put(target, attack);
        return Math.max(0.0D, before - attack.getValue());
    }

    private static double stealMana(LivingEntity target, double ratio) {
        if (!(target instanceof ServerPlayer victim)) {
            return 0.0D;
        }
        PlayerMadokaData data = PlayerMadokaData.of(victim);
        if (!data.isTransformed()) {
            return 0.0D;
        }
        double amount = data.spendMana(data.getMana() * ratio);
        if (amount > 0.0D) {
            data.save(victim);
        }
        return amount;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        SpellDefinition definition = definition(data);
        Combat combat = COMBATS.get(player);
        if (combat != null) {
            if (definition == null || !player.isAlive() || player.serverLevel().getGameTime() >= combat.end) {
                finish(player);
            } else {
                float before = combat.absorption;
                combat.reconcileAbsorption(player.getAbsorptionAmount());
                if (combat.absorption != before) {
                    combat.snapshot(data);
                    data.save(player);
                }
                combat.targets.entrySet().removeIf(entry -> {
                    if (!entry.getKey().isRemoved()) {
                        return false;
                    }
                    entry.getValue().removeModifier(combat.drainModifier);
                    return true;
                });
            }
        } else if (data.hasPendingSelfishEye()) {
            // 世界重开时服务器不再持有上一场战斗，结算存档里尚未留存的数值。
            finish(player);
        }
        applyPermanent(player, definition == null ? StolenStats.ZERO : data.getSelfishEye());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            finish(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            finish(player);
        }
    }

    private static void finish(ServerPlayer player) {
        Combat combat = COMBATS.remove(player);
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (combat == null && !data.hasPendingSelfishEye()) {
            return;
        }
        if (combat != null) {
            combat.targets.values().forEach(attribute -> attribute.removeModifier(combat.drainModifier));
            setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), TEMPORARY, 0.0D,
                    AttributeModifier.Operation.ADDITION);
            setModifier(player.getAttribute(MadokaAttributes.MAGIC_MANA.get()), TEMPORARY, 0.0D,
                    AttributeModifier.Operation.ADDITION);
            combat.reconcileAbsorption(player.getAbsorptionAmount());
            combat.snapshot(data);
        }
        float absorption = data.settleSelfishEye();
        if (absorption > 0.0F) {
            player.setAbsorptionAmount(Math.max(0.0F, player.getAbsorptionAmount() - absorption));
        }
        applyPermanent(player, definition(data) == null ? StolenStats.ZERO : data.getSelfishEye());
        data.setMana(Math.min(data.getMana(), MadokaMath.manaMax(player)));
        data.save(player);
    }

    private static SpellDefinition definition(PlayerMadokaData data) {
        var theme = data.getTheme();
        if (!data.isTransformed() || theme == null || !theme.getPassives(data.isWitchPath()).contains(ID)) {
            return null;
        }
        SpellDefinition definition = SpellRegistry.get(ID);
        return definition != null && !definition.active() ? definition : null;
    }

    public static void refreshForm(ServerPlayer player) {
        PlayerMadokaData data = PlayerMadokaData.of(player);
        if (!data.isTransformed()) {
            finish(player);
        }
        applyPermanent(player, definition(data) == null ? StolenStats.ZERO : data.getSelfishEye());
    }

    private static void applyPermanent(ServerPlayer player, StolenStats stats) {
        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), PERMANENT, stats.attack(),
                AttributeModifier.Operation.ADDITION);
        setModifier(player.getAttribute(Attributes.MAX_HEALTH), PERMANENT, stats.health(),
                AttributeModifier.Operation.ADDITION);
        setModifier(player.getAttribute(MadokaAttributes.MAGIC_MANA.get()), PERMANENT, stats.mana(),
                AttributeModifier.Operation.ADDITION);
    }

    private static void setModifier(AttributeInstance attribute, UUID id, double value,
                                    AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && current.getAmount() == value) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(id);
        }
        if (value != 0.0D) {
            AttributeModifier modifier = new AttributeModifier(id, "Madoka Selfish Eye", value, operation);
            if (id.equals(PERMANENT)) {
                attribute.addPermanentModifier(modifier);
            } else {
                attribute.addTransientModifier(modifier);
            }
        }
    }
}
