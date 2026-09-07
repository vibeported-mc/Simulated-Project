package dev.eriksonn.aeronautics.content.items;

import com.simibubi.create.content.equipment.armor.BaseArmorItem;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import dev.eriksonn.aeronautics.index.AeroArmorMaterials;
import dev.eriksonn.aeronautics.index.AeroItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.ArmorType;

/**
 * <h2>26.2 note</h2>
 * <p>There is no {@code ArmorItem} and no {@code ArmorItem.Type}: a piece of armour is a plain item
 * whose properties carry the material and slot, and whose look comes from the material's equipment
 * asset rather than from a texture handed to the constructor.
 */
public class AviatorsGogglesItem extends BaseArmorItem {

	public static final ArmorType TYPE = ArmorType.HELMET;

	public AviatorsGogglesItem(final Properties properties) {
		super(AeroArmorMaterials.AVIATORS_GOGGLES, TYPE, properties);
		GogglesItem.addIsWearingPredicate(player -> AeroItems.AVIATORS_GOGGLES.isIn(player.getItemBySlot(EquipmentSlot.HEAD)));
	}
}
