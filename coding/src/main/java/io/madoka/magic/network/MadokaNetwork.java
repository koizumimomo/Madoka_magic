package io.madoka.magic.network;

import io.madoka.magic.MadokaMagic;
import io.madoka.magic.data.PlayerMadokaData;
import io.madoka.magic.data.PlayerTransformation;
import io.madoka.magic.event.ContractWeaponEvents;
import io.madoka.magic.registry.MadokaAttributes;
import io.madoka.magic.spell.SpellPreview;
import io.madoka.magic.spell.SpellRegistry;
import io.madoka.magic.util.MadokaMath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * 模组自己的网络层。
 *
 * <p>服务端权威：客户端只能"请求"和"上报意图"，所有状态变更都在服务端完成。</p>
 */
public final class MadokaNetwork {

    /**
     * 协议版本。
     * <p>消息或实体同步格式变更时递增：客户端/服务端版本不一致时 Forge 会直接拒绝连接，
     * 比"字段错位读出乱数据"安全。</p>
     */
    private static final String PROTOCOL = "21";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MadokaMagic.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private MadokaNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncState.class, SyncState::encode, SyncState::decode,
                SyncState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RequestSync.class, RequestSync::encode, RequestSync::decode, RequestSync::handle);
        CHANNEL.registerMessage(id++, CastSpell.class, CastSpell::encode, CastSpell::decode, CastSpell::handle);
        CHANNEL.registerMessage(id++, ConfirmContract.class, ConfirmContract::encode, ConfirmContract::decode, ConfirmContract::handle);
        CHANNEL.registerMessage(id++, WitchChoice.class, WitchChoice::encode, WitchChoice::decode, WitchChoice::handle);
        CHANNEL.registerMessage(id++, OpenBarrier.class, OpenBarrier::encode, OpenBarrier::decode, OpenBarrier::handle);
        CHANNEL.registerMessage(id++, TransformationFx.class, TransformationFx::encode, TransformationFx::decode,
                TransformationFx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, PlayerFormState.class, PlayerFormState::encode, PlayerFormState::decode,
                PlayerFormState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ToggleTransformation.class, (msg, buffer) -> {}, buffer -> new ToggleTransformation(),
                ToggleTransformation::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, RequestSkillPreview.class, (msg, buffer) -> {}, buffer -> new RequestSkillPreview(),
                RequestSkillPreview::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SkillPreviews.class, SkillPreviews::encode, SkillPreviews::decode,
                SkillPreviews::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ButterflyMarkState.class, ButterflyMarkState::encode, ButterflyMarkState::decode,
                ButterflyMarkState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WeaponInput.class, (msg, buffer) -> buffer.writeEnum(msg.input()),
                buffer -> new WeaponInput(buffer.readEnum(ContractWeaponEvents.Input.class)),
                WeaponInput::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, NightArrival.class, (msg, buffer) -> {}, buffer -> new NightArrival(),
                NightArrival::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, RitualCraftFx.class, RitualCraftFx::encode, RitualCraftFx::decode,
                RitualCraftFx::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SpawnWitchPortal.class, SpawnWitchPortal::encode, SpawnWitchPortal::decode,
                SpawnWitchPortal::handle);
        CHANNEL.registerMessage(id++, ProgressionState.class, ProgressionState::encode, ProgressionState::decode,
                ProgressionState::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id, ProgressionState.LearnTalent.class, ProgressionState.LearnTalent::encode,
                ProgressionState.LearnTalent::decode, ProgressionState.LearnTalent::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public record NightArrival() {
        private static void handle(NightArrival message, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> io.madoka.magic.client.NightArrivalOverlay::start));
            ctx.setPacketHandled(true);
        }
    }

    public record WeaponInput(ContractWeaponEvents.Input input) {
        private static void handle(WeaponInput message, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                if (ctx.getSender() != null) {
                    ContractWeaponEvents.handle(ctx.getSender(), message.input());
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    /** 把玩家状态推给客户端（HUD / 轮盘要用）。 */
    public static void syncTo(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.connection == null
                || serverPlayer.connection.connection.channel() == null) {
            return;
        }
        PlayerMadokaData data = PlayerMadokaData.of(player);
        long gameTime = player.level().getGameTime();
        // 罪责是"现算"的（记在敌人身上）：只有赎罪系需要，别的角色省掉这次 16 格扫描
        float guiltTotal = 0.0F;
        var theme = data.getTheme();
        if (theme != null && theme.getPassives(data.isWitchPath()).contains("sin_mark")) {
            guiltTotal = io.madoka.magic.spell.GuiltMark.totalFor(serverPlayer);
        }
        SyncState packet = new SyncState(
                data.getTaint(),
                data.getLevel(),
                data.getThemeId(),
                data.isContracted(),
                data.isTransformed(),
                data.isWitchPath(),
                data.isEverContracted(),
                data.isKeepIdentity(),
                data.isPendingWitchChoice(),
                new ArrayList<>(data.getUnlockedSpells()),
                data.getMana(),
                data.getWitchDirection(),
                data.getWitchDistance(),
                attributeValue(player, MadokaAttributes.MAGIC_CAUSALITY.get()),
                MadokaMath.power(player),
                data.getBlossomPhase().ordinal(),
                data.getBlossomPhase() == PlayerMadokaData.BlossomPhase.BLOOM
                        ? io.madoka.magic.spell.BlossomState.getDamageMultiplier(data, player.level().getGameTime())
                        : 1.0F,
                data.getObsessionStacks(),
                guiltTotal,
                io.madoka.magic.spell.PromiseState.remainingTicks(data, gameTime),
                data.getPromisePool(),
                attributeValue(player, MadokaAttributes.MAGIC_MANA.get()),
                MadokaMath.progression(player),
                data.getDefeatedWitches());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        ProgressionState.send(serverPlayer);
    }

    private static double attributeValue(Player player, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = player.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    // ------------------------------------------------------------------ S2C

    /** 服务端 → 客户端：玩家状态快照。 */
    public static class SyncState {
        /** 污染值（0~100，小数）。 */
        public final float taint;
        public final int level;
        public final String themeId;
        public final boolean contracted;
        public final boolean transformed;
        public final boolean witchPath;
        public final boolean everContracted;
        public final boolean keepIdentity;
        public final boolean pendingWitchChoice;
        public final List<String> spells;
        /** 魔女相对玩家朝向的角度（度），NaN 表示附近没有魔女。 */
        public final float witchDir;
        public final float witchDist;
        public final double mana;
        public final double causality;
        public final double power;
        /** 花期阶段：0 无 / 1 绽放 / 2 花落。 */
        public final int blossomPhase;
        /** 花期当前的伤害加成倍率（1.0 = 无加成）。 */
        public final float blossomBonus;
        /** 执念层数。 */
        public final int obsessionStacks;
        /** 赎罪系：附近敌人的罪责总量（HUD 用）。 */
        public final float guiltTotal;
        /** 赎罪系：无法偿还的约定剩余 tick（0 = 没有约定）。 */
        public final int promiseTicks;
        /** 赎罪系：约定池当前积累的伤害量。 */
        public final float promisePool;
        /** 魔力上限（属性 {@code MAGIC_MANA}，HUD 魔力条用）。 */
        public final double maxMana;
        /** 当前路线的成长值，魔法少女为纯度，魔女为绝望。 */
        public final double purity;
        public final List<String> defeatedWitches;

        public SyncState(float taint, int level, String themeId, boolean contracted, boolean transformed,
                         boolean witchPath, boolean everContracted,
                         boolean keepIdentity, boolean pendingWitchChoice, List<String> spells,
                         double mana, float witchDir, float witchDist, double causality, double power,
                         int blossomPhase, float blossomBonus, int obsessionStacks,
                         float guiltTotal, int promiseTicks, float promisePool,
                         double maxMana, double purity, List<String> defeatedWitches) {
            this.taint = taint;
            this.level = level;
            this.themeId = themeId;
            this.contracted = contracted;
            this.transformed = transformed;
            this.witchPath = witchPath;
            this.everContracted = everContracted;
            this.keepIdentity = keepIdentity;
            this.pendingWitchChoice = pendingWitchChoice;
            this.spells = spells;
            this.mana = mana;
            this.witchDir = witchDir;
            this.witchDist = witchDist;
            this.causality = causality;
            this.power = power;
            this.blossomPhase = blossomPhase;
            this.blossomBonus = blossomBonus;
            this.obsessionStacks = obsessionStacks;
            this.guiltTotal = guiltTotal;
            this.promiseTicks = promiseTicks;
            this.promisePool = promisePool;
            this.maxMana = maxMana;
            this.purity = purity;
            this.defeatedWitches = List.copyOf(defeatedWitches);
        }

        public static void encode(SyncState msg, FriendlyByteBuf buf) {
            buf.writeFloat(msg.taint);
            buf.writeVarInt(msg.level);
            buf.writeUtf(msg.themeId);
            buf.writeBoolean(msg.contracted);
            buf.writeBoolean(msg.transformed);
            buf.writeBoolean(msg.witchPath);
            buf.writeBoolean(msg.everContracted);
            buf.writeBoolean(msg.keepIdentity);
            buf.writeBoolean(msg.pendingWitchChoice);
            buf.writeVarInt(msg.spells.size());
            for (String id : msg.spells) {
                buf.writeUtf(id);
            }
            buf.writeDouble(msg.mana);
            buf.writeFloat(msg.witchDir);
            buf.writeFloat(msg.witchDist);
            buf.writeDouble(msg.causality);
            buf.writeDouble(msg.power);
            buf.writeVarInt(msg.blossomPhase);
            buf.writeFloat(msg.blossomBonus);
            buf.writeVarInt(msg.obsessionStacks);
            buf.writeFloat(msg.guiltTotal);
            buf.writeVarInt(msg.promiseTicks);
            buf.writeFloat(msg.promisePool);
            buf.writeDouble(msg.maxMana);
            buf.writeDouble(msg.purity);
            buf.writeCollection(msg.defeatedWitches, FriendlyByteBuf::writeUtf);
        }

        public static SyncState decode(FriendlyByteBuf buf) {
            float taint = buf.readFloat();
            int level = buf.readVarInt();
            String themeId = buf.readUtf();
            boolean contracted = buf.readBoolean();
            boolean transformed = buf.readBoolean();
            boolean witchPath = buf.readBoolean();
            boolean ever = buf.readBoolean();
            boolean keep = buf.readBoolean();
            boolean pending = buf.readBoolean();
            // 上限保护：畸形包不该让客户端按声明长度巨量分配
            int size = Math.min(buf.readVarInt(), 64);
            List<String> spells = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                spells.add(buf.readUtf());
            }
            return new SyncState(taint, level, themeId, contracted, transformed, witchPath, ever, keep, pending, spells,
                    buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarInt(),
                    buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                    buf.readDouble(), buf.readDouble(), buf.readList(FriendlyByteBuf::readUtf));
        }

        public static void handle(SyncState msg, Supplier<NetworkEvent.Context> ctx) {
            // S2C 包：客户端类用 DistExecutor 隔离，避免服务端加载本类时触碰客户端代码
            ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.madoka.magic.client.ClientMadokaState.accept(msg)));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S

    public record RequestSkillPreview() {
        private static final Map<ServerPlayer, Integer> LAST_REQUEST = new WeakHashMap<>();

        private static void handle(RequestSkillPreview msg, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) {
                    return;
                }
                int now = player.getServer().getTickCount();
                Integer last = LAST_REQUEST.get(player);
                if (last != null && now - last < 8) {
                    return;
                }
                LAST_REQUEST.put(player, now);
                var data = PlayerMadokaData.of(player);
                var theme = data.getTheme();
                if (data.isContracted() && theme != null) {
                    Map<String, SpellPreview> previews = new HashMap<>();
                    boolean witch = data.isWitchPath();
                    java.util.stream.Stream.concat(theme.getSkills(witch).stream(), theme.getPassives(witch).stream())
                            .distinct().map(SpellRegistry::get).filter(java.util.Objects::nonNull)
                            .forEach(spell -> previews.put(spell.id(), SpellPreview.of(player, spell)));
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkillPreviews(theme.getId(), previews));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public record SkillPreviews(String theme, Map<String, SpellPreview> values) {
        private static void encode(SkillPreviews msg, FriendlyByteBuf buffer) {
            buffer.writeUtf(msg.theme());
            buffer.writeMap(msg.values(), FriendlyByteBuf::writeUtf, (buf, preview) -> preview.write(buf));
        }

        private static SkillPreviews decode(FriendlyByteBuf buffer) {
            return new SkillPreviews(buffer.readUtf(), buffer.readMap(FriendlyByteBuf::readUtf, SpellPreview::read));
        }

        private static void handle(SkillPreviews msg, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.madoka.magic.client.SkillDetails.accept(msg)));
            ctx.setPacketHandled(true);
        }
    }

    public record ToggleTransformation() {
        private static void handle(ToggleTransformation msg, Supplier<NetworkEvent.Context> context) {
            var ctx = context.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null) {
                    PlayerTransformation.toggle(player);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：请求一次状态同步。 */
    public static class RequestSync {
        public static void encode(RequestSync msg, FriendlyByteBuf buf) {
        }

        public static RequestSync decode(FriendlyByteBuf buf) {
            return new RequestSync();
        }

        public static void handle(RequestSync msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player != null) {
                    syncTo(player);
                    PlayerFormState.sendTo(player, player);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：请求施放一个主动技能。 */
    public static class CastSpell {
        private final String spellId;
        /** 是否按住 Shift 施法（凋零用它切换代价）。 */
        private final boolean shiftHeld;

        public CastSpell(String spellId) {
            this(spellId, false);
        }

        public CastSpell(String spellId, boolean shiftHeld) {
            this.spellId = spellId;
            this.shiftHeld = shiftHeld;
        }

        public static void encode(CastSpell msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.spellId);
            buf.writeBoolean(msg.shiftHeld);
        }

        public static CastSpell decode(FriendlyByteBuf buf) {
            return new CastSpell(buf.readUtf(), buf.readBoolean());
        }

        public static void handle(CastSpell msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    io.madoka.magic.spell.SpellCasting.castFromClient(sender, msg.spellId, msg.shiftHeld);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：确认契约（"你真的希望通过【X】成为魔法少女吗"）。 */
    public static class ConfirmContract {
        public static void encode(ConfirmContract msg, FriendlyByteBuf buf) {
        }

        public static ConfirmContract decode(FriendlyByteBuf buf) {
            return new ConfirmContract();
        }

        public static void handle(ConfirmContract msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    io.madoka.magic.item.ContractItem.confirmContract(sender);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：请求半魔女化。 */
    public static class OpenBarrier {
        public static void encode(OpenBarrier msg, FriendlyByteBuf buf) {
        }

        public static OpenBarrier decode(FriendlyByteBuf buf) {
            return new OpenBarrier();
        }

        public static void handle(OpenBarrier msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    io.madoka.magic.data.PlayerTransformation.beginHalfWitch(sender);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：测试工具请求在附近（8–16 格外）生成所选魔女的结界入口传送门。 */
    public static class SpawnWitchPortal {
        private final String themeId;

        public SpawnWitchPortal(String themeId) {
            this.themeId = themeId;
        }

        public static void encode(SpawnWitchPortal msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.themeId);
        }

        public static SpawnWitchPortal decode(FriendlyByteBuf buf) {
            return new SpawnWitchPortal(buf.readUtf());
        }

        public static void handle(SpawnWitchPortal msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null && sender.isCreative()) {
                    io.madoka.magic.event.PortalEvents.openNearbyEntrance(sender.serverLevel(), sender, msg.themeId);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** 客户端 → 服务端：魔女化后的去留选择。 */
    public static class WitchChoice {
        private final boolean keep;

        public WitchChoice(boolean keep) {
            this.keep = keep;
        }

        public static void encode(WitchChoice msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.keep);
        }

        public static WitchChoice decode(FriendlyByteBuf buf) {
            return new WitchChoice(buf.readBoolean());
        }

        public static void handle(WitchChoice msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sender = ctx.get().getSender();
                if (sender != null) {
                    io.madoka.magic.event.TaintEvents.applyWitchChoice(sender, msg.keep);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
