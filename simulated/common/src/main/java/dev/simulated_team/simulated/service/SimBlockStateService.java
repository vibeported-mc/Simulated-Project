package dev.simulated_team.simulated.service;

import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlock;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.data.SimBlockStateGen;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * <h2>26.2 note</h2>
 * <p>These methods took and returned {@code Object} because a model was a NeoForge
 * {@code ModelFile}, which {@code common} could not name. 26.2's replacement, {@link MultiVariant},
 * is vanilla -- so the types are honest now and the casts inside the implementation are gone.
 *
 * <p>The service itself is kept: the block model generator is still reached through it, and nothing
 * is served by collapsing an abstraction the mods' other loaders may want back.
 */
public interface SimBlockStateService {

	SimBlockStateService INSTANCE = ServiceUtil.load(SimBlockStateService.class);

	<T extends Block> void genericModelBuilder(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov, final Function<BlockState, SimBlockStateGen.XYHolder> xyGetter, final Function<BlockState, MultiVariant> modelGetter);

	<P extends AugerShaftBlock> NonNullBiConsumer<DataGenContext<Block, P>, RegistrateBlockModelGenerator> augerShaftGenerate(String name, boolean cog);

	<T extends AbstractDirectionalAxisBlock> void directionalAxisBlock(DataGenContext<Block, T> ctx, RegistrateBlockModelGenerator prov, BiFunction<BlockState, Boolean, MultiVariant> modelFunc);
}
