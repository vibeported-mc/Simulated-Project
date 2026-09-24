package dev.simulated_team.simulated.mixin.creative_tab_sections;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Which creative tab is showing.
 *
 * <p>Private and static on the screen, and needed from a mixin on {@code AbstractContainerScreen} --
 * which is where the banners have to be submitted, because that is the class that decides when the
 * carried item goes in.
 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {

    @Accessor("selectedTab")
    static CreativeModeTab simulated$selectedTab() {
        throw new AssertionError("mixin accessor");
    }
}
