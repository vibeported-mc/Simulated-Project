package dev.ryanhcode.offroad.neoforge.mixin_helpers;

import java.lang.ref.WeakReference;

import com.simibubi.create.content.contraptions.Contraption;

import dev.ryanhcode.offroad.content.blocks.borehead_bearing.BoreheadAttachedStorage;
import dev.ryanhcode.offroad.content.contraptions.borehead_contraption.BoreheadBearingContraption;
import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A borehead bearing's mounted storage, watched so the bearing unstalls when something is taken out
 * of it.
 *
 * <h2>26.2 note</h2>
 * <p>{@code getHandlerForMenu} hands back a {@code ResourceHandler<ItemResource>} rather than an
 * {@code IItemHandlerModifiable}, so this delegates across that interface instead. Create's own
 * {@code MountedItemStorage} notes that every mounted storage provides both halves and picks the
 * writable one out by instance check, so this implements both to stay substitutable for what it
 * wraps.
 *
 * <p>{@code setStackInSlot} became {@link IndexModifier#set}, and it is only forwarded when the
 * wrapped handler is itself writable -- the same test Create makes.
 *
 * <p>One behavioural note. The unstall used to fire when an extract actually returned something.
 * Extraction is transactional now and this wrapper never sees the commit, so it fires when an
 * extract is attempted and would move something -- including during a simulation. The bearing's own
 * guard makes a second start harmless, which is the same trade the mounted storage service makes.
 */
public class WrappedWrappedMountedItemStorage implements ResourceHandler<ItemResource>, IndexModifier<ItemResource> {

    private final WeakReference<Contraption> associatedContraption;
    private final ResourceHandler<ItemResource> wrappedInv;

    public WrappedWrappedMountedItemStorage(final WeakReference<Contraption> associatedContraption, final ResourceHandler<ItemResource> wrappedInv) {
        this.associatedContraption = associatedContraption;
        this.wrappedInv = wrappedInv;
    }

    private void unstall() {
        final Contraption contraption = this.associatedContraption.get();
        if (contraption instanceof final BoreheadBearingContraption bce) {
            ((BoreheadAttachedStorage) bce.getStorage()).invokeUnstall();
        }
    }

    @Override
    public void set(final int index, final ItemResource resource, final int amount) {
        if (resource.isEmpty() || amount <= 0) {
            this.unstall();
        }

        if (this.wrappedInv instanceof final IndexModifier<?> modifier) {
            @SuppressWarnings("unchecked")
            final IndexModifier<ItemResource> writable = (IndexModifier<ItemResource>) modifier;
            writable.set(index, resource, amount);
        }
    }

    @Override
    public int size() {
        return this.wrappedInv.size();
    }

    @Override
    public ItemResource getResource(final int index) {
        return this.wrappedInv.getResource(index);
    }

    @Override
    public long getAmountAsLong(final int index) {
        return this.wrappedInv.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(final int index, final ItemResource resource) {
        return this.wrappedInv.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(final int index, final ItemResource resource) {
        return this.wrappedInv.isValid(index, resource);
    }

    @Override
    public int insert(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
        return this.wrappedInv.insert(index, resource, amount, transaction);
    }

    @Override
    public int extract(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
        final int extracted = this.wrappedInv.extract(index, resource, amount, transaction);
        if (extracted > 0) {
            this.unstall();
        }

        return extracted;
    }
}
