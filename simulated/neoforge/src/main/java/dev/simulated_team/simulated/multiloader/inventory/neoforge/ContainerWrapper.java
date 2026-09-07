package dev.simulated_team.simulated.multiloader.inventory.neoforge;

import dev.simulated_team.simulated.multiloader.inventory.AbstractContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A {@link AbstractContainer} seen as a NeoForge item handler.
 *
 * <h2>26.2 note</h2>
 * <p>The capability is a {@code ResourceHandler<ItemResource>} rather than an
 * {@code IItemHandlerModifiable}, and the two differ in more than shape:
 *
 * <ul>
 *   <li><b>A slot holds a resource and an amount</b>, not a stack, so the container's stacks are
 *       taken apart on the way out and rebuilt on the way in.</li>
 *   <li><b>Simulation is a transaction rather than a boolean.</b> A handler mutates immediately and
 *       registers a snapshot so a rolled-back transaction is undone; that is what the journal below
 *       is for. Snapshotting the whole container would be wasteful, so each slot keeps its own
 *       journal, holding the one stack that slot is about to lose.</li>
 *   <li><b>{@code setStackInSlot}</b> became {@link IndexModifier#set}, which is the direct write
 *       that bypasses the transaction entirely -- the same thing it always was.</li>
 * </ul>
 */
public class ContainerWrapper<T extends AbstractContainer> implements ResourceHandler<ItemResource>, IndexModifier<ItemResource> {

    private final T container;
    private final SlotJournal[] journals;

    public ContainerWrapper(final T container) {
        this.container = container;
        this.journals = new SlotJournal[container.getContainerSize()];
    }

    private SlotJournal journal(final int index) {
        SlotJournal journal = this.journals[index];
        if (journal == null) {
            journal = new SlotJournal(this.container, index);
            this.journals[index] = journal;
        }
        return journal;
    }

    @Override
    public int size() {
        return this.container.getContainerSize();
    }

    @Override
    public ItemResource getResource(final int index) {
        return ItemResource.of(this.container.getItem(index));
    }

    @Override
    public long getAmountAsLong(final int index) {
        return this.container.getItem(index).getCount();
    }

    @Override
    public long getCapacityAsLong(final int index, final ItemResource resource) {
        return this.container.getMaxStackSize();
    }

    @Override
    public boolean isValid(final int index, final ItemResource resource) {
        return true;
    }

    @Override
    public int insert(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }

        final ItemStack existing = this.container.getItem(index);
        if (!existing.isEmpty() && !resource.matches(existing)) {
            return 0;
        }

        final int limit = Math.min(this.container.getMaxStackSize(), resource.toStack(1).getMaxStackSize());
        final int inserted = Math.min(amount, limit - existing.getCount());
        if (inserted <= 0) {
            return 0;
        }

        this.journal(index).updateSnapshots(transaction);
        this.container.setItem(index, resource.toStack(existing.getCount() + inserted));
        return inserted;
    }

    @Override
    public int extract(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }

        final ItemStack existing = this.container.getItem(index);
        if (existing.isEmpty() || !resource.matches(existing)) {
            return 0;
        }

        final int extracted = Math.min(amount, existing.getCount());

        this.journal(index).updateSnapshots(transaction);
        final int remaining = existing.getCount() - extracted;
        this.container.setItem(index, remaining == 0 ? ItemStack.EMPTY : resource.toStack(remaining));
        return extracted;
    }

    @Override
    public void set(final int index, final ItemResource resource, final int amount) {
        this.container.setItem(index, resource.isEmpty() || amount <= 0 ? ItemStack.EMPTY : resource.toStack(amount));
    }

    /**
     * One slot's worth of undo. The snapshot is the stack that slot held before the transaction
     * touched it; reverting puts it back.
     */
    private static class SlotJournal extends SnapshotJournal<ItemStack> {

        private final AbstractContainer container;
        private final int index;

        private SlotJournal(final AbstractContainer container, final int index) {
            this.container = container;
            this.index = index;
        }

        @Override
        protected ItemStack createSnapshot() {
            return this.container.getItem(this.index).copy();
        }

        @Override
        protected void revertToSnapshot(final ItemStack snapshot) {
            this.container.setItem(this.index, snapshot);
        }
    }
}
