package dev.eriksonn.aeronautics.data;

import java.util.function.BiConsumer;

import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.index.AeroArmorMaterials;
import net.minecraft.client.data.models.EquipmentAssetProvider;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;

/**
 * The layers the aviator's goggles draw.
 *
 * <h2>26.2 note</h2>
 * <p>26.2 reads these out of {@code assets/aeronautics/equipment/&lt;name&gt;.json} rather than
 * taking a texture identifier on the armour item's constructor, which is what the old
 * {@code ArmorMaterial.Layer} list and the {@code TEXTURE} constant were for.
 */
public class AeroEquipmentAssets extends EquipmentAssetProvider {

	public AeroEquipmentAssets(final PackOutput output) {
		super(output);
	}

	@Override
	protected void registerModels(final BiConsumer<ResourceKey<EquipmentAsset>, EquipmentClientInfo> output) {
		output.accept(AeroArmorMaterials.AVIATORS_GOGGLES_ASSET, EquipmentClientInfo.builder()
				.addLayers(EquipmentClientInfo.LayerType.HUMANOID,
						new EquipmentClientInfo.Layer(Aeronautics.path("aviators_goggles")))
				.build());
	}

	@Override
	public String getName() {
		return "Aeronautics' Equipment Assets";
	}
}
