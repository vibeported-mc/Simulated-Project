package dev.simulated_team.simulated.data.neoforge;

import com.simibubi.create.foundation.data.BlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlock;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import static net.minecraft.core.Direction.UP;

/**
 * <h2>26.2 note</h2>
 * <p>NeoForge's {@code MultiPartBlockStateBuilder}, {@code ConfiguredModel} and {@code ModelFile} are
 * gone -- multipart blockstates are vanilla's {@link MultiPartGenerator} now, and a model is a
 * {@link MultiVariant} named by identifier rather than a file object fetched through
 * {@code models()}.
 *
 * <p>The rotations survive unchanged because every one of them was already a right angle: 26.2
 * expresses a rotation as a quadrant mutator, so only multiples of 90 can be written at all.
 */
public class AugerShaftGen {

    public static MultiVariant rotate(final Direction direction, final MultiVariant variant) {
        return BlockStateGen.rotateY(
                BlockStateGen.rotateX(variant, direction.getAxis().isHorizontal() ? -90 : (direction == UP ? 180 : 0)),
                direction.getAxis().isVertical() ? 0 : (((int) direction.toYRot()) + 180) % 360);
    }

    public static <P extends AugerShaftBlock> NonNullBiConsumer<DataGenContext<Block, P>, RegistrateBlockModelGenerator> generate(
            final String name, final boolean cog) {
        return (c, p) -> {

            final MultiVariant axis_y = cog ? sub(p, name, "cog_axis_y") : sub(p, name, "axis_y");
            final MultiVariant connection_top = sub(p, name, "connection_top");

            final MultiPartGenerator builder = MultiPartGenerator.multiPart(c.get());

            //rotate main body
            for (final Direction.Axis dir : Direction.Axis.values()) {
                builder.with(BlockModelGenerators.condition()
                                .term(AugerShaftBlock.AXIS, dir)
                                .term(AugerShaftBlock.ENCASED, false),
                        rotate(Direction.get(Direction.AxisDirection.POSITIVE, dir), axis_y));
            }

            //rotate encased body
            for (final Direction.Axis dir : Direction.Axis.values()) {
                builder.with(BlockModelGenerators.condition()
                                .term(AugerShaftBlock.AXIS, dir)
                                .term(AugerShaftBlock.ENCASED, true),
                        rotate(Direction.get(Direction.AxisDirection.POSITIVE, dir), sub(p, name, "axis_y_encased")));
            }

            //Generate start and end segments
            for (final Direction.Axis dir : Direction.Axis.values()) {
                builder.with(BlockModelGenerators.condition()
                                .term(AugerShaftBlock.AXIS, dir)
                                .term(AugerShaftBlock.SECTION, AugerShaftBlock.BarrelSection.END, AugerShaftBlock.BarrelSection.SINGLE)
                                .term(AugerShaftBlock.ENCASED, false),
                        rotate(Direction.get(Direction.AxisDirection.POSITIVE, dir), connection_top));

                builder.with(BlockModelGenerators.condition()
                                .term(AugerShaftBlock.AXIS, dir)
                                .term(AugerShaftBlock.SECTION, AugerShaftBlock.BarrelSection.FRONT, AugerShaftBlock.BarrelSection.SINGLE)
                                .term(AugerShaftBlock.ENCASED, false),
                        rotate(Direction.get(Direction.AxisDirection.NEGATIVE, dir), connection_top));
            }

            //generate connection points
            if (!cog) {
                for (final Direction dir : Direction.values()) {
                    builder.with(BlockModelGenerators.condition()
                                    .term(AugerShaftBlock.PROPERTY_BY_DIRECTION.get(dir), true)
                                    .term(AugerShaftBlock.ENCASED, false),
                            rotate(dir.getOpposite(), sub(p, name, "bracket_top_" + dir.getAxis().getName())));
                }
            }

            p.blockStateOutput.accept(builder);
        };
    }

    private static MultiVariant sub(final RegistrateBlockModelGenerator p, final String name, final String suffix) {
        return BlockModelGenerators.plainVariant(Simulated.path("block/auger_shaft/" + suffix));
    }
}
