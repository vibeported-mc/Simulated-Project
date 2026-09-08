package dev.simulated_team.simulated.mixin.accessor;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the 16x16 texture behind the level's {@link Lightmap}.
 *
 * <p>{@code Lightmap} exposes only a {@code GpuTextureView}, which cannot be written to. The
 * diagram needs the {@link GpuTexture} itself so it can upload its own texels rather than ask
 * {@code render(LightmapRenderState)} to compute something close to them.
 */
@Mixin(Lightmap.class)
public interface LightmapTextureAccessor {

    @Accessor("texture")
    GpuTexture simulated$getTexture();
}
