package dev.eriksonn.aeronautics.neoforge.content.fluids;

import com.tterrag.registrate.builders.FluidBuilder;
import net.createmod.catnip.api.theme.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

/**
 * <h2>26.2 note</h2>
 * <p>A fluid type no longer carries its textures: they moved into a baked model held by the model
 * manager, which is also where the tint now lives. The two identifiers and their getters are gone,
 * and with them the third and second arguments of the factory -- {@code FluidTypeFactory} takes only
 * the properties, so the textures are named where the fluid is registered instead.
 *
 * <p>Only the fog is still a client extension.
 */
public abstract class AeroFluidType extends FluidType implements IClientFluidTypeExtensions {
	private Vector3f fogColor;
	private Supplier<Float> fogDistance;

	public AeroFluidType(Properties properties) {
		super(properties);
	}

	public static FluidBuilder.FluidTypeFactory create(int fogColor, Supplier<Float> fogDistance, Factory factory) {
		return p -> {
			AeroFluidType fluidType = factory.create(p);
			fluidType.fogColor = new Color(fogColor, false).asVectorF();
			fluidType.fogDistance = fogDistance;
			return fluidType;
		};
	}

	public interface Factory {
		AeroFluidType create(Properties properties);
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>The fog colour is written into a {@code Vector4f} the caller owns rather than returned, and
	 * the alpha channel it arrives with is left alone -- this fluid only ever had a colour to give.
	 */
	@Override
	public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
			float darkenWorldAmount, Vector4f fluidFogColor) {
		Vector3f customFogColor = this.getCustomFogColor();
		if (customFogColor != null)
			fluidFogColor.set(customFogColor.x, customFogColor.y, customFogColor.z, fluidFogColor.w);
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>Fog is described by filling in a {@link FogData} rather than by pushing uniforms through
	 * {@code RenderSystem}, and the {@code FogMode} became a {@code FogEnvironment} that may be null.
	 *
	 * <p>The cylindrical fog shape has no equivalent to set: {@code FogData} carries distances only,
	 * and the shape is the renderer's to choose. Everything else is the same pair of distances this
	 * always wrote.
	 */
	@Override
	public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance,
			float partialTick, FogData fogData) {
		float modifier = this.getFogDistanceModifier();
		float baseWaterFog = 96.0f;
		if (modifier != 1.0f) {
			fogData.environmentalStart = -8;
			fogData.environmentalEnd = baseWaterFog * modifier;
		}
	}

	public Vector3f getCustomFogColor() {
		return this.fogColor;
	}

	public float getFogDistanceModifier() {
		return this.fogDistance.get();
	}
}
