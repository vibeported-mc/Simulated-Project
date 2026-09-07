package dev.eriksonn.aeronautics.index;

import java.util.Map;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * <h2>26.2 note</h2>
 * <p>{@code ArmorMaterial} is a plain record rather than a registry entry, so there is no
 * {@code RegistrationProvider}, no {@code RegistryObject} and nothing left to register -- and
 * {@code init()} is kept only so the call site does not have to change shape.
 *
 * <p>Three of the old fields moved. The repair ingredient is a {@code TagKey<Item>} rather than a
 * supplied {@code Ingredient}. The layer list is gone: a material names an {@link EquipmentAsset},
 * and the layers live in {@code assets/aeronautics/equipment/aviators_goggles.json}, written by
 * {@code AeroEquipmentAssets}. And the record leads with a durability factor, which the goggles
 * never had one of -- the item set its own durability, so the factor here is the one that
 * reproduces it through {@code ArmorType.HELMET.getDurability}.
 */
public class AeroArmorMaterials {

	public static final ResourceKey<EquipmentAsset> AVIATORS_GOGGLES_ASSET =
			ResourceKey.create(EquipmentAssets.ROOT_ID, Aeronautics.path("aviators_goggles"));

	public static final ArmorMaterial AVIATORS_GOGGLES = new ArmorMaterial(
			5,
			Map.of(ArmorType.HELMET, 1),
			15,
			SoundEvents.ARMOR_EQUIP_LEATHER,
			0.0f,
			0.0f,
			AeroTags.ItemTags.LEATHERS,
			AVIATORS_GOGGLES_ASSET
	);

	public static void init() {}
}
