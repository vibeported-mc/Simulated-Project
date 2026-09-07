package dev.simulated_team.simulated.mixin_interface;

import net.minecraft.client.renderer.texture.SpriteContents;

public interface SpriteContentsExtension {
    SpriteContents.AnimationState simulated$getAnimationState();

    void simulated$setAnimationState(SpriteContents.AnimationState animationState);
}
