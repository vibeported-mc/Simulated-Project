package dev.simulated_team.simulated.mixin.creative_tab_sections;

import dev.simulated_team.simulated.registrate.simulated_tab.SimulatedCreativeTab;
import dev.simulated_team.simulated.service.SimTabService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the creative tab's section banners are submitted.
 *
 * <h2>Why here and not on the creative screen</h2>
 *
 * <p>26.2 does not layer a GUI by submission order or by z. {@code GuiRenderState} puts an element
 * one node above the highest earlier element whose {@code bounds()} intersect its own. A banner is as
 * wide as the item grid, so a banner submitted after everything else intersects the stack held on the
 * cursor and is drawn over it -- the item vanishes behind the banner while being dragged across it.
 *
 * <p>{@code AbstractContainerScreen.extractRenderState} runs the contents, then the carried item,
 * then tooltips, and {@code CreativeModeInventoryScreen} reaches all three through {@code super}. So
 * the moment between the contents and the carried item exists only here, and that is the moment the
 * banners want: above the slots, below the stack on the cursor and below any tooltip.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractCarriedItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void simulated$renderSectionBanners(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen screen
                && CreativeModeInventoryScreenAccessor.simulated$selectedTab() == SimTabService.INSTANCE.getCreativeTab()) {
            SimulatedCreativeTab.renderBanners(screen, graphics, mouseX, mouseY);
        }
    }
}
