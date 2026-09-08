package dev.simulated_team.simulated.mixin.torsion_spring;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.simulated_team.simulated.api.IDirectionalAnalogOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ComparatorBlock.class)
public class ComparatorBlockMixin {
    /**
     * <h2>26.2 note</h2>
     * <p>Vanilla's {@code getAnalogOutputSignal} takes the direction the comparator is reading from,
     * which is the whole point this mixin existed to add -- so a block can answer directionally
     * without any of this, by overriding the vanilla method.
     *
     * <p>It is kept rather than retired because the two conventions differ: vanilla passes the
     * comparator's facing <em>opposite</em>, and {@code IDirectionalAnalogOutput} was written against
     * the raw facing. Moving the two implementers onto the vanilla hook means reconciling that, and
     * getting it backwards silently inverts which side a torsion spring or steering wheel answers
     * on. The local is still the raw direction, so behaviour here is unchanged.
     */
    @WrapOperation(method = "getInputSignal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getAnalogOutputSignal(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"))
    private int simulated$potentiallyDirectionalAnalogueSignal(final BlockState instance, final Level level, final BlockPos pos, final Direction readFrom, final Operation<Integer> original, @Local(name = "direction") final Direction direction) {
        if (instance.getBlock() instanceof final IDirectionalAnalogOutput directionalAnalogOutput) {
            return directionalAnalogOutput.getAnalogOutputSignalFrom(instance, level, pos, direction);
        }
        return original.call(instance, level, pos, readFrom);
    }
}
