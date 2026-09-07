package dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.PoseTransformStack;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.client.animation.AnimationTickHolder;
import net.createmod.catnip.api.animation.LerpedFloat;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Vector;


/**
 * <h2>26.2 note</h2>
 * <p>Every key is the same two models, placed by a walk down the pose stack and pushed in by its own
 * depression. Only the depressions read the block entity -- whether this player is the one typing --
 * so extraction measures those and bakes the two models once; submission walks the poses and queues
 * the right model at each stop.
 *
 * <p>{@code facing} is carried on the state because the whole keyboard is turned by it, and a turn
 * of the pose is not something a baked model can hold.
 */
public class LinkedTypewriterRenderer
        extends SmartBlockEntityRenderer<LinkedTypewriterBlockEntity, LinkedTypewriterRenderer.LinkedTypewriterRenderState> {

    static Vector<LerpedFloat> keys = new Vector<>(14);

    /** 13 keys and a space bar. */
    private static final int KEY_COUNT = 14;

    public static class LinkedTypewriterRenderState extends SmartRenderState {
        public @Nullable SuperByteBufferRenderState key;
        public @Nullable SuperByteBufferRenderState spacebar;
        public final float[] depressions = new float[KEY_COUNT];
        public @Nullable Direction facing;
    }

    public LinkedTypewriterRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LinkedTypewriterRenderState createRenderState() {
        return new LinkedTypewriterRenderState();
    }

    static {
        for (int i = 0; i < KEY_COUNT; ++i) {
            keys.add(LerpedFloat.linear().startWithValue(0.0));
        }
    }

    public static void tick() {
        if (Minecraft.getInstance()
                .isPaused())
            return;

        if (LinkedTypewriterInteractionHandler.getMode() == LinkedTypewriterInteractionHandler.Mode.IDLE) {
            return;
        }

        for (int i = 0; i < keys.size(); i++) {
            final LerpedFloat lerpedFloat = keys.get(i);
            lerpedFloat.chase(LinkedTypewriterInteractionHandler.getPressedKeys().contains(i) ? 1 : 0, .4f, LerpedFloat.Chaser.EXP);
            lerpedFloat.tickChaser();
        }
    }

    public static void resetKeys() {
        for (final LerpedFloat key : keys) {
            key.startWithValue(0.0);
        }
    }

    @Override
    protected void extractSafe(final LinkedTypewriterBlockEntity be, final LinkedTypewriterRenderState renderState, final float partialTicks,
                               final Vec3 cameraPosition) {
        super.extractSafe(be, renderState, partialTicks, cameraPosition);

        final BlockState blockState = be.getBlockState();
        renderState.facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);

        final float pt = AnimationTickHolder.getPartialTicks();
        final float s = 0.0625F;
        final float b = s * -0.75F;

        int light = renderState.lightCoords;
        if (LinkedTypewriterInteractionHandler.getMode() == LinkedTypewriterInteractionHandler.Mode.BIND) {
            final int i = (int) Mth.lerp((Mth.sin(AnimationTickHolder.getRenderTime() / 4.0F) + 1.0F) / 2.0F, 5.0F, 15.0F);
            light = i << 20;
        }

        final boolean thisPlayer = be.checkUser(Minecraft.getInstance().player.getUUID());
        for (int i = 0; i < KEY_COUNT; i++) {
            renderState.depressions[i] = thisPlayer ? b * keys.get(i).getValue(pt) : 0;
        }

        renderState.key = CachedBufferer.partial(SimPartialModels.LINKED_TYPEWRITER_KEY, blockState)
                .light(light).extractRenderState();
        renderState.spacebar = CachedBufferer.partial(SimPartialModels.LINKED_TYPEWRITER_KEY_SPACEBAR, blockState)
                .light(light).extractRenderState();
    }

    @Override
    protected void submitSafe(final LinkedTypewriterRenderState renderState, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(renderState, ms, queue, camera);

        if (renderState.key == null || renderState.spacebar == null || renderState.facing == null)
            return;

        final TransformStack<PoseTransformStack> ps = TransformStack.of(ms);
        final float s = 0.0625F;
        int index = 0;

        // Account for block rotation
        ps.pushPose();
        ps.translate(0.5, 4 * s, 0.5);
        ps.rotateYDegrees(AngleHelper.horizontalAngle(renderState.facing));

        // Top Row
        ps.translate(-7 * s, s, 2 * s);
        ps.pushPose();
        for (int i = 0; i < 6; i++) {
            ps.translate(2 * s, 0.0, 0.0);
            submitKey(ms, queue, renderState, index++, false);
        }
        ms.popPose();

        // Bottom Row
        ps.translate(-1 * s, -s, 2 * s);
        ps.pushPose();
        for (int i = 0; i < 7; i++) {
            ps.translate(2 * s, 0.0, 0.0);
            submitKey(ms, queue, renderState, index++, false);
        }
        ms.popPose();

        // Space Bar
        ps.translate(8 * s, -s, 2 * s);
        ps.pushPose();
        submitKey(ms, queue, renderState, index, true);
        ms.popPose();

        ms.popPose();
    }

    private static void submitKey(final PoseStack ms, final SubmitNodeCollector queue, final LinkedTypewriterRenderState renderState,
                                  final int index, final boolean isSpacebar) {
        ms.pushPose();
        ms.translate(0.0F, renderState.depressions[index], 0.0F);

        final SuperByteBufferRenderState model = isSpacebar ? renderState.spacebar : renderState.key;
        if (model != null)
            model.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

        ms.popPose();
    }
}
