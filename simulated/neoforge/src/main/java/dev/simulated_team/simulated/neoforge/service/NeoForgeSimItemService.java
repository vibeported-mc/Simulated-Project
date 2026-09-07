package dev.simulated_team.simulated.neoforge.service;

import com.simibubi.create.api.data.datamaps.BlazeBurnerFuel;
import com.simibubi.create.api.registry.CreateDataMaps;
import dev.simulated_team.simulated.service.SimItemService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class NeoForgeSimItemService implements SimItemService {

    /**
     * <h2>26.2 note</h2>
     * <p>{@code getBurnTime} takes the level's {@code FuelValues} alongside the recipe type, because
     * burn times are datapack-driven. Create passes a null recipe type at its own call sites, which
     * is what asks for the plain fuel value rather than one a specific recipe type overrides -- the
     * smelting type was the general case here too.
     */
    @Override
    public int getBurnTime(final Level level, final ItemStack stack) {
        return stack.getBurnTime(null, level.fuelValues());
    }

    @Override
    public int getSuperheatedBurnTime(final ItemStack stack) {
        final BlazeBurnerFuel fuel = stack.getItem().builtInRegistryHolder().getData(CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS);
        if(fuel != null) {
            return fuel.burnTime();
        }
        return 0;
    }
}