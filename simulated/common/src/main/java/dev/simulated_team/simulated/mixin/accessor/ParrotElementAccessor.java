package dev.simulated_team.simulated.mixin.accessor;

import net.createmod.ponder.impl.client.element.ParrotElementImpl;
import net.minecraft.world.entity.animal.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.animal.parrot.Parrot;

@Mixin(ParrotElementImpl.class)
public interface ParrotElementAccessor {

    @Accessor
    Parrot getEntity();
}
