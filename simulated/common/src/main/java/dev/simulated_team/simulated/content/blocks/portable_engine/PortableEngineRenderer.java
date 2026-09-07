package dev.simulated_team.simulated.content.blocks.portable_engine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.FilterRenderState;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * <h2>26.2 note</h2>
 * <p>This renderer draws into two different render types -- the cold parts cutout, the heated parts
 * translucent -- through the same two helpers. A render state cannot carry that implicitly, so each
 * extracted piece is paired with the type it belongs to and the submit phase replays them in order.
 *
 * <p>The shaft now goes through {@code super.extractSafe} rather than a hand-rolled
 * {@code renderRotatingBuffer}, which also gains the visualization check the old call was missing:
 * the shaft used to be drawn a second time by this renderer even when Flywheel was already
 * instancing it.
 */
public class PortableEngineRenderer
        extends KineticBlockEntityRenderer<PortableEngineBlockEntity, PortableEngineRenderer.PortableEngineRenderState> {

    /** A piece of geometry and the render type it belongs to. */
    public record Part(SuperByteBufferRenderState geometry, RenderType type) { }

    public static class PortableEngineRenderState extends KineticRenderState {
        public final List<Part> parts = new ArrayList<>();
        public @Nullable FilterRenderState filter;
    }

    private final ItemModelResolver itemModelResolver;

    public PortableEngineRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PortableEngineRenderState createRenderState() {
        return new PortableEngineRenderState();
    }

    protected static float getHatchOpenProgress(final PortableEngineBlockEntity engine, final float partialTicks) {
        return Mth.sin(engine.getHatchOpenTime(partialTicks) / 10 * Mth.HALF_PI);
    }

    @Override
    protected void extractSafe(final PortableEngineBlockEntity be, final PortableEngineRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        renderState.parts.clear();
        renderState.filter = FilteringRenderer.getFilterRenderState(be, this.itemModelResolver, cameraPosition);

        final Direction direction = be.getBlockState()
                .getValue(BlockStateProperties.HORIZONTAL_FACING);
        final BlockState blockState = be.getBlockState();

        SimPartialModels.EngineParts engineParts = SimPartialModels.ENGINE_PARTS;

        final float visualStrength = be.visualStrength.getValue(partialTicks);

        final boolean lit = blockState.getValue(RedstoneTorchBlock.LIT);
        this.extractHatch(be, partialTicks, renderState, renderState.lightCoords, blockState, direction, RenderTypes.cutoutMovingBlock(), 255, engineParts, !lit, false);
        this.extractPipes(be, partialTicks, renderState, renderState.lightCoords, blockState, direction, RenderTypes.cutoutMovingBlock(), 255, engineParts, false);

        final float hatchOpenProgress = 1.0f - getHatchOpenProgress(be, partialTicks);
        if (visualStrength > 0) {
            engineParts = be.isSuperHeated() ? SimPartialModels.ENGINE_PARTS_SUPERHEATED : SimPartialModels.ENGINE_PARTS_HEATED;

            this.extractPipes(be, partialTicks, renderState, LightCoordsUtil.FULL_BRIGHT, blockState, direction, RenderTypes.translucentMovingBlock(), (int) (visualStrength * 255), engineParts, true);
        }

        if (lit) {
            this.extractHatch(be, partialTicks, renderState, LightCoordsUtil.FULL_BRIGHT, blockState, direction, RenderTypes.translucentMovingBlock(), (int) (hatchOpenProgress * 255), engineParts, lit, true);
        }
    }

    @Override
    protected void submitSafe(final PortableEngineRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);

        if (renderState.filter != null)
            renderState.filter.submit(renderState.blockState, queue, ms, renderState.lightCoords);

        for (final Part part : renderState.parts)
            part.geometry().submit(ms, part.type(), queue);
    }

    private void extractHatch(final PortableEngineBlockEntity be, final float partialTicks, final PortableEngineRenderState renderState, final int light, final BlockState blockState, final Direction direction, final RenderType type, final int alpha, final SimPartialModels.EngineParts parts, final boolean renderInner, boolean lit) {
        if (be.isVirtual()) lit = false;

        final double hatchPivotY = 4.9f / 16.0f;
        final double hatchPivotZ = 3.7f / 16.0f;

        final float hatchOpenAmount = getHatchOpenProgress(be, partialTicks) * 0.65f;

        final SuperByteBuffer hatchBottom = this.rotateToFacing(CachedBufferer.partial(parts.hatchBottom, blockState), direction);
        if (lit) hatchBottom.disableDiffuse();
        hatchBottom
                .translate(0.0f, hatchPivotY, hatchPivotZ)
                .rotate(-hatchOpenAmount, Direction.EAST)
                .translate(-0.0f, -hatchPivotY, -hatchPivotZ)
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(hatchBottom.extractRenderState(), type));

        final SuperByteBuffer hatchTop = this.rotateToFacing(CachedBufferer.partial(parts.hatchTop, blockState), direction);
        if (lit) hatchTop.disableDiffuse();
        hatchTop
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(hatchTop.extractRenderState(), type));

        if (renderInner) {
            final SuperByteBuffer mouth = this.rotateToFacing(CachedBufferer.partial(parts.mouth, blockState), direction.getOpposite());
            if (lit) mouth.disableDiffuse();
            mouth.light(light);
            renderState.parts.add(new Part(mouth.extractRenderState(), type));
        }
    }

    private void extractPipes(final PortableEngineBlockEntity be, final float partialTicks, final PortableEngineRenderState renderState, final int light, final BlockState blockState, final Direction direction, final RenderType type, final int alpha, final SimPartialModels.EngineParts parts, boolean lit) {
        final float renderTime = AnimationTickHolder.getRenderTime(be.getLevel()) / 20;

        final double pulseTime = renderTime * 7.0;
        final double clipHeight = 0.65;
        final float pulseStrength = 0.03f * be.visualStrength.getValue(partialTicks);
        final float pipePulseStrength = pulseStrength * 1.1f;

        final float pipeScale = (float) (Math.max(Math.sin(pulseTime) + clipHeight, 0.0) - clipHeight) * pipePulseStrength + 1.0f;
        final float outletScale = (float) (Math.max(Math.sin(pulseTime - 1.15) + clipHeight, 0.0) - clipHeight) * pulseStrength + 1.0f;

        final Vector3f outletRotationPointLeft = new Vector3f(2.2f, 10.2f, 11.0f).div(16.0f);
        final Vector3f outletRotationPointRight = new Vector3f(13.6f, 10.2f, 11.0f).div(16.0f);

        final float outletRotation = (float) Math.toRadians(7.5);

        final Vector3f pipeCenterRight = new Vector3f(14.0f, 10.0f, 8.0f).div(16.0f);
        final Vector3f pipeCenterLeft = new Vector3f(16.0f - 14.0f, 10.0f, 8.0f).div(16.0f);

        if (be.isVirtual()) lit = false;

        final SuperByteBuffer pipeRight = this.rotateToFacing(CachedBufferer.partial(parts.pipeRight, blockState), direction);
        if (lit) pipeRight.disableDiffuse();
        pipeRight
                .translate(pipeCenterRight)
                .scale(pipeScale)
                .translateBack(pipeCenterRight)
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(pipeRight.extractRenderState(), type));

        final SuperByteBuffer outletRight = this.rotateToFacing(CachedBufferer.partial(parts.outletRight, blockState), direction);
        if (lit) outletRight.disableDiffuse();
        outletRight
                .translate(pipeCenterRight)
                .scale(outletScale)
                .translateBack(pipeCenterRight)
                .translate(outletRotationPointRight)
                .rotateY(-outletRotation)
                .translateBack(outletRotationPointRight)
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(outletRight.extractRenderState(), type));

        final SuperByteBuffer pipeLeft = this.rotateToFacing(CachedBufferer.partial(parts.pipeLeft, blockState), direction);
        if (lit) pipeLeft.disableDiffuse();
        pipeLeft
                .translate(pipeCenterLeft)
                .scale(pipeScale)
                .translateBack(pipeCenterLeft)
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(pipeLeft.extractRenderState(), type));

        final SuperByteBuffer outletLeft = this.rotateToFacing(CachedBufferer.partial(parts.outletLeft, blockState), direction);
        if (lit) outletLeft.disableDiffuse();
        outletLeft
                .translate(pipeCenterLeft)
                .scale(outletScale)
                .translateBack(pipeCenterLeft)
                .translate(outletRotationPointLeft)
                .rotateY(outletRotation)
                .translateBack(outletRotationPointLeft)
                .light(light)
                .color(255, 255, 255, alpha);
        renderState.parts.add(new Part(outletLeft.extractRenderState(), type));
    }

    @Override
    protected SuperByteBuffer getRotatedModel(final PortableEngineBlockEntity te, final BlockState state) {
        return CachedBufferer.partialFacing(AllPartialModels.SHAFT_HALF, te.getBlockState(), te.getBlockState()
                .getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    protected SuperByteBuffer rotateToFacing(final SuperByteBuffer buffer, final Direction facing) {
        buffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP);
        return buffer;
    }
}
