package dev.simulated_team.simulated.mixin.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.mixin_interface.ponder.PonderSceneExtension;
import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderer;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades and offsets a ponder scene's base-plate shadow.
 *
 * <h2>26.2 note</h2>
 * <p>This used to mix into {@code PonderUI.renderScene}, which drew the shadow itself. In 26.2 that
 * method draws nothing: it queues a {@code PonderSceneRenderState} as a picture-in-picture, and the
 * shadow is drawn later by {@link PonderSceneRenderer}. Both hooks move there.
 *
 * <p>The move makes the scene easier to reach, not harder -- the renderer is handed the state, which
 * carries the scene, so the {@code scenes} list and the index that used to be shadowed and looked up
 * are both gone.
 *
 * <p>The offset is applied before {@code flipForGuiRender} rather than after the second
 * {@code translate}, which is the same point: the base plate has been placed and nothing has flipped
 * into GUI space yet. Naming the call rather than an ordinal also survives a translate being added.
 */
@Mixin(PonderSceneRenderer.class)
public class PonderUIMixin {

    @ModifyConstant(method = "renderScene", constant = @Constant(intValue = 0x66_000000, ordinal = 0))
    private int customShadowFade(final int constant, final PonderSceneRenderState state) {
        final PonderScene scene = state.scene();
        final int alpha = (int) ((constant >> 24) * ((PonderSceneExtension) scene).simulated$getBasePlateAnimationTimer(state.partialTicks()));
        return (alpha << 24) | (constant & 0x00_FFFFFF);
    }

    @Inject(method = "renderScene",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/createmod/catnip/api/client/gui/UIRenderHelper;flipForGuiRender(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void shadowTranslate(final PonderSceneRenderState state, final PoseStack poseStack, final SubmitNodeCollector queue,
                                 final CallbackInfo ci) {
        final Vec3 offset = ((PonderSceneExtension) state.scene()).simulated$getShadowOffset(state.partialTicks());
        poseStack.translate(offset.x, offset.y, offset.z);
    }
}
