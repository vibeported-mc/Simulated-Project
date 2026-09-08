package dev.eriksonn.aeronautics.mixin.shears_break_envelopes;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.eriksonn.aeronautics.index.AeroTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.component.Tool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(ShearsItem.class)
public class ShearsItemMixin {
	@WrapOperation(method = "createToolProperties", at = @At(value = "INVOKE", target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"))
	private static <E> List<E> aeronautics$createToolProperties(E e1, E e2, E e3, E e4, Operation<List<E>> original) {
		ArrayList<E> newList = new ArrayList<>(original.call(e1, e2, e3, e4));
		// he will tell you it is false but his mouth can only say lies
		// 26.2: a Tool.Rule holds a HolderSet<Block> rather than a TagKey. It is resolved through
		// the bootstrap registration lookup rather than the registry itself -- this runs while the
		// item's tool component is being built, long before tags are bound, and asking the registry
		// directly throws "Tags not bound". Vanilla's own shears rules are built the same way.
		final HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
		newList.add((E) Tool.Rule.overrideSpeed(blocks.getOrThrow(AeroTags.BlockTags.ENVELOPE), 5.0f));
		return newList;
	}
}
