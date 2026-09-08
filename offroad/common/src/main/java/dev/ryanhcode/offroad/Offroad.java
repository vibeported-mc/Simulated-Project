package dev.ryanhcode.offroad;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import dev.simulated_team.simulated.util.SimColors;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.ryanhcode.offroad.data.OffroadLang;
import dev.ryanhcode.offroad.events.OffroadCommonEvents;
import dev.ryanhcode.offroad.index.*;
import dev.ryanhcode.offroad.network.OffroadPacketManager;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import net.createmod.catnip.api.client.lang.FontHelper;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Offroad {
	public static final String MOD_ID = "offroad";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final NonNullSupplier<SimulatedRegistrate> REGISTRATE = NonNullSupplier.lazy(() ->
			(SimulatedRegistrate) new SimulatedRegistrate(Offroad.path(MOD_ID), MOD_ID).defaultCreativeTab((ResourceKey<CreativeModeTab>) null));

	public static void init() {
		setTooltips();
		getRegistrate().addDataGenerator(ProviderType.LANG, OffroadLang::registrateLang);

		OffroadBlocks.init();
		OffroadBlockEntityTypes.init();
		OffroadEntityTypes.init();
		OffroadDataComponents.init();
		OffroadItems.init();
		OffroadSoundEvents.init();
		OffroadPacketManager.init();

		OffroadContraptionTypes.init();

		listenCommonEvents();
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>The rarity is read on first use rather than while the item is being registered -- an item's
	 * default components are bound after registration, so asking then throws "Components not bound
	 * yet" and takes the registration with it. See {@code Simulated.setTooltips}, which is the same
	 * factory and needed the same change.
	 */
	public static void setTooltips() {
		getRegistrate().setTooltipModifierFactory(item -> new TooltipModifier() {

			@Nullable
			private TooltipModifier resolved;

			@Override
			public void modify(final ItemTooltipEvent context) {
				if (this.resolved == null) {
					final Rarity rarity = item.getDefaultInstance().getRarity();
					FontHelper.Palette color = FontHelper.Palette.STANDARD_CREATE;
					if (rarity == Rarity.EPIC)
						color = new FontHelper.Palette(TooltipHelper.styleFromColor(SimColors.EPIC_OURPLE), TooltipHelper.styleFromColor(rarity.color()));

					this.resolved = new ItemDescription
							.Modifier(item, color)
							.andThen(TooltipModifier.mapNull(KineticStats.create(item)));
				}

				this.resolved.modify(context);
			}
		});
	}

	private static void listenCommonEvents() {
		SableEventPlatform.INSTANCE.onPhysicsTick(OffroadCommonEvents::physicsTick);
	}

	public static SimulatedRegistrate getRegistrate() {
		return REGISTRATE.get();
	}

	public static Identifier path(final String path) {
		return Identifier.tryBuild(MOD_ID, path);
	}
}
