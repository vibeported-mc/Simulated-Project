package dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.effect;

import com.mojang.blaze3d.platform.Window;
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
                                          final int renderTick) {
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }


        // The overlay draws into its own framebuffer, from inside the level render.
        //
        // On OpenGL that is a framebuffer switch mid-frame, which is what the API was built for.
        // Off it there is no switch: drawing into another target means opening a render pass, and
        // this event fires while one is already open -- `Close the existing render pass before
        // creating a new one!`. The overlay has to be drawn somewhere a pass can be opened, which
        // is not here, so it is a restructuring rather than a port.
        if (!VeilGlDevice.isSupported()) {
            freeFbo();
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

        renderBalloonEffects(ballonMap, frustumMatrix, projectionMatrix, renderTick);
    }

    /**
     * Renders the balloon effects for a given balloon map
     * @param balloonMap the balloon map to render effects for
     * @param frustumMatrix the model view matrix
     * @param projectionMatrix the projection matrix
     * @param renderTick the render-tick
     */
    private static void renderBalloonEffects(final BalloonMap balloonMap, final Matrix4fc frustumMatrix, final Matrix4fc projectionMatrix, final int renderTick) {
        final Minecraft minecraft = Minecraft.getInstance();
        final float partialTicks = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        final ShaderProgram shader = VeilRenderSystem.setShader(SHADER_ID);
        if (shader == null) return;


        // Depth cleared to zero, which is *far* under 26.2's reversed depth buffer. The five-argument
        // clear passes 1.0, because that is what an empty depth buffer meant on every version before
        // this one -- and here 1.0 is nearest, so everything drawn into this framebuffer afterwards
        // fails the depth test and the overlay comes out empty. That was true on OpenGL too.
        overlayFbo.clear(0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);

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

            GL30.glCullFace(GL11.GL_FRONT);

            // Polygon offset to be before blocks
            GlStateManager._polygonOffset(-0.5F, -30.0F);
            GlStateManager._enablePolygonOffset();
        }

        final float scrollAmount = (renderTick + partialTicks) / -20.0f;

        final ShaderUniformAccess scrollUniform = shader.getUniformSafe("Scroll");
        final ShaderUniformAccess yCutoffUniform = shader.getUniformSafe("CutoffY");

        scrollUniform.setFloat((float) (Math.floor(scrollAmount * 16.0f) / 16.0f));

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
            yCutoffUniform.setFloat((1.0f - filledPercent) * (balloon.getHeight() + 1.0f));

            renderRegion.render(overlayFbo, shader, modelViewMat, projMat);
        }

        // Cleanup render state. Nothing to undo off OpenGL, where none of it was set and
        // where nothing stays bound past the pass that used it.
        if (VeilGlDevice.isSupported()) {
            GlStateManager._polygonOffset(0.0F, 0.0F);
            GlStateManager._disablePolygonOffset();
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
