package dev.simulated_team.simulated.mixin.new_ponder;

import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.ponder.new_ponder_tooltip.NewPonderTooltipManager;
import dev.simulated_team.simulated.service.SimConfigService;
import net.createmod.ponder.impl.client.tooltip.PonderTooltipHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a ponder tooltip whose scenes the player has not all watched.
 *
 * <h2>26.2 note</h2>
 * <p>This wrapped {@code LangBuilder.component()} inside {@code makeProgressBar} and read the item
 * back out of a {@code trackingStack} field. Neither survives: the progress bar is built from
 * literals now and calls no {@code LangBuilder}, and the handler keeps no tracked stack.
 *
 * <p>{@code determineTooltip} is the better hook and was always the more honest one. It is where the
 * whole tooltip is decided rather than one branch of it, and it takes the stack as an argument -- so
 * the accessor that reached for the field is deleted rather than retargeted.
 *
 * <p>One behavioural difference falls out of that: the tag used to appear only while the progress bar
 * was showing, which is to say only while the player was holding the ponder key. It now appears on
 * the resting "hold to ponder" line too, which is what a "you have not seen this" marker is for.
 */
@Mixin(PonderTooltipHandler.class)
public class PonderTooltipHandlerMixin {

    @Inject(method = "determineTooltip", at = @At("RETURN"), cancellable = true)
    private static void simulated$addToTooltip(final ItemStack stack, final CallbackInfoReturnable<Component> cir) {
        if (!SimConfigService.INSTANCE.client().itemConfig.showNewPonderTag.get()) {
            return;
        }

        if (NewPonderTooltipManager.hasWatchedAllScenes(stack.getItem())) {
            return;
        }

        cir.setReturnValue(Component.empty()
                .append(cir.getReturnValue())
                .append(" ")
                .append(SimLang.translate("tooltip.new_ponder").style(ChatFormatting.GOLD).component()));
    }
}
