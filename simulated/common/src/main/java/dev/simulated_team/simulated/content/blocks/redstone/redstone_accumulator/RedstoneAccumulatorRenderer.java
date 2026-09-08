package dev.simulated_team.simulated.content.blocks.redstone.redstone_accumulator;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimPartialModels;
import foundry.veil.api.client.render.VeilRenderBridge;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The diode's own render type was built from {@code CompositeState} and {@code RenderStateShard},
 * neither of which exists. It is assembled through Veil's builder now, as the types in
 * {@code SimRenderTypes} are -- see that class for the shard-by-shard mapping. The old builder set a
 * shader twice, vanilla cutout then Veil's, and the second won; only the winner is named here.
 */
public class RedstoneAccumulatorRenderer
        extends SmartBlockEntityRenderer<RedstoneAccumulatorBlockEntity, RedstoneAccumulatorRenderer.RedstoneAccumulatorRenderState> {

    public static Identifier SHADER_NAME = Simulated.path("core/redstone_accumulator/diode");
    public static RenderType DIODE_RENDER_TYPE = RenderType.create("redstone_accumulator_diode",
            VeilRenderBridge.createRenderType("redstone_accumulator_diode", DefaultVertexFormat.BLOCK)
                    .vertexShader(SHADER_NAME)
                    .fragmentShader(SHADER_NAME)
                    .texture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useLightmap()
                    .affectsCrumbling()
                    .create(true));

    public static class RedstoneAccumulatorRenderState extends SmartRenderState {
        public @Nullable SuperByteBufferRenderState diode;
    }

    public RedstoneAccumulatorRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public RedstoneAccumulatorRenderState createRenderState() {
        return new RedstoneAccumulatorRenderState();
    }

    @Override
    protected void extractSafe(final RedstoneAccumulatorBlockEntity be, final RedstoneAccumulatorRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, state, partialTicks, cameraPosition);

        final SuperByteBuffer render = CachedBufferer.partial(SimPartialModels.REDSTONE_ACCUMULATOR_DIODE, be.getBlockState())
                .color(255, 255, 255, this.getLitAmount(be, partialTicks));

        final Direction facing = be.getBlockState().getValue(RedstoneAccumulatorBlock.FACING);
        render.light(state.lightCoords);
        render.translate(0.5, 0, 0.5);
        render.rotateYDegrees(AngleHelper.horizontalAngle(facing));
        // 26.2 moved the pose stack off SuperByteBuffer itself; getTransforms() is the same stack.
        // Nothing pops it, so this only duplicates the top entry -- kept so the transform this
        // renderer builds is literally the one it built before.
        render.getTransforms().pushPose();
        state.diode = render.extractRenderState();
    }

    @Override
    protected void submitSafe(final RedstoneAccumulatorRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        if (state.diode != null)
            state.diode.submit(ms, DIODE_RENDER_TYPE, queue);
    }

    private int getLitAmount(final RedstoneAccumulatorBlockEntity be, final float partialTicks) {
        float state = be.lerpedState.getValue(partialTicks);
        // ^1.5 is for gamma correction, otherwise dark change is too quick and light change is barely noticeable
        state = 1 - (float) Math.pow(state / 15F, 1.5);
        return (int) Mth.clamp(state * 255, 0, 255);
    }
}
