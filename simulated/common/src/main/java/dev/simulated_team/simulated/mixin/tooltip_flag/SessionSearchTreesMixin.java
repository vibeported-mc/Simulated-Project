package dev.simulated_team.simulated.mixin.tooltip_flag;

import dev.simulated_team.simulated.mixin_interface.tooltip_flag.TooltipFlagExtension;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SessionSearchTrees.class)
public class SessionSearchTreesMixin {
    /**
     * <h2>26.2 note</h2>
     * <p>The lambda this marks is still the one that builds the creative search tree's tooltip flag,
     * but its synthetic index moved from 15 to 0 -- {@code updateCreativeTooltips} was split into a
     * two-argument form and a three-argument one, and the lambdas renumbered within the method that
     * actually holds them.
     *
     * <p>The descriptor is spelled out so the selector names one method rather than trusting an
     * index on its own; an index that silently points at a different lambda is a mixin that compiles,
     * loads, and marks the wrong flag.
     */
    @ModifyVariable(method = "lambda$updateCreativeTooltips$0(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/client/multiplayer/SessionSearchTrees$Key;Ljava/util/List;)V", at = @At(value = "STORE"))
    private static TooltipFlag markAsCreativeSearch(final TooltipFlag value) {
        ((TooltipFlagExtension)value).simulated$setCreativeSearch(true);
        return value;
    }
}
