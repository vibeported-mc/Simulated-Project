package dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.simulated_team.simulated.util.SimColors;
import dev.eriksonn.aeronautics.content.blocks.hot_air.GasEmitterRenderHandler;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <h2>26.2 note</h2>
 * <p>The redstone indicator, the steam base and the jet all read the block entity -- signal strength
 * and the emitter's alpha and position -- so all three are baked during extraction.
 *
 * <p>The jet's vertical offset is the one thing that cannot be baked into the buffer: it was applied
 * to the pose stack between two draws. The distance is measured during extraction and carried on the
 * state; the pose is moved during submission, which is the only phase that holds one.
 */
public class SteamVentRenderer
        extends SmartBlockEntityRenderer<SteamVentBlockEntity, SteamVentRenderer.SteamVentRenderState> {

    public static class SteamVentRenderState extends SmartRenderState {
        public @Nullable SuperByteBufferRenderState redstone;
        public @Nullable SuperByteBufferRenderState base;
        public @Nullable SuperByteBufferRenderState jet;
        public float jetOffset;
    }

    public SteamVentRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public SteamVentRenderState createRenderState() {
        return new SteamVentRenderState();
    }

    @Override
    protected void extractSafe(final SteamVentBlockEntity blockEntity, final SteamVentRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(blockEntity, state, partialTicks, cameraPosition);

        final float signalStrength = Math.max(0, blockEntity.signalStrength / 15F);
        final BlockState blockState = blockEntity.getBlockState();

        state.redstone = CachedBufferer
                .partial(AeroPartialModels.STEAM_VENT_REDSTONE, blockState)
                .light(state.lightCoords)
                .color(SimColors.redstone(signalStrength))
                .extractRenderState();

        final GasEmitterRenderHandler renderHandler = blockEntity.getRenderHandler();
        final int alpha = renderHandler.getAlpha(partialTicks);

        // Reused between frames, so the steam has to be cleared when it is not being drawn.
        if (alpha <= 2) {
            state.base = null;
            state.jet = null;
            return;
        }

        state.jetOffset = (renderHandler.getPosition(partialTicks) - 1) / 3.0f;
        state.base = CachedBufferer.partial(AeroPartialModels.STEAM_VENT_BASE, blockState)
                .disableDiffuse()
                .light(LightCoordsUtil.FULL_BRIGHT)
                .color(255, 255, 255, alpha)
                .extractRenderState();
        state.jet = CachedBufferer.partial(AeroPartialModels.STEAM_VENT_JET, blockState)
                .disableDiffuse()
                .light(LightCoordsUtil.FULL_BRIGHT)
                .color(255, 255, 255, alpha)
                .extractRenderState();
    }

    @Override
    protected void submitSafe(final SteamVentRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);

        if (state.redstone != null)
            state.redstone.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

        if (state.base == null || state.jet == null)
            return;

        ms.pushPose();
        state.base.submit(ms, RenderTypes.translucentMovingBlock(), queue);
        ms.translate(0.0f, state.jetOffset, 0.0f);
        state.jet.submit(ms, RenderTypes.translucentMovingBlock(), queue);
        ms.popPose();
    }
}
