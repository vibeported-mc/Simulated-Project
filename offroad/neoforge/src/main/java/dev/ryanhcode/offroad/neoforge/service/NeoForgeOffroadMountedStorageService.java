package dev.ryanhcode.offroad.neoforge.service;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper;
import com.simibubi.create.content.contraptions.MountedStorageManager;
import dev.ryanhcode.offroad.content.blocks.borehead_bearing.BoreheadBearingBlockEntity;
import dev.ryanhcode.offroad.content.blocks.borehead_bearing.BoreheadAttachedStorage;
import dev.ryanhcode.offroad.service.OffroadMountedStorageService;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.lang.ref.WeakReference;

public class NeoForgeOffroadMountedStorageService implements OffroadMountedStorageService {

    @Override
    public <T extends MountedStorageManager & BoreheadAttachedStorage> T getSidedBoreheadContraptionMountedStorage() {
        return (T) new NeoforgeBoreheadBearingMountedStorage();
    }

    public static class NeoforgeBoreheadBearingMountedStorage extends MountedStorageManager implements BoreheadAttachedStorage {

        public WeakReference<BoreheadBearingBlockEntity> attachedBoreheadBearing = new WeakReference<>(null);

        private boolean insertAllowed;

        @Override
        public void initialize() {
            super.initialize();

            this.items = new NeoForgeBoreheadInvWrapper(this.items);
            this.allItems = this.items;
            if (this.fuelItems != null) {
                this.fuelItems = new NeoForgeBoreheadInvWrapper(this.fuelItems);
            }
        }

        @Override
        public void attachBlockEntity(final BoreheadBearingBlockEntity be) {
            this.attachedBoreheadBearing = new WeakReference<>(be);
        }

        @Override
        public void setInsertAllowed(final boolean insertionAllowed) {
            this.insertAllowed = insertionAllowed;
        }

        @Override
        public void invokeUnstall() {
            final BoreheadBearingBlockEntity bbe = this.attachedBoreheadBearing.get();
            if (bbe != null) {
                bbe.startUnstalling();
            }
        }

        /**
         * <h2>26.2 note</h2>
         * <p>{@code MountedItemStorageWrapper} is a {@code ResourceHandler<ItemResource>} now, not an
         * {@code IItemHandler}, so the three methods this overrode are gone and the two it needs are
         * different in shape:
         *
         * <ul>
         *   <li>Simulation is a transaction rather than a flag. An insert or extract is applied when
         *       the caller commits, and this wrapper never sees that commit -- so gating on
         *       {@code insertAllowed} still works, but the unstall on a successful extract now fires
         *       when the extract is <em>attempted</em> and would move something, including during a
         *       simulation. The bearing's own guard makes a second start harmless, and the previous
         *       code had the same shape for a simulated extract that returned a non-empty stack.</li>
         *   <li>{@code setStackInSlot} became {@code IndexModifier.set}, which the parent implements
         *       and this class only forwarded, so the override is dropped rather than rewritten.</li>
         * </ul>
         */
        class NeoForgeBoreheadInvWrapper extends MountedItemStorageWrapper {

            NeoForgeBoreheadInvWrapper(final MountedItemStorageWrapper wrapped) {
                super(wrapped.storages);
            }

            @Override
            public int insert(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
                if (!NeoforgeBoreheadBearingMountedStorage.this.insertAllowed) {
                    return 0;
                }

                return super.insert(index, resource, amount, transaction);
            }

            @Override
            public int extract(final int index, final ItemResource resource, final int amount, final TransactionContext transaction) {
                final BoreheadBearingBlockEntity bbe = NeoforgeBoreheadBearingMountedStorage.this.attachedBoreheadBearing.get();
                if (bbe == null) {
                    return 0;
                }

                final int extracted = super.extract(index, resource, amount, transaction);
                if (extracted > 0) {
                    bbe.startUnstalling();
                }

                return extracted;
            }
        }
    }
}
