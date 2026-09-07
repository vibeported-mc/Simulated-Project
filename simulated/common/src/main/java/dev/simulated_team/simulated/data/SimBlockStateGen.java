package dev.simulated_team.simulated.data;

import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.generators.RegistrateBlockModelGenerator;
import com.tterrag.registrate.providers.generators.RegistrateItemModelGenerator;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import dev.simulated_team.simulated.content.blocks.redstone.redstone_inductor.RedstoneInductorBlock;
import dev.simulated_team.simulated.content.blocks.symmetric_sail.SymmetricSailBlock;
import dev.simulated_team.simulated.content.blocks.util.AbstractDirectionalAxisBlock;
import dev.simulated_team.simulated.service.SimBlockStateService;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * <h2>26.2 note</h2>
 * <p>Model datagen was rewritten. {@code RegistrateBlockstateProvider} became
 * {@code RegistrateBlockModelGenerator}, and a model is named by an {@code Identifier} wrapped in a
 * {@code MultiVariant} rather than fetched as a {@code ModelFile} through {@code models()} -- which
 * no longer exists.
 *
 * <p>The generator's own {@code generateDirectionalBlock} and {@code generateHorizontalBlock} take a
 * single variant, so they cannot express a model that varies with the block's state. Create keeps
 * state-dependent forms in {@link BlockStateGen}, taking a
 * {@code Function<BlockState, MultiVariant>}, and those are what the helpers below use.
 */
public class SimBlockStateGen {

    public static <T extends DirectionalAxisKineticBlock> void directionalKineticAxisBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        BlockStateGen.directionalAxisBlock(ctx, prov, (blockState, vertical) -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/" + ctx.getName() + "/block_" + (vertical ? "vertical" : "horizontal"))));
    }

    public static <T extends Block> void facingPoweredAxisBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        BlockStateGen.directionalBlock(ctx, prov,
                blockState -> BlockModelGenerators.plainVariant(
                        prov.modLoc("block/" + ctx.getName() + "/block" + (blockState.getValue(BlockStateProperties.POWERED) ? "_powered" : ""))
                )
        );
    }

    public static <T extends Block> void facingBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov, final String modelPath) {
        BlockStateGen.directionalBlock(ctx, prov,
                blockState -> BlockModelGenerators.plainVariant(
                        prov.modLoc(modelPath)
                )
        );
    }

    public static <T extends Block> void horizontalFacingLitBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        BlockStateGen.horizontalBlock(ctx, prov, blockState -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/" + ctx.getName() + "/block" + (blockState.getValue(AbstractFurnaceBlock.LIT) ? "_lit" : ""))));
    }

    public static <T extends Block> void redstoneInductorBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        BlockStateGen.horizontalBlock(ctx, prov,
                blockState -> {
                    final boolean inverted = blockState.getValue(RedstoneInductorBlock.INVERTED);

                    return BlockModelGenerators.plainVariant(
                            prov.modLoc("block/" + ctx.getName() + "/block" + ((inverted ? "_inverted" : "")) + (blockState.getValue(BlockStateProperties.POWERED) ? "_powered" : "")));
                });
    }

    public static <T extends DirectionalAxisKineticBlock> void directionalPoweredAxisBlockstate(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        BlockStateGen.directionalAxisBlock(ctx, prov, (blockState, vertical) -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/" + ctx.getName() + "/block_" + (vertical ? "vertical" : "horizontal") + (blockState.getValue(BlockStateProperties.POWERED) ? "_powered" : ""))));
    }

    public static <I extends BlockItem> NonNullBiConsumer<DataGenContext<Item, I>, RegistrateItemModelGenerator> coloredBlockItemModel(final String texture, final String... folders) {
        return (c, p) -> {
            String path = "block";
            for (final String folder : folders)
                path += "/" + ("_".equals(folder) ? c.getName() : folder);

            // 26.2 port: withExistingParent(...).texture(...) is gone -- an item model is generated
            // from a template and a texture mapping rather than built up by hand. The parent model
            // already carries the texture slot, so pointing at it is what the old call amounted to.
            p.createWithExistingModel(c.getEntry(), p.modLoc(path));
        };
    }

    public static <T extends AbstractDirectionalAxisBlock> void directionalAxisBlock(final DataGenContext<Block, T> ctx, final RegistrateBlockModelGenerator prov) {
        SimBlockStateService.INSTANCE.directionalAxisBlock(ctx, prov, (blockState, vertical) -> BlockModelGenerators.plainVariant(
                prov.modLoc("block/" + ctx.getName() + "/block_" + (vertical ? "vertical" : "horizontal"))));
    }

    public static XYHolder xySymmetricSail(final BlockState state) {
        final Direction.Axis axis = state.getValue(SymmetricSailBlock.AXIS);
        return new XYHolder(axis == Direction.Axis.Y ? 0 : 90, axis == Direction.Axis.X ? 90 : axis == Direction.Axis.Z ? 180 : 0);
    }

    public static XYHolder xyAltitudeSensor(final BlockState state) {
        final int yRot = ((int) state.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot() + 180) % 360;
        final int xRot = state.getValue(BlockStateProperties.ATTACH_FACE).ordinal() * 90;

        return new XYHolder(xRot, yRot);
    }

    public static <I extends BlockItem, P> NonNullFunction<ItemBuilder<I, P>, P> customItemModel(final Identifier path) {
        return b -> b.model(() -> SimBlockStateGen.customBlockItemModel(path))
                .build();
    }

    /**
     * Generate item model inheriting from a seperate model in
     * models/block/folders[0]/folders[1]/.../item.json "_" will be replaced by the
     * item name
     */
    public static <I extends BlockItem> NonNullBiConsumer<DataGenContext<Item, I>, RegistrateItemModelGenerator> customBlockItemModel(
            final Identifier path) {
        return (c, p) -> {
            p.createWithExistingModel(c.getEntry(), path);
        };
    }

    public static XYHolder xyLaser(final BlockState state) {
        final Direction dir = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        final int yRot = (int) ((dir.getAxis().isVertical() ? 0 : dir.toYRot()) + 180);
        final int xRot = switch (state.getValue(DirectedDirectionalBlock.TARGET)) {
            case CEILING -> -90;
            case WALL -> 0;
            case FLOOR -> 90;
        };

        return new SimBlockStateGen.XYHolder((xRot + 360) % 360, (yRot + 360) % 360);
    }

    public record XYHolder(int xRot, int yRot) {
    }

}
