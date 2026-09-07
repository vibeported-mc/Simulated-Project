package dev.eriksonn.aeronautics.mixin.ponder;

import net.createmod.ponder.impl.client.element.WorldSectionElementImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldSectionElementImpl.class)
public interface WorldSectionElementImplAccessor {
    @Accessor
    Vec3 getCenterOfRotation();
}
