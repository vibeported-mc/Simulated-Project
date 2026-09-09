package dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroPartialModels;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.util.SimColors;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderPipelines;
import com.simibubi.create.foundation.render.CachedBufferer;
import net.createmod.catnip.api.client.render.SuperByteBufferRenderState;
import net.createmod.ponder.api.client.level.PonderLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * <h2>26.2 note</h2>
 * <p>The flame was drawn by hand: a {@code Tesselator} quad pushed through
 * {@code BufferUploader.drawWithShader}, with three Veil uniforms set immediately before it. Neither
 * half survives. There is no immediate-mode draw in 26.2, and a render type's uniforms are fixed per
 * type -- a block entity gets no moment of its own in which to set one, because the queue decides
 * when the draw happens and batches by type.
 *
 * <p>So the three values move:
 *
 * <ul>
 *   <li>{@code Palette} has two values, so it becomes a per-render-type constant and the type is
 *       built once per palette.</li>
 *   <li>{@code Intensity} is 0..1 and rides in the vertex colour.</li>
 *   <li>{@code FlameRenderTime} accumulates at a rate that depends on the burner's own intensity, so
 *       it cannot come from a global clock. It rides in the lightmap channel, which is free because
 *       the flame is drawn full-bright, as fixed point across two 16-bit ints -- whole 256-second
 *       blocks in one, 1/256-second steps in the other.</li>
 * </ul>
 *
 * <p>{@code burner_flame.vsh} and {@code .fsh} read those as attributes now rather than as uniforms.
 * The flames stay independent per burner, which is what the uniforms bought.
 */
