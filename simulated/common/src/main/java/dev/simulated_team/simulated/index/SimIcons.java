package dev.simulated_team.simulated.index;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.simulated_team.simulated.Simulated;
import net.createmod.catnip.api.client.gui.element.DelegatedStencilElement;
import net.createmod.catnip.api.theme.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public class SimIcons extends AllIcons {
    public static final Identifier ICON_ATLAS = Simulated.path("textures/gui/icons.png");
    public static final int ICON_ATLAS_SIZE = 64;

    private static int x = 0, y = -1;
    private final int iconX;
    private final int iconY;

    public static final SimIcons
            //Docking connector
            HALF_EXTEND = newRow(),
            FULL_EXTEND = next(),

            //linked typewriter
            ADD_OR_EDIT = newRow(),
            HAMBURGER = next(),
            CANCEL = next(),
            CONFIG = next(),

            //small arrows
            KEY_ARROW_UP = newRow(),
            KEY_ARROW_LEFT = next(),
            KEY_ARROW_DOWN = next(),
            KEY_ARROW_RIGHT = next();

    public SimIcons(final int x, final int y) {
        super(x, y);
        this.iconX = x * 16;
        this.iconY = y * 16;
    }

    private static SimIcons next() {
        return new SimIcons(++x, y);
    }

    private static SimIcons newRow() {
        return new SimIcons(x = 0, ++y);
    }

    @Override
    public void render(final GuiGraphicsExtractor graphics, final int x, final int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_ATLAS, x, y, this.iconX, this.iconY, 16, 16, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE);
    }

    /** The same icon tinted, which used to be a {@code RenderSystem.setShaderColor} around the draw. */
    public void render(final GuiGraphicsExtractor graphics, final int x, final int y, final int color) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON_ATLAS, x, y, this.iconX, this.iconY, 16, 16, ICON_ATLAS_SIZE, ICON_ATLAS_SIZE, color);
    }

    /**
     * Queue this icon as a quad in the world.
     *
     * <h2>26.2 note</h2>
     * <p>A vertex consumer is only handed out at draw time now, so the quad is written from a custom
     * geometry node. The record carries everything it needs, which keeps it safe to draw from
     * whichever thread reaches it.
     */
    public void submit(final PoseStack ms, final SubmitNodeCollector queue, final int color) {
        queue.submitCustomGeometry(ms, RenderTypes.text(ICON_ATLAS), new IconGeometry(this.iconX, this.iconY, color));
    }

    private record IconGeometry(int iconX, int iconY, int color)
            implements SubmitNodeCollector.CustomGeometryRenderer {
        @Override
        public void render(final PoseStack.Pose pose, final VertexConsumer builder) {
            final Matrix4f matrix = pose.pose();
            final int light = LightCoordsUtil.FULL_BRIGHT;
            final Color rgb = new Color(this.color);

            final float u1 = this.iconX * 1f / ICON_ATLAS_SIZE;
            final float u2 = (this.iconX + 16) * 1f / ICON_ATLAS_SIZE;
            final float v1 = this.iconY * 1f / ICON_ATLAS_SIZE;
            final float v2 = (this.iconY + 16) * 1f / ICON_ATLAS_SIZE;

            vertex(builder, matrix, 0, 0, rgb, u1, v1, light);
            vertex(builder, matrix, 0, 1, rgb, u1, v2, light);
            vertex(builder, matrix, 1, 1, rgb, u2, v2, light);
            vertex(builder, matrix, 1, 0, rgb, u2, v1, light);
        }

        private static void vertex(final VertexConsumer builder, final Matrix4f matrix, final float x, final float y,
                                   final Color rgb, final float u, final float v, final int light) {
            builder.addVertex(matrix, x, y, 0)
                    .setColor(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), 255)
                    .setUv(u, v)
                    .setLight(light);
        }
    }

    public DelegatedStencilElement asStencil() {
        return new DelegatedStencilElement().withStencilRenderer((ms, w, h, alpha) -> this.render(ms, 0, 0)).withBounds(16, 16);
    }

}
