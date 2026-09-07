package dev.simulated_team.simulated.content.blocks.directional_gearshift;

import com.simibubi.create.foundation.data.BlockStateGen;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * <h2>26.2 note</h2>
 * <p>NeoForge's multipart builder is gone; this is vanilla's {@link MultiPartGenerator}, whose parts
 * are (condition, variant) pairs rather than a chain of {@code part().modelFile(...).condition(...)},
 * and rotations are quadrant mutators on the variant rather than angles. Every angle here was
 * already a right angle.
 *
 * <p>Walking the possible blockstates emitted the same part once per state sharing its condition.
 * A multipart part is chosen by its condition, so the duplicates changed nothing -- iterating the
 * properties directly says it once.
 */
public class DirectionalGearshiftGenerator {
    public static <P extends DirectionalGearshiftBlock> void generate(final DataGenContext<Block, P> context, final RegistrateBlockModelGenerator provider) {
        final MultiPartGenerator builder = MultiPartGenerator.multiPart(context.get());

        for (final Direction direction : DirectionalGearshiftBlock.FACING.getPossibleValues()) {
            for (final boolean alongFirst : new boolean[] { false, true }) {
                final boolean vertical = direction.getAxis()
                        .isHorizontal() && (direction.getAxis() == Direction.Axis.X) == alongFirst;
                final int xRot = direction == Direction.DOWN ? 270 : direction == Direction.UP ? 90 : 0;
                final int yRot = direction.getAxis()
                        .isVertical() ? alongFirst ? 0 : 90 : (int) direction.toYRot();

                builder.with(BlockModelGenerators.condition()
                                .term(DirectionalGearshiftBlock.FACING, direction)
                                .term(DirectionalGearshiftBlock.AXIS_ALONG_FIRST_COORDINATE, alongFirst),
                        rotate(model("middle", false, vertical), xRot, yRot));

                for (final boolean on : new boolean[] { false, true }) {
                    builder.with(BlockModelGenerators.condition()
                                    .term(DirectionalGearshiftBlock.LEFT_POWERED, on)
                                    .term(DirectionalGearshiftBlock.FACING, direction)
                                    .term(DirectionalGearshiftBlock.AXIS_ALONG_FIRST_COORDINATE, alongFirst),
                            rotate(model("left", on, vertical), xRot, yRot));

                    builder.with(BlockModelGenerators.condition()
                                    .term(DirectionalGearshiftBlock.RIGHT_POWERED, on)
                                    .term(DirectionalGearshiftBlock.FACING, direction)
                                    .term(DirectionalGearshiftBlock.AXIS_ALONG_FIRST_COORDINATE, alongFirst),
                            rotate(model("right", on, vertical), xRot, yRot));
                }
            }
        }

        provider.blockStateOutput.accept(builder);
    }

    private static MultiVariant rotate(final MultiVariant variant, final int xRot, final int yRot) {
        return BlockStateGen.rotateY(BlockStateGen.rotateX(variant, xRot), yRot);
    }

    private static MultiVariant model(final String part, final boolean powered, final boolean vertical) {
        return BlockModelGenerators.plainVariant(Simulated.path("block/directional_gearshift/" + (vertical ? "vertical/" : "horizontal/") + part + (powered ? "_powered" : "")));
    }
}
