package dev.simulated_team.simulated.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws a rectangle out of a texture sheet.
 *
 * <h2>26.2 note -- why this exists</h2>
 * <p>{@code GuiGraphics.blit(location, x, y, uOffset, vOffset, width, height, textureWidth,
 * textureHeight)} is gone. The nine-argument overload that took its place means something else
 * entirely:
 *
 * <pre>{@code blit(Identifier location, int x0, int y0, int x1, int y1,
 *      float u0, float u1, float v0, float v1)}</pre>
 *
 * <p>Two opposite corners, and normalised texture coordinates in the order u0, u1, v0, v1 -- not
 * a position and a size, and not offsets in pixels.
 *
 * <p>Nothing warns about this. The old call has nine arguments too, and its ints widen to the new
 * floats, so every old call site still compiles and quietly draws a rectangle from (x, y) to
 * (uOffset, vOffset) -- which for the usual {@code uOffset = 0, vOffset = 0} is a zero-area
 * rectangle, and therefore nothing at all. That is what happened to every HUD overlay in this mod:
 * the assembler's track, the steering wheel, the diagram's own sprites. They were being drawn, with
 * correct state, at zero size.
 *
 * <p>Call sites keep the arguments they always had and this does the conversion, so the pixel
 * offsets in them still line up with the sheets they were measured against.
 */
public final class SimGuiBlit {

    private SimGuiBlit() {
    }

    /**
     * @param graphics      the extractor to record into
     * @param location      the texture sheet
     * @param x             left edge, in GUI pixels
     * @param y             top edge, in GUI pixels
     * @param uOffset       left edge within the sheet, in texture pixels
     * @param vOffset       top edge within the sheet, in texture pixels
     * @param width         width to draw, in pixels, also the width sampled from the sheet
     * @param height        height to draw, in pixels, also the height sampled from the sheet
     * @param textureWidth  the sheet's full width, for normalising
     * @param textureHeight the sheet's full height, for normalising
     */
    public static void blit(final GuiGraphicsExtractor graphics, final Identifier location,
                            final int x, final int y, final int uOffset, final int vOffset,
                            final int width, final int height,
                            final int textureWidth, final int textureHeight) {
        graphics.blit(location,
                x, y, x + width, y + height,
                (float) uOffset / textureWidth, (float) (uOffset + width) / textureWidth,
                (float) vOffset / textureHeight, (float) (vOffset + height) / textureHeight);
    }
}
