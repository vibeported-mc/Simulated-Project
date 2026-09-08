package dev.eriksonn.aeronautics.mixin.levitite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
public class BlockMixin {

    /**
     * <h2>26.2 note</h2>
     * <p>{@code shouldRenderFace} is two overloads now -- a three-argument one that delegates, and
     * the five-argument one that does the work -- so the selector names the descriptor rather than
     * just the name. An unqualified name matched neither, which Mixin reports as "Scanned 0
     * target(s)": a message that reads like a bad injection point when the injection point was fine.
     *
     * <p>Both block states are parameters of that overload, so both are read as arguments; the
     * neighbour used to be a local further down.
     */
    @WrapOperation(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"))
    private static boolean shouldRenderFace(final VoxelShape shape1, final VoxelShape shape2, final BooleanOp ops, final Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) final BlockState blockstate1, @Local(argsOnly = true, ordinal = 1) final BlockState blockstate2) {
        if (original.call(shape1, shape2, ops))
            return true;

        final boolean l1 = blockstate1.is(AeroTags.BlockTags.LEVITITE);
        final boolean l2 = blockstate2.is(AeroTags.BlockTags.LEVITITE);

        return l1 ^ l2;
    }
}
