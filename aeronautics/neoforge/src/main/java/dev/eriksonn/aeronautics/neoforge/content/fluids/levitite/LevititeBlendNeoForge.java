package dev.eriksonn.aeronautics.neoforge.content.fluids.levitite;

import dev.eriksonn.aeronautics.api.levitite_blend_crystallization.LevititeBlendDummyInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

public class LevititeBlendNeoForge extends BaseFlowingFluid implements LevititeBlendDummyInterface {
	public LevititeBlendNeoForge(Properties properties) {
		super(properties);
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>A fluid ticks on the server only, and is handed the block state alongside the fluid state --
	 * so it no longer has to look either up. The crystallization tick still wants a {@code Level},
	 * which a {@code ServerLevel} is.
	 */
	@Override
	public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState state) {
		super.tick(level, pos, blockState, state);
		LevititeBlendDummyInterface.super.levititeBlendTick(level, pos, state);
	}

	@Override
	public boolean isSource(FluidState fluidState) {
		return true;
	}

	@Override
	public int getAmount(FluidState fluidState) {
		return 8;
	}
}
