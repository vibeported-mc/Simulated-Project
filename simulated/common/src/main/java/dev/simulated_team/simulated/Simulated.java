package dev.simulated_team.simulated;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.events.SimulatedCommonEvents;
import dev.simulated_team.simulated.index.*;
import dev.simulated_team.simulated.network.SimPacketManager;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import dev.simulated_team.simulated.service.SimModCompatibilityService;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import dev.simulated_team.simulated.util.SimColors;
import net.createmod.catnip.api.client.lang.FontHelper;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Rarity;
import org.slf4j.Logger;

public final class Simulated {
    public static final String MOD_ID = "simulated";
    public static final String MOD_NAME = "Create Simulated";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final NonNullSupplier<SimulatedRegistrate> REGISTRATE = NonNullSupplier.lazy(() ->
            (SimulatedRegistrate) new SimulatedRegistrate(path("simulated"), MOD_ID).defaultCreativeTab((ResourceKey)null));

    public static void init() {
        setTooltips();
        SimEntityDataSerializers.register();
        getRegistrate().addDataGenerator(ProviderType.LANG, SimLang::registrateLang);

        SimRegistries.register();
        SimTags.register();
        SimBlocks.register();
        SimItems.register();
        SimBlockEntityTypes.register();
        SimParticleTypes.register();
        SimSoundEvents.init();
        SimSpriteShifts.init();
        SimPacketManager.init();
        SimEntityTypes.register();
        SimMenuTypes.register();
        SimNavigationTargets.register();
        SimDataComponents.register();
        SimItemAttributeTypes.init();

        SimulatedCommonEvents.register();
        SimBlockMovementChecks.register();
        SimAssemblyHelper.register();
        SimModCompatibilityService.initLoaded();

        SableEventPlatform.INSTANCE.onPhysicsTick(SimulatedCommonEvents::onPhysicsTick);
        SableEventPlatform.INSTANCE.onPostPhysicsTick(SimulatedCommonEvents::onPostPhysicsTick);
    }

    /**
     * <h2>26.2 note</h2>
     * <p>This asked each item for its rarity while the item was being registered, to pick a palette.
     * An item's default components -- rarity among them -- are bound after registration in 26.2, so
     * {@code getDefaultInstance} at that moment throws "Components not bound yet" and takes the whole
     * registration with it.
     *
     * <p>The rarity is read on first use instead. {@code TooltipModifier} is a functional interface,
     * so the factory can hand back one that builds the real modifier the first time a tooltip is
     * asked for -- by which point the components exist. It is memoised because the factory's result
     * is stored per item and reused for every tooltip after that.
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


    public static SimulatedRegistrate getRegistrate() {
        return REGISTRATE.get();
    }

    public static Identifier path(final String path) {
        return Identifier.tryBuild(MOD_ID, path);
    }

}
