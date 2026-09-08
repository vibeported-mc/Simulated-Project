package dev.simulated_team.simulated.mixin.hold_interaction;

import com.mojang.authlib.GameProfile;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    public LocalPlayerMixin(final ClientLevel clientLevel, final GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Inject(method = "isShiftKeyDown", at = @At("RETURN"), cancellable = true)
    private void simulated$handlerShiftBlock(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !HoldInteractionManager.canCrouch()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code Input} is an immutable record of seven booleans rather than a mutable object with a
     * {@code shiftKeyDown} field, so there is no field access to wrap. The input is sent to the
     * server as a whole, in one {@code ServerboundPlayerInputPacket}, which is the same moment this
     * was reaching for -- so the packet's argument is what gets the crouch cleared out of it.
     *
     * <p>{@code lastSentInput} still records the unmodified input, so the "has it changed" test on
     * the next tick stays consistent with what the player is actually pressing.
     *
     * <p>The constructor is named as an {@code INVOKE} rather than through {@code @At("NEW")}:
     * {@code NEW} matches the allocation instruction, and {@code @ModifyArg} needs the call that
     * takes the argument. A selector can resolve and still be the wrong kind of injection point.
     */
    @ModifyArg(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerInputPacket;<init>(Lnet/minecraft/world/entity/player/Input;)V"))
    private Input simulated$shhhhDontTellTheServer(final Input input) {
        if (input.shift() && !HoldInteractionManager.canCrouch()) {
            return new Input(input.forward(), input.backward(), input.left(), input.right(), input.jump(), false,
                    input.sprint());
        }

        return input;
    }
}
