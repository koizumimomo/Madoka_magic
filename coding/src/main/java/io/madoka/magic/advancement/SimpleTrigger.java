package io.madoka.magic.advancement;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 无条件的进度触发器：注册一个 id，代码在事件点上调 {@link #grant(ServerPlayer)} 就发放。
 *
 * <p>1.20.1 的自定义触发器走 {@code CriteriaTriggers.register}，不经过注册表，也没有 codec。</p>
 */
public final class SimpleTrigger extends SimpleCriterionTrigger<SimpleTrigger.Instance> {

    private final ResourceLocation id;

    public SimpleTrigger(ResourceLocation id) {
        this.id = id;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        return new Instance(id, player);
    }

    public void grant(ServerPlayer player) {
        if (player != null) {
            trigger(player, instance -> true);
        }
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        public Instance(ResourceLocation criterion, ContextAwarePredicate player) {
            super(criterion, player);
        }
    }
}
