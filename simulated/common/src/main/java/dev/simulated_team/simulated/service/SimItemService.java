package dev.simulated_team.simulated.service;

import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;

public interface SimItemService {

    SimItemService INSTANCE = ServiceUtil.load(SimItemService.class);

    static DyeColor getDyeColor(final ItemStack itemStack) {
        return itemStack.getItem() instanceof DyeItem ? itemStack.get(DataComponents.DYE) : null;
    }

    /**
     * <h2>26.2 note</h2>
     * <p>How long an item burns for is a datapack value now, held by the level as its
     * {@code FuelValues}, so the level has to be named. Every caller had one to hand.
     */
    int getBurnTime(final Level level, final ItemStack stack);

    int getSuperheatedBurnTime(final ItemStack stack);
}
