package dev.simulated_team.simulated.neoforge.mixin.self_mixins;

import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlungerLauncherItem.class)
public abstract class PlungerLauncherItemMixin extends Item {
    public PlungerLauncherItemMixin(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean onEntitySwing(final ItemStack stack, final LivingEntity entity, final InteractionHand hand) {
        return true;
    }


    // 26.2: initializeClient and IClientItemExtensions are gone. The renderer this declared is
    // registered in SimCustomItemRenderers instead, which is also why the physics staff's mixin --
    // which had nothing else in it -- no longer exists.
}
