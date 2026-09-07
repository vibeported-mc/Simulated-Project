package dev.simulated_team.simulated.util.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * <h2>26.2 note</h2>
 * <p>A textured GUI quad whose top and bottom edges carry different colours.
 *
 * <p>Screens used to draw this by hand -- bind the texture, set a shader, open a {@code Tesselator},
 * write four vertices, {@code BufferUploader.drawWithShader}. None of that exists any more: GUI
 * drawing is collected as render states and replayed later, so anything the vanilla {@code blit}
 * calls cannot express has to arrive as its own {@link GuiElementRenderState}. Catnip's
 * {@code TexturedQuadRenderState} is the flat-colour form of this; the only thing added here is a
 * second colour, so a texture can fade out along one axis.
 */
public record FadedTexturedQuadRenderState(
        Matrix3x2f pose,
        @Nullable ScreenRectangle scissorArea,
        TextureSetup textureSetup,
        int topColor,
        int bottomColor,
        float left, float right, float top, float bottom,
        float u1, float u2, float v1, float v2
) implements GuiElementRenderState {

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public void buildVertices(final VertexConsumer consumer) {
        consumer.addVertexWith2DPose(this.pose, this.left, this.top).setUv(this.u1, this.v1).setColor(this.topColor);
        consumer.addVertexWith2DPose(this.pose, this.left, this.bottom).setUv(this.u1, this.v2).setColor(this.bottomColor);
        consumer.addVertexWith2DPose(this.pose, this.right, this.bottom).setUv(this.u2, this.v2).setColor(this.bottomColor);
        consumer.addVertexWith2DPose(this.pose, this.right, this.top).setUv(this.u2, this.v1).setColor(this.topColor);
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        final ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(this.left), (int) Math.floor(this.top),
                (int) Math.ceil(this.right - this.left), (int) Math.ceil(this.bottom - this.top))
                .transformMaxBounds(this.pose);
        return this.scissorArea != null ? this.scissorArea.intersection(bounds) : bounds;
    }
}
