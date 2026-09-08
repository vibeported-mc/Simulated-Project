package dev.simulated_team.simulated.data.advancements;

import com.tterrag.registrate.util.entry.ItemProviderEntry;
import dev.simulated_team.simulated.util.SimColors;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.*;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.ItemUsedOnLocationTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import net.minecraft.advancements.triggers.Criterion;

// i pretty much yoinked everything from Create's advancement/trigger stuff
// the only notable difference is it's not hardcoded to simulated, and addons *should* be able to use it
public class SimulatedAdvancement {
    static final String SECRET_SUFFIX = "\n§7(Hidden Advancement)";

    private final Advancement.Builder builder;
    private SimpleSimulatedTrigger builtinTrigger;
    private SimulatedAdvancement parent;

    AdvancementHolder datagenResult;

    /**
     * <h2>26.2 note</h2>
     * <p>The builder's {@code display} is filled in when the advancement is saved rather than when it
     * is declared. Declaring one is class initialisation, which runs during registration; naming an
     * item there asks the item registry for a value it has not bound yet, which throws and takes the
     * whole registration with it. Saving happens during datagen, by which point everything exists.
     */
    private Builder display;

    private final Identifier background;
    private final String lang;

    private final String id;
    private final String modid;
    private String title;
    private String description;

    public SimulatedAdvancement(final String id, final UnaryOperator<Builder> b, final Identifier background, final String modid, final BiFunction<String,String, SimpleSimulatedTrigger> triggerHandler) {
        this.builder = Advancement.Builder.advancement();
        this.id = id;
        this.modid = modid;
        this.background = background;
        this.lang = "advancement." + modid + ".";

        final Builder t = new Builder();
        b.apply(t);

        if(!t.externalTrigger) {
            this.builtinTrigger = triggerHandler.apply(modid,id + "_builtin");
            this.builder.addCriterion("0", this.builtinTrigger.createCriterion(this.builtinTrigger.instance()));
        }

        this.display = t;

        if(t.type == TaskType.SECRET)
            this.description += SECRET_SUFFIX;
    }

    private String titleKey() {
        return this.lang + this.id;
    }

    private String descriptionKey() {
        return this.titleKey() + ".desc";
    }

    public boolean isAlreadyAwardedTo(final Player player) {
        if (!(player instanceof final ServerPlayer sp))
            return true;
        final AdvancementHolder advancement = sp.level().getServer()
                .getAdvancements()
                .get(Identifier.fromNamespaceAndPath(this.modid, this.id));
        if (advancement == null)
            return true;
        return sp.getAdvancements()
                .getOrStartProgress(advancement)
                .isDone();
    }

    public void awardTo(final Player player) {
        if(this.isAlreadyAwardedTo(player)) return;
        if (!(player instanceof final ServerPlayer sp)) {
            return;
        }
        if (this.builtinTrigger == null) {
            throw new UnsupportedOperationException("Advancement " + this.id + " uses external Triggers, it cannot be awarded directly");
        }

        this.builtinTrigger.trigger(sp);
    }

    /**
     * Only attempts to award nearby players every x amount of ticks, use this when you need to award something in a method called every tick
     */
    public void awardToNearby(final BlockPos pos, final Level level, final int ticks, final double radius) {
        if(level.getGameTime() % ticks == 0) {
            this.awardToNearby(pos, level, radius);
        }
    }

