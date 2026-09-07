package dev.simulated_team.simulated.mixin.hold_interaction;

import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    /**
     * <h2>26.2 note</h2>
     * <p>Two changes met here. {@code keyPress} takes a {@code KeyEvent} rather than the four raw
     * ints, so the key, scan code and modifiers are read off it. And the screen is no longer a field
     * on {@code Minecraft} -- it is {@code Gui.screen()} -- so the injection point moved from that
     * field read to the call that replaced it, which is the same place in the method: after the
     * debug-key handling and before anything dispatches the press.
     */
    @Inject(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;", ordinal = 0), cancellable = true)
    private void simulated$preOnPress(final long windowPointer, final int action, final KeyEvent event, final CallbackInfo ci) {
        if (this.minecraft.gui.screen() == null) {
            if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
                final InteractCallback.Result status = SimulatedCommonClientEvents.onBeforeMouseInput(
                        InteractCallback.Input.key(event.key(), event.scancode()), event.modifiers(), action);
                if (status.cancelled()) {
                    ci.cancel();
                }
            }
        }
    }
}