public class HotAirBurnerRenderer
        extends SmartBlockEntityRenderer<HotAirBurnerBlockEntity, HotAirBurnerRenderer.HotAirBurnerRenderState> {

    /**
     * <h2>26.2 note</h2>
     * <p>A render type's shader is compiled into a vanilla {@code RenderPipeline}, which resolves the
     * id under {@code assets/<namespace>/shaders/}. It is not a Veil program and cannot be one -- the
     * port had this pointing at {@code pinwheel/shaders/program}, where Veil keeps its own, and every
     * draw of the flame ended in "Pipeline contains invalid shader program".
     */
    private static final Identifier BURNER_FLAME_SHADER = Aeronautics.path("core/burner_flame");

    /** Read by the flame's fragment shader; the .png the palette row is sampled from. */
    private static final Identifier FIRE_PALETTE = Aeronautics.path("textures/effects/fire_palette.png");
    private static final float FLAME_SIZE = 2.0f;

    /** One render type per palette; the palette is the only value that could not move into a vertex. */
    private static final Map<Float, RenderType> FLAME_TYPES = new HashMap<>();

    private static synchronized RenderType flameType(final float palette) {
        return FLAME_TYPES.computeIfAbsent(palette, p -> RenderType.create(
                "aeronautics:burner_flame/" + p,
                VeilRenderBridge.createRenderType("aeronautics/burner_flame/" + p, DefaultVertexFormat.BLOCK)
                        .vertexShader(BURNER_FLAME_SHADER)
                        .fragmentShader(BURNER_FLAME_SHADER)
                        .texture("FirePalette", FIRE_PALETTE)
                        // The palette is the one value that could not move into a vertex, so it is
                        // baked in instead -- one compiled program per palette.
                        .shaderDefine("PALETTE", p)
                        .snippet(VeilRenderPipelines.translucentBlend())
                        // The depth state 1.21.1 got from its own `enableDepthTest` around the
                        // draw, and 26.2 does not give at all. See the note on SimRenderTypes.
                        .snippet(VeilRenderPipelines.defaultDepthTest())
                        .snippet(VeilRenderPipelines.noCull())
                        .useLightmap()
                        .create(false)));
    }

    public static class HotAirBurnerRenderState extends SmartRenderState {
        public @Nullable SuperByteBufferRenderState indicator;
        /** Null when the burner is off, or the flame would be invisible. */
        public @Nullable RenderType flameType;
        public float flameIntensity;
        /** Whole 256-second blocks, and 1/256-second steps within one. */
        public int timeHigh;
        public int timeLow;
        public float billboardAngle;
    }

    public HotAirBurnerRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public HotAirBurnerRenderState createRenderState() {
        return new HotAirBurnerRenderState();
    }

    @Override
    protected void extractSafe(final HotAirBurnerBlockEntity be, final HotAirBurnerRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        super.extractSafe(be, state, partialTicks, cameraPosition);

        final float signalStrength = Math.max(0, be.getSignalStrength() / 15F);
        state.indicator = CachedBufferer.partial(AeroPartialModels.HOT_AIR_BURNER_INDICATOR, be.getBlockState())
                .light(state.lightCoords)
                .color(SimColors.redstone(signalStrength))
                .extractRenderState();

        // Reused between frames, so a burner that went out has to clear its flame.
        state.flameType = null;

        if (signalStrength <= 0.0) {
            return;
        }

        final BlockPos pos = be.getBlockPos();
        final Vec3 center = Vec3.atCenterOf(pos);

        final Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.mainCamera().position();

        if (be.getLevel() instanceof PonderLevel) {
            camera = minecraft.getCameraEntity().getPosition(partialTicks);
        }

        final SubLevel sublevel = Sable.HELPER.getContaining(be);
        if (sublevel != null) {
            camera = sublevel.logicalPose().transformPositionInverse(camera);
        }

        final float angle = (float) Math.atan2(camera.z() - center.z(), camera.x() - center.x());
        state.billboardAngle = (float) (-angle + Math.PI * 0.5f);

        final HotAirBurnerBlock.Variant variant = be.getBlockState().getValue(HotAirBurnerBlock.VARIANT);
        state.flameType = flameType(variant == HotAirBurnerBlock.Variant.FIRE ? 0.25f : 0.75f);

        final float flameRenderTime = (float) Mth.lerp(partialTicks, be.lastRenderTime, be.renderTime) + be.getTimeOffset();
        // Fixed point across the two lightmap channels: 256-second blocks and 1/256-second steps.
        final float wrapped = flameRenderTime % (256.0f * 32767.0f);
        state.timeHigh = (int) Math.floor(wrapped / 256.0f);
        state.timeLow = (int) ((wrapped - state.timeHigh * 256.0f) * 256.0f);
        state.flameIntensity = be.getFlameIntensity(partialTicks);
    }

    @Override
    protected void submitSafe(final HotAirBurnerRenderState state, final PoseStack ms, final SubmitNodeCollector queue, final CameraRenderState camera) {
        super.submitSafe(state, ms, queue, camera);

        if (state.indicator != null)
            state.indicator.submit(ms, RenderTypes.cutoutMovingBlock(), queue);

        if (state.flameType == null)
            return;

        final int intensityByte = Mth.clamp((int) (state.flameIntensity * 255.0f), 0, 255);
        final int timeHigh = state.timeHigh;
        final int timeLow = state.timeLow;

        ms.pushPose();
        ms.translate(-0.5, 0.35, 0.5);
        ms.rotateAround(Axis.YP.rotation(state.billboardAngle), 1.0f, 0.0f, 0.0f);

        // 26.2: setLight takes one packed int now. This flame never carried real light -- it
        // smuggles a fixed-point time through the two lightmap channels -- so it writes them
        // directly with setUv2, which is what setLight unpacks into.
        queue.submitCustomGeometry(ms, state.flameType, (transform, builder) -> {
            final Matrix4f pose = transform.pose();
            builder.addVertex(pose, 0.0f, 0.0f, 0.0f).setColor(intensityByte, 0, 0, 255).setUv(0.0f, 1.0f).setUv2(timeLow, timeHigh);
            builder.addVertex(pose, FLAME_SIZE, 0.0f, 0.0f).setColor(intensityByte, 0, 0, 255).setUv(1.0f, 1.0f).setUv2(timeLow, timeHigh);
            builder.addVertex(pose, FLAME_SIZE, FLAME_SIZE, 0.0f).setColor(intensityByte, 0, 0, 255).setUv(1.0f, 0.0f).setUv2(timeLow, timeHigh);
            builder.addVertex(pose, 0.0f, FLAME_SIZE, 0.0f).setColor(intensityByte, 0, 0, 255).setUv(0.0f, 0.0f).setUv2(timeLow, timeHigh);
        });

        ms.popPose();
    }
}
