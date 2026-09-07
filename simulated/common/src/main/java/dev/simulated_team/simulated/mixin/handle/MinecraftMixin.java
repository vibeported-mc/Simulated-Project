package dev.simulated_team.simulated.mixin.handle;

import dev.simulated_team.simulated.index.SimClickInteractions;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    /**
     * <h2>26.2 note</h2>
     * <p>{@code InteractionResult.shouldSwing} is gone: a success carries a {@code SwingSource}, and
     * the client swings when that is {@code CLIENT}. The old selector named no ordinal, so it hooked
     * every {@code shouldSwing} call in the method -- the entity interaction and both block uses --
     * and naming no ordinal here keeps that, because {@code swingSource} is called at exactly those
     * same points.
     */
    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;swingSource()Lnet/minecraft/world/InteractionResult$SwingSource;"))
    private void makeHandleHandlerCountUsing(final CallbackInfo ci) {
        SimClickInteractions.HANDLE_HANDLER.actuallyUsedBlockCountdown = 4;
    }
}
