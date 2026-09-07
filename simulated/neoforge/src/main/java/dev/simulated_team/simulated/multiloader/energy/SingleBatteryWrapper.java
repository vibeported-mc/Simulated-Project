package dev.simulated_team.simulated.multiloader.energy;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A {@link SingleBattery} seen as a NeoForge energy handler.
 *
 * <h2>26.2 note</h2>
 * <p>{@code IEnergyStorage} became {@code EnergyHandler}, and simulation is a transaction rather
 * than a boolean: a handler moves the energy immediately and registers a snapshot so a rolled-back
 * transaction is undone. The battery's own {@code simulate} flag is still what computes how much
 * would move, so it does the arithmetic and the journal carries the undo.
 *
 * <p>{@code canExtract} and {@code canReceive} are gone -- a handler that refuses says so by moving
 * nothing, which is what the two guards below do.
 */
public class SingleBatteryWrapper implements EnergyHandler {

    private final SingleBattery battery;
    private final Journal journal = new Journal();

    public SingleBatteryWrapper(final SingleBattery battery) {
        this.battery = battery;
    }

    @Override
    public long getAmountAsLong() {
        return this.battery.getEnergy();
    }

    @Override
    public long getCapacityAsLong() {
        return this.battery.maxEnergy;
    }

    @Override
    public int insert(final int amount, final TransactionContext transaction) {
        if (!this.battery.canReceive()) {
            return 0;
        }

        final int received = this.battery.receiveEnergy(amount, true);
        if (received <= 0) {
            return 0;
        }

        this.journal.updateSnapshots(transaction);
        return this.battery.receiveEnergy(amount, false);
    }

    @Override
    public int extract(final int amount, final TransactionContext transaction) {
        if (!this.battery.canExtract()) {
            return 0;
        }

        final int extracted = this.battery.extractEnergy(amount, true);
        if (extracted <= 0) {
            return 0;
        }

        this.journal.updateSnapshots(transaction);
        return this.battery.extractEnergy(amount, false);
    }

    private class Journal extends SnapshotJournal<Integer> {

        @Override
        protected Integer createSnapshot() {
            return SingleBatteryWrapper.this.battery.getEnergy();
        }

        @Override
        protected void revertToSnapshot(final Integer snapshot) {
            SingleBatteryWrapper.this.battery.setEnergy(snapshot);
        }
    }
}
