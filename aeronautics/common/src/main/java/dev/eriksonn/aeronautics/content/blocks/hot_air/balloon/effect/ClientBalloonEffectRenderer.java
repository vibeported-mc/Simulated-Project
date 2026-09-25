package dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import foundry.veil.api.client.render.ext.VeilGlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.content.blocks.hot_air.BlockEntityLiftingGasProvider;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ClientBalloon;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class ClientBalloonEffectRenderer {

    private static final Identifier FBO_ID = Aeronautics.path("soft_light");
    private static final Identifier POST_SHADER_ID = Aeronautics.path("soft_light");

    private static final Identifier SHADER_ID = Aeronautics.path("hot_air_overlay");

   @Nullable
    private static AdvancedFbo overlayFbo;



    public static void onRenderLevelStage(final VeilRenderLevelStageEvent.Stage stage,
                                          final Matrix4fc frustumMatrix,
                                          final Matrix4fc projectionMatrix,
                                          final int renderTick,
                                          final net.minecraft.client.Camera camera) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }


        final Minecraft minecraft = Minecraft.getInstance();
        final ClientLevel level = minecraft.level;
        if (level == null) {
            freeFbo();
            return;
        }

        final BalloonMap ballonMap = BalloonMap.MAP.get(level);
        if (ballonMap.isEmpty()) {
            freeFbo();
            return;
        }

        final Window window = minecraft.getWindow();
        if (overlayFbo == null || overlayFbo.getWidth() != window.getWidth() || overlayFbo.getHeight() != window.getHeight()) {
            freeFbo();
            overlayFbo = AdvancedFbo.withSize(window.getWidth(), window.getHeight())
                    .addColorTextureBuffer()
                    .setDepthTextureBuffer()
                    .build(true);
        }

        renderBalloonEffects(ballonMap, frustumMatrix, projectionMatrix, renderTick, camera);
    }

    /**
     * Renders the balloon effects for a given balloon map
     * @param balloonMap the balloon map to render effects for
     * @param frustumMatrix the model view matrix
     * @param projectionMatrix the projection matrix
     * @param renderTick the render-tick
     */
    private static void renderBalloonEffects(final BalloonMap balloonMap, final Matrix4fc frustumMatrix, final Matrix4fc projectionMatrix, final int renderTick, final net.minecraft.client.Camera camera) {
        final Minecraft minecraft = Minecraft.getInstance();
        final float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        final ShaderProgram shader = VeilRenderSystem.setShader(SHADER_ID);
        if (shader == null) return;


        // Depth cleared to 1.0, which under 26.2's reversed buffer is the *near* plane -- the
        // opposite of what an empty depth buffer meant on every version before this one, and the
        // opposite of what the rest of the frame clears to.
        //
        // Because this pass keeps the farthest surface rather than the nearest one. See the depth
        // state below: the two have to agree, and a buffer cleared to "far" with a test that keeps
        // the farther fragment rejects everything and leaves the overlay empty.
        overlayFbo.clear(0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
                GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

        // What glCullFace(GL_FRONT) and glPolygonOffset(-0.5, -30) did on 1.21.1, said in the only
        // vocabulary 26.2 has for it.
        //
        // Front-face culling was never about culling. The balloon is a closed volume and the point
        // was to draw its *far* surface, so the depth this leaves behind is where the hot air ends
        // rather than where it begins -- which is what soft_light compares against the world. But
        // Blaze3D culls back faces or nothing; there is no front-face option. So the far surface is
        // selected by the depth test instead: under a reversed buffer LESS_THAN_OR_EQUAL keeps the
        // smaller depth, and smaller is farther. It survives a non-convex volume, which front-face
        // culling does not.
        //
        // No depth bias, where 1.21.1 had a polygon offset -- and the reason is worth keeping.
        //
        // The offset existed because the volume's boundary is exactly the inner face of the
        // envelope blocks, so the two depths soft_light compares are the same number and the
        // comparison goes either way per pixel. A bias hides that by pushing one of them, and
        // on a reversed float buffer there is no setting that hides it without overshooting:
        // too little leaves a crawling stipple over every surface, and enough to clear the
        // stipple at a grazing angle pushes the overlay in front of the envelope, so the
        // effect paints on the outside of solid blocks in the shape of the volume behind them.
        // Both were seen; the second is the drifting hard-edged wedge reported from play.
        //
        // The ambiguity belongs to the comparison, so it is settled there: soft_light requires
        // the world to be nearer by a margin before it treats the effect as hidden, which gives
        // coincident surfaces one answer instead of a coin toss and leaves the depth honest.
        final DepthStencilState depthState =
                new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);

        // 26.2: RenderSystem.setShaderTexture is gone, and with it the two constants that named the
        // side and top textures here. The samplers this program reads are declared in
        // hot_air_overlay.json instead, which is how Veil binds a program's textures -- the same
        // mechanism levitite already used for its noise sampler.
        // Off OpenGL this state belongs to the pipeline and is set when the pass is opened,
        // and calling GlStateManager without a context reaches raw GL with no context behind
        // it. So it is set here only where there is a state machine to set it in.
        //
        // Two of these have no counterpart in the pipeline, and the overlay differs because
        // of it. Blaze3D culls back faces or nothing -- there is no front-face option -- so
        // the depth this writes is the near surface of the balloon rather than the far one.
        // And a pipeline carries a depth bias but Veil has no channel to ask for one, so the
        // polygon offset that pushed the overlay in front of block faces is absent.
        if (VeilGlDevice.isSupported()) {
            GlStateManager._enableCull();
            GlStateManager._depthMask(true);
            GlStateManager._enableDepthTest();

            // Set, not inherited. 26.2 reverses the depth buffer on OpenGL too, so vanilla leaves
            // GL_GEQUAL behind and this pass clears its depth to the near plane -- under which
            // GEQUAL rejects every fragment and the overlay comes out empty. On 1.21.1 the
            // inherited GL_LEQUAL happened to be the one this wanted, which is why nothing here
            // ever named it.
            GlStateManager._depthFunc(GL11.GL_LEQUAL);

            GL30.glCullFace(GL11.GL_FRONT);

            // Polygon offset to be before blocks, with the sign the reversed buffer needs: towards
            // the camera is towards larger depth here, where on 1.21.1 it was towards smaller.
            GlStateManager._polygonOffset(0.5F, 30.0F);
            GlStateManager._enablePolygonOffset();
        }

        final float scrollAmount = (renderTick + partialTicks) / -20.0f;

        final ShaderUniformAccess scrollUniform = shader.getUniformSafe("Scroll");
        final ShaderUniformAccess yCutoffUniform = shader.getUniformSafe("CutoffY");

        final float scrollValue = (float) (Math.floor(scrollAmount * 16.0f) / 16.0f);
        scrollUniform.setFloat(scrollValue);

        // 26.2: setShaderColor drove a vanilla uniform that no longer exists. The program declares
        // ColorModulator itself, so it is set by name like every other uniform here.
        final float brightness = 0.85f;
        final float alpha = 1.0f;
        shader.getUniformSafe("ColorModulator").setVector(brightness, brightness, brightness, alpha);

        final Matrix4f modelViewMat = new Matrix4f(frustumMatrix);
        final Matrix4f projMat = new Matrix4f(projectionMatrix);

        for (final Balloon balloon : balloonMap.getBalloons()) {
            final ClientBalloon clientBalloon = (ClientBalloon) balloon;

            final HeatedCulledRenderRegion renderRegion = clientBalloon.getRenderRegion();

            if (renderRegion == null) {
                continue;
            }

            float filledPercent = 0.0f;

            // These should all have the same client predicted volume, but we pick the max
            // to be safe
            for (final BlockEntityLiftingGasProvider heater : balloon.getHeaters()) {
                filledPercent = Math.max(filledPercent, (float) heater.getClientPredictedVolume() / balloon.getCapacity());
            }

            filledPercent = Mth.clamp(filledPercent, 0.0f, 1.0f);
            final float cutoff = (1.0f - filledPercent) * (balloon.getHeight() + 1.0f);
            yCutoffUniform.setFloat(cutoff);

            renderRegion.render(overlayFbo, shader, depthState, camera, modelViewMat, projMat);
        }

        // Cleanup render state. Nothing to undo off OpenGL, where none of it was set and
        // where nothing stays bound past the pass that used it.
        if (VeilGlDevice.isSupported()) {
            GlStateManager._polygonOffset(0.0F, 0.0F);
            GlStateManager._disablePolygonOffset();
            GlStateManager._depthFunc(GL11.GL_GEQUAL);
            GL30.glCullFace(GL11.GL_BACK);
            AdvancedFbo.unbind();
        }

        shader.getUniformSafe("ColorModulator").setVector(1.0f, 1.0f, 1.0f, 1.0f);

        applyHeatingToScreen();
    }

    private static void applyHeatingToScreen() {
        final PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
        final PostPipeline pipeline = manager.getPipeline(POST_SHADER_ID);
        final PostPipeline.Context context = manager.getPostPipelineContext();

        context.setFramebuffer(FBO_ID, overlayFbo);
        manager.runPipeline(pipeline);
    }

    private static void freeFbo() {
        if (overlayFbo != null) {
            overlayFbo.free();
        }

        overlayFbo = null;
    }
}
