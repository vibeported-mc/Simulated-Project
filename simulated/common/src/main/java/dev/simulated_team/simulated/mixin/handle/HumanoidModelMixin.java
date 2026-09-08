package dev.simulated_team.simulated.mixin.handle;

import dev.simulated_team.simulated.content.blocks.handle.PlayerHoldingHandleRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>26.2 note</h2>
 * <p>{@code setupAnim} lost its five animation floats and its entity; it takes the render state and
 * nothing else. There is no player to test any more, so the test happens during extraction and
 * arrives here as render data -- see {@link PlayerHoldingHandleRenderer}.
 *
 * <p>The descriptor is spelled out because {@code Model} declares {@code setupAnim(S)} over a
 * looser bound, so the class also carries a synthetic bridge taking {@code Object}.
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin<T extends HumanoidRenderState> {
    @Shadow
    @Final
    public ModelPart body;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void simulated$afterSetupAnim(final T state, final CallbackInfo callbackInfo) {
        PlayerHoldingHandleRenderer.afterSetupAnim(state, (HumanoidModel<?>) (Object) this);
    }
}
