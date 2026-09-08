package dev.simulated_team.simulated.mixin.hold_interaction;

import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    /**
     * <h2>26.2 note</h2>
     * <p>The y-inversion used to be a local {@code int} of 1 or -1, multiplied into the delta just
     * before the turn. 26.2 applies it inline at the call -- {@code invertMouseY().get() ? -yo : yo}
     * -- so there is no {@code int} in the method at all, and the sign is read from the option here
     * instead.
     *
     * <p>This is what crashed the client. The selector was correct and the two doubles still sit at
     * ordinals 4 and 5, so the mixin validator passed it; MixinExtras matches a {@code @Local} on its
     * type as well as its ordinal, and reports the mismatch as "Scanned 0 target(s)" with the real
     * cause -- a SugarApplicationException -- suppressed underneath.
     */
    @Inject(method = "turnPlayer", cancellable = true,
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void simulated$turnPlayer(final double d, final CallbackInfo ci,
                                      @Local(ordinal = 4) final double xo, @Local(ordinal = 5) final double yo) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final int invertY = Minecraft.getInstance().options.invertMouseY().get() ? -1 : 1;
            final InteractCallback.Result status = SimulatedCommonClientEvents.onMouseMove(xo, yo * invertY);
            if (status.cancelled()) {
                ci.cancel();
            }
        }
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code onPress} became {@code onButton}, and the button and its modifiers arrive together in
     * a {@code MouseButtonInfo} rather than as two loose ints -- which also retires the two
     * {@code @Local}s that were only reaching back for the arguments.
     *
     * <p>The overlay moved off {@code Minecraft} onto the {@code Gui}, so the injection point is that
     * call instead: the same place in the method, before anything dispatches the press.
     */
    @Inject(method = "onButton",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0),
            cancellable = true)
    private void simulated$preOnPress(final long windowPointer, final MouseButtonInfo buttonInfo, final int action, final CallbackInfo ci) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onBeforeMouseInput(
                    InteractCallback.Input.mouse(buttonInfo.button()), buttonInfo.modifiers(), action);
            if (status.cancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "onScroll",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;", ordinal = 0),
            cancellable = true)
    private void simulated$preOnScroll(final long l, final double d, final double e, final CallbackInfo ci, @Local(ordinal = 3) final double deltaX, @Local(ordinal = 4) final double deltaY) {
        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onMouseScroll(deltaX, deltaY);
            if (status.cancelled()) {
                ci.cancel();
            }
        }
    }
}