    public void awardToNearby(final BlockPos pos, final Level level) {
        this.awardToNearby(pos, level, 10);
    }
    public void awardToNearby(final BlockPos pos, final Level level, final double radius) {
        final AABB aabb = new AABB(pos).inflate(radius);
        final List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, aabb);
        for (final Player player : nearbyPlayers) {
            this.awardTo(player);
        }
    }

    public void save(final Consumer<AdvancementHolder> t) {
        // 26.2: the icon is an ItemStackTemplate, and the overload taking a stack is gone.
        this.builder.display(this.display.icon.get(), Component.translatable(this.titleKey()),
                Component.translatable(this.descriptionKey()).withStyle(st -> st.withColor(SimColors.ADVANCABLE_GOLD)),
                this.id.equals("root") ? this.background : null, this.display.type.advancementType,
                this.display.type.toast, this.display.type.announce, this.display.type.hide);

        if (this.parent != null)
            this.builder.parent(this.parent.datagenResult);

        this.datagenResult = this.builder.save(t, Identifier.fromNamespaceAndPath(this.modid, this.id)
                .toString());
    }

    public void provideLang(final BiConsumer<String, String> consumer) {
        consumer.accept(this.titleKey(), this.title);
        consumer.accept(this.descriptionKey(), this.description);
    }

    /**
     * <strong>Silent</strong> - No toast or chat announcement<br>
     * <strong>Normal</strong> - Toast but no chat announcement (default)<br>
     * <strong>Noisy </strong> - Toast and chat announcement<br>
     * <strong>Expert</strong> - Toast and chat announcement, but purple<br>
     * <strong>Secret</strong> - Toast and chat announcement, but purple and hidden<br>
     */
    public enum TaskType {

        SILENT(AdvancementType.TASK, false, false, false),
        NORMAL(AdvancementType.TASK, true, false, false),
        NOISY(AdvancementType.TASK, true, true, false),
        EXPERT(AdvancementType.GOAL, true, true, false),
        SECRET(AdvancementType.GOAL, true, true, true),

        ;

        private final AdvancementType advancementType;
        private final boolean toast;
        private final boolean announce;
        private final boolean hide;

        TaskType(final AdvancementType advancementType, final boolean toast, final boolean announce, final boolean hide) {
            this.advancementType = advancementType;
            this.toast = toast;
            this.announce = announce;
            this.hide = hide;
        }
    }
    public class Builder {

        private TaskType type = TaskType.NORMAL;
        private boolean externalTrigger;
        private int keyIndex;
        /**
         * <h2>26.2 note</h2>
         * <p>Held as a supplier of the item rather than as a stack. An advancement icon only ever
         * uses the item -- the three places below all call {@code getItem()} -- and building a stack
         * eagerly asks the item registry for its default components, which are not bound while the
         * advancements are being declared. That threw "Trying to access unbound value" and took the
         * whole registration down with it.
         *
         * <p>Create's own builder carries the same note on its {@code icon(ItemStack)} overload.
         */
        private Supplier<Item> icon;

        public Builder special(final TaskType type) {
            this.type = type;
            return this;
        }

        public Builder after(final SimulatedAdvancement other) {
            SimulatedAdvancement.this.parent = other;
            return this;
        }

        public Builder icon(final ItemProviderEntry<?, ?> item) {
            this.icon = item::asItem;
            return this;
        }

        public Builder icon(final ItemLike item) {
            this.icon = item::asItem;
            return this;
        }

        public Builder icon(final ItemStack stack) {
            this.icon = stack::getItem;
            return this;
        }

        public Builder title(final String title) {
            SimulatedAdvancement.this.title = title;
            return this;
        }

        public Builder description(final String description) {
            SimulatedAdvancement.this.description = description;
            return this;
        }

        public Builder whenBlockPlaced(final Block block) {
            return this.externalTrigger(ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(block));
        }

        public Builder whenIconCollected() {
            return this.externalTrigger(InventoryChangeTrigger.TriggerInstance.hasItems(this.icon.get()));
        }

        public Builder whenIconPlaced() {
            if(this.icon.get() instanceof final BlockItem blockItem) {
                return this.externalTrigger(ItemUsedOnLocationTrigger.TriggerInstance.placedBlock(blockItem.getBlock()));
            }
            return this.whenIconCollected();
        }

        public Builder whenItemCollected(final ItemProviderEntry<?, ?> item) {
            return this.whenItemCollected(item.asStack()
                    .getItem());
        }

        public Builder whenItemCollected(final ItemLike itemProvider) {
            return this.externalTrigger(InventoryChangeTrigger.TriggerInstance.hasItems(itemProvider));
        }

        public Builder whenItemCollected(final TagKey<Item> tag) {
            return this.externalTrigger(InventoryChangeTrigger.TriggerInstance
                    .hasItems(ItemPredicate.Builder.item()
                    // 26.2: a tag predicate resolves through the item registry rather than holding
                    // the tag key, so the lookup has to be handed in.
                    .of(BuiltInRegistries.ITEM, tag).build()));
        }

        public  Builder awardedForFree() {
            return this.externalTrigger(InventoryChangeTrigger.TriggerInstance.hasItems(new ItemLike[] {}));
        }

        public Builder externalTrigger(final Criterion<? extends CriterionTriggerInstance> trigger) {
            SimulatedAdvancement.this.builder.addCriterion(String.valueOf(this.keyIndex), trigger);
            this.externalTrigger = true;
            this.keyIndex++;
            return this;
        }
    }
}
