package dev.simulated_team.simulated.mixin.physics_staff;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.simulated_team.simulated.index.SimItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin {

    @Shadow public abstract int guiWidth();

    @Shadow public abstract void fill(int minX, int minY, int maxX, int maxY, int color);

    @Shadow public abstract void enableScissor(int x0, int y0, int x1, int y1);

    @Shadow public abstract void disableScissor();

    // 26.2: GuiGraphics draws an item through item(), and the seed/offset pair became one seed.
    @WrapMethod(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;III)V")
    private void simulated$renderPhysicsStaff(final LivingEntity entity,
                                              final ItemStack stack,
                                              final int x,
                                              final int y,
                                              final int seed,
                                              final Operation<Void> original) {
        final boolean isStaff = stack.is(SimItems.PHYSICS_STAFF);

        // 26.2 port: this used to work the scissor rectangle out in window pixels, transforming the
        // slot's corners by the pose and scaling by the GUI scale. GuiGraphics scissors in GUI
        // coordinates and applies the pose itself, so the maths the old code did is now the thing
        // being asked for.
        if (isStaff) {
            this.enableScissor(x, y, x + 16, y + 16);
        }

        original.call(entity, stack, x, y, seed);

        if (isStaff) {
            this.disableScissor();
        }
    }

}
