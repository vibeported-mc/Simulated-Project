package dev.simulated_team.simulated.multiloader.inventory.neoforge;

import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import dev.simulated_team.simulated.multiloader.inventory.InventoryLoaderWrapper;
import dev.simulated_team.simulated.multiloader.inventory.ItemInfoWrapper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.NotNull;

/**
 * <h2>26.2 note</h2>
 * <p>{@code IItemHandler} is replaced by {@code ResourceHandler<ItemResource>}, and the change is
 * more than a rename:
 *
 * <ul>
 *   <li><b>Simulation is a transaction, not a flag.</b> Every mutating call takes a
 *       {@code TransactionContext}; a transaction closed without {@code commit()} is rolled back,
 *       which is what {@code simulate = true} used to mean. {@code ItemUtil} wraps both insert forms
 *       for exactly this, so those two call sites keep reading as one line.</li>
 *   <li><b>A slot holds a resource and an amount, not a stack.</b> {@code getStackInSlot} became
 *       {@code ItemUtil.getStack}, which builds a <em>new</em> stack from the two -- so the result is
 *       a copy, and writing to it changes nothing. Nothing here relied on that, but callers of
 *       {@link #getItem} should not either.</li>
 *   <li><b>Insert and extract return the amount moved</b> rather than a leftover or an extracted
 *       stack, which removes the reason for the note that used to sit on {@link #insertGeneral}:
 *       there is no "returns EMPTY when it took everything" convention left to work around.</li>
 * </ul>
 */
public class InventoryLoaderWrapperImpl extends InventoryLoaderWrapper {

    private final ResourceHandler<ItemResource> attachedInventory;

    public InventoryLoaderWrapperImpl(final ResourceHandler<ItemResource> attachedInventory) {
        this.attachedInventory = attachedInventory;
    }

    @Override
    public ItemStack extractAny(final int maxAmount, final boolean simulate, final boolean exact) {
        final ItemStack extracted = ItemHelper.extract(this.attachedInventory, $ -> true, exact ? ExtractionCountMode.EXACTLY : ExtractionCountMode.UPTO, maxAmount, simulate);
        if (this.callback != null && !extracted.isEmpty() && !simulate) {
            this.callback.accept(true);
        }

        return extracted;
    }

    @Override
    public int insertGeneral(final ItemInfoWrapper info, final int amountToInsert, final boolean simulate) {
        final ItemStack is = ItemInfoWrapper.generateFromInfo(info);
        is.setCount(amountToInsert);

        final int amountInserted = amountToInsert
                - ItemUtil.insertItemReturnRemaining(this.attachedInventory, is, simulate, null).getCount();
        if (this.callback != null && amountInserted > 0 && !simulate) {
            this.callback.accept(false);
        }

        return amountInserted;
    }

    @Override
    public ItemStack insertSlot(final ItemStack stack, final int slot, final boolean simulate) {
        final ItemStack inserted = ItemUtil.insertItemReturnRemaining(this.attachedInventory, slot, stack, simulate, null);
        if (this.callback != null && !stack.equals(inserted) && !simulate) {
            this.callback.accept(false);
        }

        return inserted;
    }

    @Override
    public int extractGeneral(final ItemInfoWrapper info, final int amountToExtract, final boolean simulate) {
        final int extractAmount = ItemHelper.extract(this.attachedInventory, $ -> $.getItem() == info.type(), ExtractionCountMode.UPTO, amountToExtract, simulate).getCount();
        if (this.callback != null && extractAmount > 0 && !simulate) {
            this.callback.accept(true);
        }

        return extractAmount;
    }

    @Override
    public ItemStack extractSlot(final int index, final int amountToExtract, final boolean simulate) {
        final ItemResource resource = this.attachedInventory.getResource(index);
        if (resource.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack extracted;
        try (Transaction transaction = Transaction.openRoot()) {
            final int moved = this.attachedInventory.extract(index, resource, amountToExtract, transaction);
            if (!simulate) {
                transaction.commit();
            }
            extracted = moved <= 0 ? ItemStack.EMPTY : resource.toStack(moved);
        }

        if (this.callback != null && !extracted.isEmpty() && !simulate) {
            this.callback.accept(true);
        }

        return extracted;
    }

    @Override
    public int getContainerSize() {
        return this.attachedInventory.size();
    }

    @Override
    public int getMaxStackSize() {
        // 26.2: a slot's capacity depends on what is being put in it, so it is asked about a
        // resource. EMPTY is what Create asks with when it wants the slot's own limit.
        return this.attachedInventory.getCapacityAsInt(0, ItemResource.EMPTY);
    }

    @Override
    public @NotNull ItemStack getItem(final int slot) {
        return ItemUtil.getStack(this.attachedInventory, slot);
    }
}
