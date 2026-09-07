package dev.eriksonn.aeronautics.neoforge.content.fluids;

import com.tterrag.registrate.builders.FluidBuilder;
import net.createmod.catnip.api.theme.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.function.Supplier;

public abstract class AeroFluidType extends FluidType implements IClientFluidTypeExtensions {
	private Vector3f fogColor;
	private Supplier<Float> fogDistance;
	private final Identifier stillTexture;
	private final Identifier flowingTexture;

	public AeroFluidType(Properties properties, Identifier stillTexture, Identifier flowingTexture) {
		super(properties);
		this.stillTexture = stillTexture;
		this.flowingTexture = flowingTexture;
	}

	public static FluidBuilder.FluidTypeFactory create(int fogColor, Supplier<Float> fogDistance, Factory factory) {
		return (p, s, f) -> {
			AeroFluidType fluidType = factory.create(p, s, f);
			fluidType.fogColor = new Color(fogColor, false).asVectorF();
			fluidType.fogDistance = fogDistance;
			return fluidType;
		};
	}

	public interface Factory {
		AeroFluidType create(Properties properties, Identifier stillTexture, Identifier flowingTexture);
	}

	@Override
	public @NotNull Identifier getStillTexture() {
		return this.stillTexture;
	}

	@Override
	public @NotNull Identifier getFlowingTexture() {
		return this.flowingTexture;
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
