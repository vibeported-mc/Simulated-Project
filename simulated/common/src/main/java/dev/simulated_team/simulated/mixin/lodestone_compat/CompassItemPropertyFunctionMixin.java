package dev.simulated_team.simulated.mixin.lodestone_compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.simulated_team.simulated.content.navigation_targets.lodestone_compass_compatability.ClientLodestonePositions;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.client.renderer.item.properties.numeric.CompassAngleState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

/**
 * <h2>26.2 note</h2>
 * <p>{@code CompassItemPropertyFunction} became {@link CompassAngleState}, an item property under
 * {@code properties.numeric}. Its entry point is {@code calculate}, which takes an
 * {@code ItemOwner} rather than an {@code Entity} -- the compass can now be held by things that are
 * not entities -- and the two helpers this reuses went private, which a shadow can still reach.
 */
@Mixin(CompassAngleState.class)
public abstract class CompassItemPropertyFunctionMixin {

	@Shadow
	private native float getRotationTowardsCompassTarget(ItemOwner owner, long gameTime, BlockPos compassTargetPos);

	@Shadow
	private native float getRandomlySpinningRotation(int seed, long gameTime);

	@WrapMethod(method = "calculate")
	private float simulated$prioritizeID(final ItemStack stack, final ClientLevel level, final int seed, final ItemOwner owner, final Operation<Float> original) {
		if (stack.has(SimDataComponents.LODESTONE_COMPASS_SUBLEVEL_TRACKER)) {
			final UUID trackerID = stack.get(SimDataComponents.LODESTONE_COMPASS_SUBLEVEL_TRACKER);
			final ClientLodestonePositions positions = ClientLodestonePositions.clientPositions.get(level);

			final Vector3d pos = positions.CLIENT_LODESTONE_MAP.get(trackerID);
			if (pos != null) {
				return this.getRotationTowardsCompassTarget(owner, level.getGameTime(), BlockPos.containing(pos.x, pos.y, pos.z));
			} else {
				return this.getRandomlySpinningRotation(seed, level.getGameTime());
			}
		}

		return original.call(stack, level, seed, owner);
	}
}
