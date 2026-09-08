package dev.simulated_team.simulated.mixin.accessor;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the level's {@link Lightmap} object itself.
 *
 * <p>{@code GameRenderer.lightmap()} looks like the getter for this but is not: it returns a
 * {@code GpuTextureView}, and switches between the level lightmap and the UI one. The diagram needs
 * the {@link Lightmap} because {@code render(LightmapRenderState)} is the only way to rewrite the
 * texture, and the field holding it is private.
 */
@Mixin(GameRenderer.class)
public interface GameRendererLightmapAccessor {

    @Accessor("lightmap")
    Lightmap simulated$getLightmap();
}
