package dev.simulated_team.simulated.data.advancements;

import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <h2>26.2 note</h2>
 * <p>{@code CriterionTrigger} no longer has listeners. It is down to a codec and a criterion
 * factory, and all the per-player bookkeeping this class used to do by hand -- the
 * {@code Map<PlayerAdvancements, Set<Listener>>}, the add/remove/removeAll trio, the walk to find
 * which listeners matched and award them -- now lives in {@link SimpleCriterionTrigger}, which reads
 * it from {@code PlayerAdvancements.getTriggerMapForType}.
 *
 * <p>So this keeps only what is actually its own: the trigger's identifier, and the shape of an
 * instance that tests against a list of suppliers.
 *
 * <p>{@code SimpleInstance} requires a {@code player()} predicate, which the base class evaluates
 * before awarding. These triggers never had one -- their whole test is the supplier list -- so it is
 * empty, which the base class reads as "always matches".
 */
@ParametersAreNonnullByDefault
public abstract class SimulatedCriterionTriggerBase<T extends SimulatedCriterionTriggerBase.Instance> extends SimpleCriterionTrigger<T> {

    private final Identifier id;

    public SimulatedCriterionTriggerBase(final Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return this.id;
    }

    protected void trigger(final ServerPlayer player, @Nullable final List<Supplier<Object>> suppliers) {
        super.trigger(player, instance -> instance.test(suppliers));
    }

    public abstract static class Instance implements SimpleCriterionTrigger.SimpleInstance {
        private final Identifier id;

        public Instance(final Identifier id) {
            this.id = id;
        }

        public Identifier getId() {
            return this.id;
        }

        @Override
        public Optional<ContextAwarePredicate> player() {
            return Optional.empty();
        }

        protected abstract boolean test(@Nullable List<Supplier<Object>> suppliers);
    }
}
