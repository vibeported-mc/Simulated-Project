package dev.simulated_team.simulated.mixin.ponder;

import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.mixin_interface.ponder.PonderSceneExtension;
import net.createmod.ponder.api.client.scene.PonderScene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PonderScene.SceneTransform.class)
/**
 * <h2>26.2 note</h2>
 * <p>Ponder's port moved {@code PonderScene} from {@code net.createmod.ponder.foundation} to
 * {@code net.createmod.ponder.api.client.scene}, and these three selectors named the old package
 * inside a descriptor string -- which javac never reads, so they compiled clean and would have
 * matched nothing at runtime. The two fields and the method are otherwise unchanged.
 */
public abstract class PonderSceneTransformMixin {
    // todo pr create to interpolate these variables
    @Redirect(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At(value = "FIELD", target = "Lnet/createmod/ponder/api/client/scene/PonderScene;scaleFactor:F"))
    private float interpolateScaleFactor(final PonderScene instance, @Local(argsOnly = true) final float pt) {
        return ((PonderSceneExtension)instance).simulated$getScale(pt);
    }

    @Redirect(method = "apply(Lcom/mojang/blaze3d/vertex/PoseStack;F)Lcom/mojang/blaze3d/vertex/PoseStack;", at = @At(value = "FIELD", target = "Lnet/createmod/ponder/api/client/scene/PonderScene;yOffset:F"))
    private float interpolateYOffset(final PonderScene instance, @Local(argsOnly = true) final float pt) {
        return ((PonderSceneExtension)instance).simulated$getYOffset(pt);
    }
}
