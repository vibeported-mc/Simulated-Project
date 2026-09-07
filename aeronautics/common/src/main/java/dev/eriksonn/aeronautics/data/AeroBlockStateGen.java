package dev.eriksonn.aeronautics.data;

import net.minecraft.client.data.models.BlockModelGenerators;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * <h2>26.2 note</h2>
 * <p>Model datagen was rewritten: {@code RegistrateBlockstateProvider} became
 * {@code RegistrateBlockModelGenerator}, and a model is named by an {@code Identifier} wrapped in
 * a {@code MultiVariant} rather than fetched as a {@code ModelFile}.
 */
public class AeroBlockStateGen {
	public static <T extends DirectionalAxisKineticBlock> void directionalPoweredAxisBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
		BlockStateGen.directionalAxisBlock(ctx, prov, (blockState, vertical) -> BlockModelGenerators.plainVariant(prov.modLoc("block/" + ctx.getName() + "/block_" + (vertical ? "vertical" : "horizontal") + (blockState.getValue(BlockStateProperties.POWERED) ? "_powered" : ""))));
	}
}
