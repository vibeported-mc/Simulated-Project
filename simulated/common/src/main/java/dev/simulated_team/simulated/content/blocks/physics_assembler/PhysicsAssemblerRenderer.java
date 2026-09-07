package dev.simulated_team.simulated.content.blocks.physics_assembler;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.index.SimPartialModels;
import net.createmod.catnip.api.math.AngleHelper;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBuffer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The handle's angle is read from the block entity -- and {@code getRenderAngle} also initialises
 * the lever position as a side effect -- so all of that has to stay in the extract phase, where the
 * block entity is still the live one.
 */
public class PhysicsAssemblerRenderer
        extends SmartBlockEntityRenderer<PhysicsAssemblerBlockEntity, PhysicsAssemblerRenderer.PhysicsAssemblerRenderState> {

    public static class PhysicsAssemblerRenderState extends SmartRenderState {
        public @Nullable SuperByteBufferRenderState handle;
    }

    public PhysicsAssemblerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PhysicsAssemblerRenderState createRenderState() {
        return new PhysicsAssemblerRenderState();
    }

    @Override
    protected void extractSafe(final PhysicsAssemblerBlockEntity be, final PhysicsAssemblerRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, state, partialTicks, cameraPosition);

        final BlockState blockState = be.getBlockState();

        final SuperByteBuffer handle = CachedBufferer.partial(SimPartialModels.ASSEMBLER_LEVER, blockState);
        final float angle = getRenderAngle(be, partialTicks);
        this.transform(handle, blockState).translate(1 / 2f, 7 / 16f, 1 / 2f)
                .rotate(angle, Direction.EAST)
                .translate(-1 / 2f, -7 / 16f, -1 / 2f);
        state.handle = handle.light(state.lightCoords).extractRenderState();
    }

    @Override
    protected void submitSafe(final PhysicsAssemblerRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);
        if (state.handle != null)
            state.handle.submit(ms, RenderTypes.solidMovingBlock(), queue);
    }

    public static float getRenderAngle(final PhysicsAssemblerBlockEntity be, final float partialTicks) {
        if (!be.isVirtual()) {
            be.initializeLeverPosition();
        }

        return (float) Math.toRadians(be.getClientAngle(partialTicks));
    }

    private SuperByteBuffer transform(final SuperByteBuffer buffer, final BlockState leverState) {
        final AttachFace face = leverState.getValue(AnalogLeverBlock.FACE);
        final float rX = face == AttachFace.FLOOR ? 0 : face == AttachFace.WALL ? 90 : 180;
        final float rY = AngleHelper.horizontalAngle(leverState.getValue(AnalogLeverBlock.FACING));
        buffer.rotateCentered((float) (rY / 180 * Math.PI), Direction.UP);
        buffer.rotateCentered((float) (rX / 180 * Math.PI), Direction.EAST);
        return buffer;
    }
}
