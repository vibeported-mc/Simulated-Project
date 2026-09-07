package dev.simulated_team.simulated.content.blocks.steering_wheel;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelRenderer;
import com.simibubi.create.foundation.model.BakedModelHelper;
import com.simibubi.create.foundation.model.BakedQuadHelper;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.simulated_team.simulated.index.SimPartialModels;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.createmod.catnip.api.registry.RegisteredObjectsHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperBufferFactory;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferCache;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.createmod.catnip.api.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <h2>26.2 note</h2>
 * <p>{@code BakedModel} is gone. A block's model is a {@code BlockStateModel} now, and its quads are
 * not asked for directly -- the model collects {@link BlockStateModelPart}s and the quads come off
 * those. {@code BakedModelHelper.quadsOf} and {@code BakedQuadHelper.getSprite} are what Create's own
 * water wheel uses for the same texture-swapping trick, and this follows it exactly.
 *
 * <p>{@code BlockRenderDispatcher.getBlockModel} became
 * {@code ModelManager.getBlockStateModelSet().get(state)}, and a quad's facing is {@code direction()}
 * rather than {@code getDirection()}.
 */
public class SteeringWheelRenderer
        extends KineticBlockEntityRenderer<SteeringWheelBlockEntity, SteeringWheelRenderer.SteeringWheelRenderState> {

    public static final SuperByteBufferCache.Compartment<ModelKey> STEERING_WHEEL = new SuperByteBufferCache.Compartment<>();

    public static class SteeringWheelRenderState extends KineticRenderState {
        public @Nullable SuperByteBufferRenderState shaft;
        public @Nullable SuperByteBufferRenderState wheel;
    }

    public SteeringWheelRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SteeringWheelRenderState createRenderState() {
        return new SteeringWheelRenderState();
    }

    @Override
    protected void extractSafe(final SteeringWheelBlockEntity be, final SteeringWheelRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        // Reused between frames, so both pieces are cleared before anything decides not to draw.
        renderState.shaft = null;
        renderState.wheel = null;

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            renderState.skip = true;
            return;
        }

        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        final boolean floor = be.getBlockState().getValue(SteeringWheelBlock.ON_FLOOR);
        final Direction facing = be.getBlockState().getValue(SteeringWheelBlock.FACING);

        if (be.shouldRenderShaft()) {
            renderState.shaft = standardKineticRotationTransform(CachedBufferer.partialFacing(
                    AllPartialModels.SHAFT_HALF,
                    be.getBlockState(),
                    floor ? Direction.DOWN : Direction.UP
            ), be, renderState.lightCoords).extractRenderState();
        }

        final SuperByteBuffer model = this.getWheelModel(be);

        model.rotateCentered(facing.getRotation());
        if (floor) {
            model.translate(0, 6.5 / 16f, -5 / 16f);
        } else {
            model.translate(0, 6.5 / 16f, 5 / 16f);
        }
        model.rotateCentered(be.getRenderAngle(partialTicks), Direction.UP);

        model.light(renderState.lightCoords);
        model.color(Color.WHITE);
        renderState.wheel = model.extractRenderState();
    }

    @Override
    protected void submitSafe(final SteeringWheelRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);
        if (renderState.shaft != null)
            renderState.shaft.submit(ms, RenderTypes.solidMovingBlock(), queue);
        if (renderState.wheel != null)
            renderState.wheel.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    private SuperByteBuffer getWheelModel(final SteeringWheelBlockEntity be) {
        final ModelKey key = new ModelKey(be.material);
        return SuperByteBufferCache.getInstance().get(STEERING_WHEEL, key, () -> {
            final BlockStateModel model = generateModel(SimPartialModels.STEERING_WHEEL.get(), be.material);
            return SuperBufferFactory.getInstance().createForBlock(model, Blocks.AIR.defaultBlockState(), new PoseStack());
        });
    }

    public static BlockStateModel generateModel(final BlockStateModel template, final BlockState planksBlockState) {
        final Block planksBlock = planksBlockState.getBlock();
        final Identifier id = RegisteredObjectsHelper.getKeyOrThrow(planksBlock);
        final String wood = plankStateToWoodName(planksBlockState);

        if (wood == null)
            return BakedModelHelper.generateModel(template, sprite -> null);

        final Map<TextureAtlasSprite, TextureAtlasSprite> map = new Reference2ReferenceOpenHashMap<>();
        map.put(WaterWheelRenderer.OAK_PLANKS_TEMPLATE.get(), getSpriteOnSide(planksBlockState, Direction.UP));

        return BakedModelHelper.generateModel(template, map::get);
    }

    public record ModelKey(BlockState material) {
    }

    // todo tell create to make these public :p
    @Nullable
    private static String plankStateToWoodName(final BlockState planksBlockState) {
        final Block planksBlock = planksBlockState.getBlock();
        final Identifier id = RegisteredObjectsHelper.getKeyOrThrow(planksBlock);
        final String path = id.getPath();

        if (path.endsWith("_planks")) // Covers most wood types
            return (path.startsWith("archwood") ? "blue_" : "") + path.substring(0, path.length() - 7);

        if (path.contains("wood/planks/")) // TerraFirmaCraft
            return path.substring(12);

        return null;
    }

    private static @Nullable TextureAtlasSprite getSpriteOnSide(final BlockState state, final Direction side) {
        final BlockStateModel model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);
        if (model == null)
            return null;

        final RandomSource random = RandomSource.create();
        random.setSeed(42L);
        final List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(random, parts);

        final List<BakedQuad> quads = BakedModelHelper.quadsOf(parts, side);
        if (!quads.isEmpty())
            return BakedQuadHelper.getSprite(quads.get(0));

        for (final BakedQuad quad : BakedModelHelper.quadsOf(parts, null))
            if (quad.direction() == side)
                return BakedQuadHelper.getSprite(quad);

        return parts.isEmpty() ? null : parts.get(0)
                .particleMaterial()
                .sprite();
    }
}
