package dev.ryanhcode.offroad.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import dev.ryanhcode.offroad.content.contraptions.borehead_contraption.BoreheadBearingContraption;
import dev.ryanhcode.offroad.neoforge.mixin_helpers.WrappedWrappedMountedItemStorage;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.ref.WeakReference;

@Mixin(MountedItemStorage.class)
public class MountedItemStorageMixin {

    /**
     * 26.2: {@code getHandlerForMenu} hands back a {@code ResourceHandler<ItemResource>}. A
     * {@code @WrapMethod} signature is checked when the mixin is applied, not when it compiles, so
     * this one loaded clean and failed at startup.
     */
    @WrapMethod(method = "getHandlerForMenu")
    public ResourceHandler<ItemResource> offroad$wrapHandler(final StructureTemplate.StructureBlockInfo info, final Contraption contraption, final Operation<ResourceHandler<ItemResource>> original) {
        final ResourceHandler<ItemResource> originalCall = original.call(info, contraption);
        if (contraption instanceof BoreheadBearingContraption && originalCall != null) {
            return new WrappedWrappedMountedItemStorage(new WeakReference<>(contraption), originalCall);
        }

        return originalCall;
    }

}
