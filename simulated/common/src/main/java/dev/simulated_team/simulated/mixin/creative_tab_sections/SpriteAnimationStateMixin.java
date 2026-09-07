package dev.simulated_team.simulated.mixin.creative_tab_sections;

import dev.simulated_team.simulated.mixin_interface.AnimationStateExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <h2>26.2 note</h2>
 * <p>Was a {@code @WrapOperation} on the {@code frame} field inside
 * {@code SpriteContents$Ticker#tickAndUpload}, which let the sub-frame counter keep running while
 * holding the frame still. There is no such field write to wrap any more -- animation advances in
 * {@code AnimationState#tick} and the atlas draws from the counters afterwards -- so a paused
 * animation simply does not tick.
 *
 * <p>That also stops the sub-frame, which the old version did not. It only matters for sprites that
 * interpolate between frames: those used to keep sweeping toward the next frame while "paused",
 * which was a wobble rather than a hold. Holding is what the caller asks for.
 */
@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimationState")
public class SpriteAnimationStateMixin implements AnimationStateExtension {

    @Unique
    private boolean simulated$playing = true;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void simulated$holdWhilePaused(final CallbackInfo ci) {
        if (!this.simulated$playing) {
            ci.cancel();
        }
    }

    @Override
    public void simulated$setPlaying(final boolean playing) {
        this.simulated$playing = playing;
    }

    @Override
    public boolean simulated$isPlaying() {
        return this.simulated$playing;
    }
}
