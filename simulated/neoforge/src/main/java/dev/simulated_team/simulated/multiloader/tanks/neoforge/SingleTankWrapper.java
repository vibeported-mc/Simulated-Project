package dev.simulated_team.simulated.multiloader.tanks.neoforge;

import dev.simulated_team.simulated.multiloader.tanks.CFluidType;
import dev.simulated_team.simulated.multiloader.tanks.SingleTank;
import net.createmod.catnip.api.data.Pair;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A {@link SingleTank} seen as a NeoForge fluid handler.
 *
 * <h2>26.2 note</h2>
 * <p>{@code FluidTank} and the {@code fill}/{@code drain} pair are replaced by a
 * {@code ResourceHandler<FluidResource>}: a tank is an indexed thing with one slot, a
 * {@code FluidStack} splits into a {@code FluidResource} and an amount, and simulation is a
 * transaction rather than a {@code FluidAction}.
 *
 * <p>The undo is cheap here because {@code SingleTank} already keeps a snapshot pair of its type and
 * amount, which is exactly what the journal needs.
 */
public class SingleTankWrapper implements ResourceHandler<FluidResource> {

    private final SingleTank tank;
    private final Journal journal = new Journal();

    public SingleTankWrapper(final SingleTank tank) {
        this.tank = tank;
    }

    public static FluidResource fromCType(final CFluidType type) {
        return FluidResource.of(type.fluid().builtInRegistryHolder(), type.data());
    }

    public static CFluidType toCType(final FluidResource resource) {
        return new CFluidType(resource.getFluid(), resource.getComponentsPatch());
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(final int index) {
        return fromCType(this.tank.type);
    }

    @Override
    public long getAmountAsLong(final int index) {
        return this.tank.amount;
    }

    @Override
    public long getCapacityAsLong(final int index, final FluidResource resource) {
        return this.tank.capacity;
    }

    @Override
    public boolean isValid(final int index, final FluidResource resource) {
        return true;
    }

    @Override
    public int insert(final int index, final FluidResource resource, final int amount, final TransactionContext transaction) {
        final int inserted = (int) this.tank.insert(toCType(resource), amount, true);
        if (inserted <= 0) {
            return 0;
        }

        this.journal.updateSnapshots(transaction);
        return (int) this.tank.insert(toCType(resource), amount, false);
    }

    @Override
    public int extract(final int index, final FluidResource resource, final int amount, final TransactionContext transaction) {
        final int extracted = (int) this.tank.extract(toCType(resource), amount, true);
        if (extracted <= 0) {
            return 0;
        }

        this.journal.updateSnapshots(transaction);
        return (int) this.tank.extract(toCType(resource), amount, false);
    }

    private class Journal extends SnapshotJournal<Pair<CFluidType, Long>> {

        @Override
        protected Pair<CFluidType, Long> createSnapshot() {
            return SingleTankWrapper.this.tank.createSnapshot();
        }

        @Override
        protected void revertToSnapshot(final Pair<CFluidType, Long> snapshot) {
            SingleTankWrapper.this.tank.readSnapshot(snapshot.getFirst(), snapshot.getSecond());
        }
    }
}
