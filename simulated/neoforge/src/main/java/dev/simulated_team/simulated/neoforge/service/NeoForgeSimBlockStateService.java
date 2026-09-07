package dev.simulated_team.simulated.neoforge.service;

import com.simibubi.create.foundation.data.BlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlock;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.data.SimBlockStateGen;
import dev.simulated_team.simulated.data.neoforge.AugerShaftGen;
import dev.simulated_team.simulated.service.SimBlockStateService;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * <h2>26.2 note</h2>
 * <p>{@code getVariantBuilder(...).forAllStates(...)} with NeoForge's {@code ConfiguredModel} and
 * {@code ModelFile} is gone -- 26.2 dispatches a blockstate over the properties it varies with rather
 * than walking every state, and NeoForge no longer ships its own model builders. Create's
 * {@link BlockStateGen#forAllStates} covers the same ground, and rotations are quadrant mutators on a
 * {@link MultiVariant} rather than angles.
 *
 * <p>That last part is a real narrowing: only multiples of 90 survive. Both generators here already
 * produced right angles, so nothing is lost, but a caller that computed an arbitrary angle would be
 * silently rounded down to the quadrant.
 */
public class NeoForgeSimBlockStateService implements SimBlockStateService {

    @Override
    public <T extends Block> void genericModelBuilder(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov, final Function<BlockState, SimBlockStateGen.XYHolder> xyGetter, final Function<BlockState, MultiVariant> modelGetter) {
        BlockStateGen.forAllStates(ctx, prov, state -> {
            final SimBlockStateGen.XYHolder rotations = xyGetter.apply(state);
            return BlockStateGen.rotateY(
                    BlockStateGen.rotateX(modelGetter.apply(state), rotations.xRot()),
                    rotations.yRot());
        });
    }

    @Override
    public <P extends AugerShaftBlock> NonNullBiConsumer<DataGenContext<Block, P>, RegistrateBlockModelGenerator> augerShaftGenerate(final String name, final boolean cog) {
        return AugerShaftGen.generate(name, cog);
    }

    @Override
    public <T extends AbstractDirectionalAxisBlock> void directionalAxisBlock(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov,
                                                                              final BiFunction<BlockState, Boolean, MultiVariant> modelFunc) {
        BlockStateGen.forAllStates(ctx, prov, state -> {

            final boolean alongFirst = state.getValue(AbstractDirectionalAxisBlock.AXIS_ALONG_FIRST_COORDINATE);
            final Direction direction = state.getValue(AbstractDirectionalAxisBlock.FACING);
            final boolean vertical = direction.getAxis()
                    .isHorizontal() && (direction.getAxis() == Direction.Axis.X) == alongFirst;
            final int xRot = direction == Direction.DOWN ? 270 : direction == Direction.UP ? 90 : 0;
            final int yRot = direction.getAxis()
                    .isVertical() ? alongFirst ? 0 : 90 : (int) direction.toYRot();

            return BlockStateGen.rotateY(
                    BlockStateGen.rotateX(modelFunc.apply(state, vertical), xRot),
                    yRot);
        });
    }

}
