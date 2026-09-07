package dev.simulated_team.simulated.content.linked_typewriter;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

//TODO:figure out where items are rendered and change it
public class LinkedTypewriterMenuImpl extends LinkedTypewriterMenuCommon {

    public LinkedTypewriterMenuImpl(final MenuType<?> type, final int id, final Inventory inv, final RegistryFriendlyByteBuf extraData) {
        super(type, id, inv, extraData);
    }

    public LinkedTypewriterMenuImpl(final MenuType<?> type, final int id, final Inventory inv, final LinkedTypewriterBlockEntity be) {
        super(type, id, inv, be);
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code ItemStackHandler} is replaced by {@code ItemStacksResourceHandler}, and a slot over
     * one is a {@code ResourceHandlerSlot} rather than a {@code SlotItemHandler}. A ghost slot is
     * written directly rather than through a transfer, so the handler's own {@code set} is what the
     * slot is given to write with -- the same pairing Create's blueprint menu uses.
     */
    @Override
    protected ItemStacksResourceHandler createGhostInventory() {
        return new ItemStacksResourceHandler(2);
    }

    @Override
    protected void addSlots() {
        this.addPlayerSlots(6 + (16 * 2), 11 + (16 * 3));

        for (int i = 0; i < 2; i++) {
            this.addSlot(new GhostSlotHandler(this.ghostInventory, i, 105 + (i * 18), 1));
        }
    }

    private class GhostSlotHandler extends ResourceHandlerSlot {

        public GhostSlotHandler(final ItemStacksResourceHandler itemHandler, final int index, final int xPosition, final int yPosition) {
            super(itemHandler, itemHandler::set, index, xPosition, yPosition);
        }

        @Override
        public boolean isFake() {
            return true;
        }

        @Override
        public boolean isActive() {
            return LinkedTypewriterMenuImpl.this.slotsActive;
        }
    }
}