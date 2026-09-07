package dev.simulated_team.simulated.data.advancements;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class SimpleSimulatedTrigger extends SimulatedCriterionTriggerBase<SimulatedCriterionTriggerBase.Instance> {
    public SimpleSimulatedTrigger(final Identifier id) {
        super(id);
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code SimpleCriterionTrigger} gained a {@code trigger(ServerPlayer, Predicate)} of its
     * own, so a bare {@code null} no longer picks between it and the base-class overload.
     */
    public void trigger(final ServerPlayer player) {
        super.trigger(player, (java.util.List<java.util.function.Supplier<Object>>) null);
    }

    public Instance instance() {
        return new Instance(this.getId());
    }

    @Override
    public @NotNull Codec<SimulatedCriterionTriggerBase.Instance> codec() {
        return Identifier.CODEC.xmap(Instance::new, SimulatedCriterionTriggerBase.Instance::getId);
    }

    public static class Instance extends SimulatedCriterionTriggerBase.Instance {

        public Instance(final Identifier id) {
            super(id);
        }

        @Override
        protected boolean test(@Nullable final List<Supplier<Object>> suppliers) {
            return true;
        }

        @Override
        public void validate(@NotNull final ValidationContextSource criterionValidator) {}
    }
}