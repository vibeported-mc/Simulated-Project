package dev.simulated_team.simulated.mixin.rope;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.content.blocks.rope.strand.client.ZiplineClientManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the block hit outline while a rope is what the cursor is really on.
 *
 * <h2>26.2 note</h2>
 * <p>{@code renderHitOutline} is gone. The outline is collected like everything else now: the shape
 * to draw is extracted into a {@code BlockOutlineRenderState} on the level render state, and
 * {@code submitBlockOutline} queues it. Cancelling that is the same suppression, one level up -- it
 * drops the whole outline rather than the one shape, which is what this always wanted, the old hook
 * having been the only call that drew it.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void sable$cancelBlockHitOutline(final PoseStack poseStack, final SubmitNodeCollector submitNodeCollector,
                                             final LevelRenderState levelRenderState, final CallbackInfo ci) {
        if (ZiplineClientManager.hoveringRope != null) {
            ci.cancel();
        }
    }
}
