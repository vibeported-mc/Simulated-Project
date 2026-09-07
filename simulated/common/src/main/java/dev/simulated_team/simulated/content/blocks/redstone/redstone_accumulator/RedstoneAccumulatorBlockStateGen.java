package dev.simulated_team.simulated.content.blocks.redstone.redstone_accumulator;

import com.simibubi.create.foundation.data.BlockStateGen;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import dev.simulated_team.simulated.Simulated;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * <h2>26.2 note</h2>
 * <p>NeoForge's multipart builder is gone; this is vanilla's {@link MultiPartGenerator}, whose parts
 * are (condition, variant) pairs rather than a chain of {@code part().modelFile(...).condition(...)}.
 *
 * <p>The old code walked every possible blockstate and emitted four parts for each, so the same part
 * was written once per state that shared its condition -- harmless, because a multipart part is
 * selected by its condition rather than by which state produced it, but it meant hundreds of
 * duplicates in the output. Iterating the properties directly says the same thing once each.
 */
public class RedstoneAccumulatorBlockStateGen {

    public static <P extends RedstoneAccumulatorBlock> NonNullBiConsumer<DataGenContext<Block, P>, RegistrateBlockModelGenerator> generate() {
        return (ctx, prov) -> {
            final MultiVariant backOff = sub("block_back_off");
            final MultiVariant backOn = sub("block_back_on");
            final MultiVariant front = sub("block_front");
            final MultiVariant middleOff = sub("block_middle_off");
            final MultiVariant middleOn = sub("block_middle_on");
            final MultiVariant torchOff = sub("torch_off");
            final MultiVariant torchOn = sub("torch_on");

            final MultiPartGenerator builder = MultiPartGenerator.multiPart(ctx.get());

            for (final Direction facing : RedstoneAccumulatorBlock.FACING.getPossibleValues()) {
                final int yRot = ((int) facing.getOpposite().toYRot());

                builder.with(BlockModelGenerators.condition()
                                .term(RedstoneAccumulatorBlock.FACING, facing),
                        BlockStateGen.rotateY(front, yRot));

                for (final boolean powered : new boolean[] { false, true }) {
                    builder.with(BlockModelGenerators.condition()
                                    .term(RedstoneAccumulatorBlock.POWERED, powered)
                                    .term(RedstoneAccumulatorBlock.FACING, facing),
                            BlockStateGen.rotateY(powered ? backOn : backOff, yRot));

                    builder.with(BlockModelGenerators.condition()
                                    .term(RedstoneAccumulatorBlock.SIDE_POWERED, powered)
                                    .term(RedstoneAccumulatorBlock.FACING, facing),
                            BlockStateGen.rotateY(powered ? middleOn : middleOff, yRot));

                    builder.with(BlockModelGenerators.condition()
                                    .term(RedstoneAccumulatorBlock.POWERING, powered)
                                    .term(RedstoneAccumulatorBlock.FACING, facing),
                            BlockStateGen.rotateY(powered ? torchOn : torchOff, yRot));
                }
            }

            prov.blockStateOutput.accept(builder);
        };
    }

    private static MultiVariant sub(final String suffix) {
        return BlockModelGenerators.plainVariant(Simulated.path("block/redstone_accumulator/" + suffix));
    }
}
