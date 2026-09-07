package dev.eriksonn.aeronautics.mixin.levitite;

import dev.eriksonn.aeronautics.content.blocks.levitite.LevititeShaderManager;
import net.createmod.ponder.impl.client.gui.PonderUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PonderUI.class)
public class PonderUIMixin {
    /**
     * <h2>26.2 note</h2>
     * <p>{@code renderScene} lost the two mouse coordinates: it takes the graphics, the scene index
     * and the partial ticks. What it does is unchanged for this hook's purposes -- it is still the
     * first thing to run when a ponder scene is put on screen, which is when the levitite shader has
     * to be told the world it was set up for is not the one being drawn.
     */
    @Inject(method = "renderScene", at = @At("HEAD"))
    protected void renderScene(GuiGraphicsExtractor graphics, int i, float partialTicks, CallbackInfo ci) {
        LevititeShaderManager.disableShader();
    }
}
