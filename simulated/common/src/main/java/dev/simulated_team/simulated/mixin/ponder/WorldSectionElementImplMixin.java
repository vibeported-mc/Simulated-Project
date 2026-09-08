package dev.simulated_team.simulated.mixin.ponder;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.math.Axis;
import net.createmod.ponder.impl.client.element.WorldSectionElementImpl;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies a ponder section's animated rotation in the order Simulated's scenes expect: X, then Z,
 * then Y, rather than X, Y, Z.
 *
 * <h2>26.2 note</h2>
 * <p>Ponder no longer rotates through Flywheel's {@code PoseTransformStack}: {@code transformMS}
 * calls {@code ms.mulPose(Axis.YP.rotationDegrees(...))} directly. The swap is the same one, moved
 * onto the quaternion that gets built rather than the transform call that consumed it.
 *
 * <p>The method builds six of them in one run -- X, Y, Z for the rotation and the same three negated
 * for the stabilisation anchor -- so the two swaps happen at ordinals 1 and 2, and again at 4 and 5.
 * The three locals are still declared rotX, rotZ, rotY in that order, so their ordinals are
 * unchanged.
 */
@Mixin(WorldSectionElementImpl.class)
public class WorldSectionElementImplMixin {

    @WrapOperation(method = "transformMS", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 1))
    public Quaternionf fixRotateY(final Axis instance, final float degrees, final Operation<Quaternionf> original,
                                  @Local(ordinal = 1) final double rotZ) {
        return Axis.ZP.rotationDegrees((float) rotZ);
    }

    @WrapOperation(method = "transformMS", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 2))
    public Quaternionf fixRotateZ(final Axis instance, final float degrees, final Operation<Quaternionf> original,
                                  @Local(ordinal = 2) final double rotY) {
        return Axis.YP.rotationDegrees((float) rotY);
    }

    @WrapOperation(method = "transformMS", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 4))
    public Quaternionf fixUnRotateY(final Axis instance, final float degrees, final Operation<Quaternionf> original,
                                    @Local(ordinal = 1) final double rotZ) {
        return Axis.ZP.rotationDegrees((float) -rotZ);
    }

    @WrapOperation(method = "transformMS", at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;", ordinal = 5))
    public Quaternionf fixUnRotateZ(final Axis instance, final float degrees, final Operation<Quaternionf> original,
                                    @Local(ordinal = 2) final double rotY) {
        return Axis.YP.rotationDegrees((float) -rotY);
    }
}
