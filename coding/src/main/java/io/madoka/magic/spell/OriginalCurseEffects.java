package io.madoka.magic.spell;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.CurseDebtState;
import io.madoka.magic.data.CurseStackState;
import io.madoka.magic.data.OriginalCurseDefinitions;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.item.CurseRelic;
import io.madoka.magic.network.MadokaNetwork;
import io.madoka.magic.registry.ModParticles;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Original relic passives. Deferred damage has its own persisted queue and never re-enters that queue. */
@Mod.EventBusSubscriber(modid=MadokaMagic.MODID)
public final class OriginalCurseEffects {
    public static final String COOLDOWN_ID = "curse:atonement_ward";
    private static final String KEY = "madoka_original_curses";
    private static final String MARKS = "madoka_curse_butterflies";
    public static final ResourceKey<DamageType> DEBT = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MadokaMagic.MODID,"curse_debt"));

    private OriginalCurseEffects() {}
    private record Active(String theme, ItemStack stack, int rank, double matching,
                          String session, OriginalCurseDefinitions.Definition definition) {}

    private static CompoundTag state(ServerPlayer player) {
        var root=player.getPersistentData(); var state=root.getCompound(KEY); root.put(KEY,state); return state;
    }

    private static Active active(ServerPlayer player) {
        var state=state(player);
        var item=player.isAlive()&&!player.isSpectator()&&PlayerMadokaData.of(player).isTransformed()
                ?CurseStackState.equipped(player):ItemStack.EMPTY;
        if (!(item.getItem() instanceof CurseRelic relic) || OriginalCurseDefinitions.get(relic.theme())==null) {
            state.remove("activeIdentity");state.remove("session");return null;
        }
        var tag=item.getOrCreateTag();
        if (!tag.hasUUID("madoka_curse_identity"))tag.putUUID("madoka_curse_identity",UUID.randomUUID());
        UUID identity=tag.getUUID("madoka_curse_identity");
        if (!state.hasUUID("activeIdentity")||!identity.equals(state.getUUID("activeIdentity"))) {
            state.putUUID("activeIdentity",identity);state.putString("session",UUID.randomUUID().toString());
        }
        if(tag.getInt("madoka_curse_definition_revision")!=OriginalCurseDefinitions.revision())
            tag.putInt("madoka_curse_definition_revision",OriginalCurseDefinitions.revision());
        var def=OriginalCurseDefinitions.get(relic.theme());
        double matching=relic.theme().equals(PlayerMadokaData.of(player).getThemeId())?def.matching():1;
        return new Active(relic.theme(),item,CurseRelic.level(item),matching,state.getString("session"),def);
    }

    public static boolean isSettlement(DamageSource source) {return source.is(DEBT);}
    private static boolean debt(Active a) {return a!=null&&a.definition().kind().equals("debt");}
    private static long now(ServerPlayer p) {return p.getServer().overworld().getGameTime();}
    private static ServerPlayer owner(Entity entity) {
        if(entity instanceof ServerPlayer p)return p;
        return entity instanceof OwnableEntity pet&&pet.getOwner() instanceof ServerPlayer p?p:null;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void ward(LivingAttackEvent event) {
        if(event.getEntity() instanceof ServerPlayer p && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && state(p).getLong("wardUntil")>now(p))event.setCanceled(true);
    }

    @SubscribeEvent(priority=EventPriority.LOW)
    public static void damage(LivingDamageEvent event) {
        if(event.getAmount()<=0 || !Float.isFinite(event.getAmount()) || isSettlement(event.getSource()))return;
        var attacker=owner(event.getSource().getEntity());
        if(attacker!=null && attacker!=event.getEntity() && !(event.getEntity() instanceof ArmorStand)) {
            var a=active(attacker);
            if(a!=null&&a.definition().kind().equals("butterfly"))butterfly(attacker,a,event);
        }
        if(!(event.getEntity() instanceof ServerPlayer p)
                ||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        var a=active(p);
        if(!debt(a))return;
        var state=state(p);
        var entries=state.getList("debts",Tag.TAG_COMPOUND);
        long due=now(p)+Math.max(1,Math.round(a.definition().value("delay_ticks",a.rank())*a.matching()));
        var entry=new CompoundTag();entry.putDouble("amount",event.getAmount());entry.putLong("due",due);
        if(event.getSource().getEntity()!=null)entry.putUUID("attacker",event.getSource().getEntity().getUUID());
        if(entries.size()>=256) {
            var last=entries.getCompound(entries.size()-1);
            last.putDouble("amount",last.getDouble("amount")+entry.getDouble("amount"));
            last.putLong("due",Math.min(last.getLong("due"),due));
        } else entries.add(entry);
        state.put("debts",entries);
        event.setAmount(0);
        updateDebt(p,entries);
    }

    private static void butterfly(ServerPlayer p,Active a,LivingDamageEvent event) {
        var victim=event.getEntity();var root=victim.getPersistentData().getCompound(MARKS);
        long tick=now(p);
        for(String key:java.util.Set.copyOf(root.getAllKeys()))if(root.getCompound(key).getLong("until")<=tick)root.remove(key);
        String key=p.getUUID().toString();var mark=root.getCompound(key);
        boolean burst=a.session().equals(mark.getString("session"))&&mark.getLong("until")>tick;
        if(burst) {
            double ratio=a.definition().value("echo_ratio",a.rank())*a.matching();
            event.setAmount((float)(event.getAmount()*(1+ratio)));
            root.remove(key);
        } else {
            mark=new CompoundTag();mark.putString("session",a.session());
            mark.putLong("until",tick+Math.max(1,Math.round(a.definition().value("mark_ticks",a.rank()))));
            root.put(key,mark);
        }
        victim.getPersistentData().put(MARKS,root);
        p.serverLevel().sendParticles(ModParticles.Devotion.BUTTERFLY.get(),
                victim.getX(),victim.getY()+victim.getBbHeight()*.6,victim.getZ(),burst?4:1,.25,.25,.25,.02);
        if(burst)p.serverLevel().playSound(null,victim.blockPosition(),SoundEvents.AMETHYST_BLOCK_HIT,SoundSource.PLAYERS,.5f,1.5f);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void kill(LivingDeathEvent event) {
        if(event.getEntity() instanceof ServerPlayer dead) {
            var state=state(dead);state.remove("debts");state.remove("wardUntil");
        }
        var p=owner(event.getSource().getEntity());
        if(p==null||!p.isAlive()||p==event.getEntity()||event.getEntity() instanceof ArmorStand
                ||event.getEntity() instanceof OwnableEntity pet&&p.getUUID().equals(pet.getOwnerUUID()))return;
        var a=active(p);if(a==null)return;
        if(a.definition().kind().equals("honey")) {
            double total=p.getMaxHealth()*a.definition().value("heal_fraction",a.rank())*a.matching();
            double heal=Math.min(total,Math.max(0,p.getMaxHealth()-p.getHealth()));p.heal((float)heal);
            var data=PlayerMadokaData.of(p);
            data.setMana(Math.min(MadokaMath.manaMax(p),data.getMana()+Math.max(0,total-heal)
                    *a.definition().value("overflow_mana_ratio",a.rank())));data.save(p);
            p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,p.getX(),p.getY()+1,p.getZ(),6,.4,.5,.4,.03);
            MadokaNetwork.syncTo(p);
        } else if(debt(a)) {
            var entries=state(p).getList("debts",Tag.TAG_COMPOUND);
            double kept=1-Math.min(1,a.definition().value("kill_clear_fraction",a.rank())*a.matching());
            if(kept<=0)entries=new ListTag();
            else for(int i=0;i<entries.size();i++) {
                var entry=entries.getCompound(i);entry.putDouble("amount",entry.getDouble("amount")*kept);
            }
            state(p).put("debts",entries);updateDebt(p,entries);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        var a=active(p);var state=state(p);var entries=state.getList("debts",Tag.TAG_COMPOUND);
        if(entries.isEmpty())return;
        if(!p.isAlive()){state.remove("debts");return;}
        var left=new ListTag();var due=new ListTag();
        for(int i=0;i<entries.size();i++) {
            var entry=entries.getCompound(i);
            (entry.getLong("due")>now(p)?left:due).add(entry.copy());
        }
        if(due.isEmpty())return;
        state.put("debts",left);
        settle(p,a,due);
        updateDebt(p,state(p).getList("debts",Tag.TAG_COMPOUND));
    }

    private static double total(ListTag entries) {
        double value=0;for(int i=0;i<entries.size();i++)value+=Math.max(0,entries.getCompound(i).getDouble("amount"));return value;
    }

    public static double pendingDebt(ServerPlayer p) {
        return total(p.getPersistentData().getCompound(KEY).getList("debts",Tag.TAG_COMPOUND));
    }

    private static void updateDebt(ServerPlayer p,ListTag entries) {CurseDebtState.setAmount(p,total(entries));}

    private static void settle(ServerPlayer p,Active a,ListTag due) {
        double amount=total(due);if(amount<=0)return;
        var state=state(p);
        if(debt(a)&&a.rank()>=Math.max(1,a.definition().value("ward_level",a.rank()))
                &&amount>=p.getHealth()&&now(p)>=state.getLong("wardCooldown")) {
            int duration=(int)Math.max(1,Math.round(a.definition().value("ward_ticks",a.rank())*a.matching()));
            int cooldown=(int)Math.max(1,Math.round(a.definition().value("ward_cooldown_ticks",a.rank())));
            state.putLong("wardUntil",now(p)+duration);state.putLong("wardCooldown",now(p)+cooldown);
            state.putInt("wardCooldownTotal",cooldown);state.put("debts",new ListTag());
            p.serverLevel().sendParticles(ParticleTypes.END_ROD,p.getX(),p.getY()+1,p.getZ(),20,.5,.7,.5,.04);
            p.serverLevel().playSound(null,p.blockPosition(),SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,.8f,.65f);
            MadokaNetwork.syncTo(p);return;
        }
        var sourceEntry=due.getCompound(due.size()-1);Entity attacker=null;
        if(sourceEntry.hasUUID("attacker")) {
            var uuid=sourceEntry.getUUID("attacker");attacker=p.serverLevel().getEntity(uuid);
            if(attacker==null)attacker=p.getServer().getPlayerList().getPlayer(uuid);
        }
        var type=p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DEBT);
        float absorption=p.getAbsorptionAmount();p.setAbsorptionAmount(0);
        try {p.hurt(new DamageSource(type,attacker), (float)amount);}
        finally {p.setAbsorptionAmount(absorption+p.getAbsorptionAmount());}
    }

    public static void addCooldowns(ServerPlayer p,CompoundTag result) {
        var a=active(p);if(!debt(a))return;
        var state=state(p);long remaining=state.getLong("wardCooldown")-now(p);if(remaining<=0)return;
        var cd=new CompoundTag();cd.putLong("remaining",remaining);cd.putInt("total",state.getInt("wardCooldownTotal"));
        result.put(COOLDOWN_ID,cd);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var old=event.getOriginal().getPersistentData().getCompound(KEY);
        var copied=new CompoundTag();copied.putLong("wardCooldown",old.getLong("wardCooldown"));
        copied.putInt("wardCooldownTotal",old.getInt("wardCooldownTotal"));
        event.getEntity().getPersistentData().put(KEY,copied);
    }
}
