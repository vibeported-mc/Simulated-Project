package dev.simulated_team.simulated.mixin.creative_tab_sections;

import dev.simulated_team.simulated.mixin_interface.SpriteContentsExtension;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <h2>26.2 note</h2>
 * <p>Was hung on {@code createTicker}. Sprite animation is a {@code AnimationState} built by
 * {@code createAnimationState} now, and the atlas keeps those in a list of its own with no way back
 * to the sprite that owns one -- so, as before, the sprite keeps a reference as it hands one out.
 */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin implements SpriteContentsExtension {

    @Unique
    private SpriteContents.AnimationState simulated$animationState = null;

    @Override
    public SpriteContents.AnimationState simulated$getAnimationState() {
        return this.simulated$animationState;
    }

    @Override
    public void simulated$setAnimationState(final SpriteContents.AnimationState animationState) {
        this.simulated$animationState = animationState;
    }

    @Inject(method = "createAnimationState", at = @At("RETURN"))
    private void simulated$createAnimationState(final CallbackInfoReturnable<SpriteContents.AnimationState> cir) {
        this.simulated$setAnimationState(cir.getReturnValue());
    }
}
