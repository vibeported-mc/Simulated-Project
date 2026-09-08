package dev.simulated_team.simulated.neoforge.service;

import dev.simulated_team.simulated.service.SimFluidService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.capabilities.Capabilities;

public class NeoForgeSimFluidService implements SimFluidService {
    public long mbToLoaderUnits(final long mb) {
        return mb;
    }

    /**
     * <h2>26.2 note</h2>
     * <p>The fluid capability on an item is a {@code ResourceHandler<FluidResource>} rather than an
     * {@code IFluidHandlerItem}, and it is looked up against an {@code ItemAccess} -- a view of the
     * stack in whatever holds it -- rather than against the stack itself. A tank holds a resource
     * and an amount instead of a {@code FluidStack}, so the fluid is read off the resource.
     *
     * <p>The empty stack is checked for rather than passed on. {@code ItemAccess.forStack} throws
     * {@code IllegalArgumentException("Expected stack to be non-empty")} on 26.2 where the old
     * {@code IFluidHandlerItem} lookup simply returned nothing, and the callers here ask about
     * whatever is in a filter slot -- which is empty most of the time. An optical sensor with an
     * empty filter asked once a tick, so this threw out of {@code tick} and took the whole dedicated
     * server down with it.
     */
    @Override
    public Fluid getFluidInItem(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        final ResourceHandler<FluidResource> handler =
                ItemAccess.forStack(stack).getCapability(Capabilities.Fluid.ITEM);
        if (handler != null && handler.size() > 0) {
            final FluidResource fluid = handler.getResource(0);
            if (!fluid.isEmpty()) {
                return fluid.getFluid();
            }
        }
        return null;
    }
}
