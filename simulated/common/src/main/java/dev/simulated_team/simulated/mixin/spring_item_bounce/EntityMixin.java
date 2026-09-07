package dev.simulated_team.simulated.mixin.spring_item_bounce;

import dev.simulated_team.simulated.data.advancements.SimAdvancements;
import dev.simulated_team.simulated.index.SimDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow public abstract void playSound(SoundEvent soundEvent, float f, float g);

    @Shadow public abstract Level level();

    @Shadow public abstract Vec3 getPosition(float partialTicks);

    /**
     * <h2>26.2 note</h2>
     * <p>{@code Block.updateEntityAfterFallOn} is gone: bouncing is resolved inside
     * {@code Entity.restituteMovementAfterCollisions}, from a restitution that is the greater of the
     * block's {@code bounceRestitution} and the entity's own {@code getEntityBounciness}. The latter
     * returns zero by default and exists to be overridden, which is exactly what a bouncy item wants,
     * so the wrap became a value supplied there.
     *
     * <p>Two things follow from letting vanilla resolve it rather than doing it here. The bounce now
     * gets the rest of vanilla's treatment -- gravity compensation, air drag, the {@code BOUNCE} game
     * event and a position sync -- which the old two-line reflection of the velocity did not. And it
     * now applies to horizontal collisions too, not just the fall: an item with a bounciness will
     * come off a wall as well as off the floor. Both read as what a bouncy item should do, but
     * neither is what 1.21.1 did.
     */
    @Inject(method = "getEntityBounciness", at = @At("HEAD"), cancellable = true)
    private void simulated$bouncyItemVelocity(final CallbackInfoReturnable<Double> cir) {
        if (!(((Entity) (Object) this) instanceof final ItemEntity item)) {
            return;
        }

        final Float bounce = item.getItem().get(SimDataComponents.BOUNCINESS);
        if (bounce != null && bounce > 0) {
            cir.setReturnValue((double) bounce);
        }
    }

    @Inject(method = "checkFallDamage", at = @At(value = "HEAD"))
    private void simulated$bouncyItemAdvancement(final double d, final boolean onGround, final BlockState blockState, final BlockPos blockPos,
                                                 final CallbackInfo ci) {
        if (onGround && ((Entity)(Object)this) instanceof final ItemEntity item) {
            final Float bounce = item.getItem().get(SimDataComponents.BOUNCINESS);
            if (bounce != null && bounce > 0) {
                if (item.fallDistance >= 128 && item.getOwner() instanceof final Player player) {
                    SimAdvancements.MUST_COME_UP.awardTo(player);
                }
            }
        }
    }
}
