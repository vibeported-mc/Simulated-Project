package dev.simulated_team.simulated.neoforge;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.neoforge.NeoForgeSimStats;
import dev.simulated_team.simulated.index.neoforge.SimNeoForgeRecipeTypes;
import dev.simulated_team.simulated.index.neoforge.SimParticleTypesImpl;
import dev.simulated_team.simulated.neoforge.events.SimNeoForgeCommonEvents;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimConfigService;
import dev.simulated_team.simulated.neoforge.service.NeoForgeSimEntityDataSerialization;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Simulated.MOD_ID)
public final class SimulatedNeoForge {
    public static final CreativeModeTab TAB = CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + Simulated.MOD_ID + ".group"))
            .icon(() -> new ItemStack(SimBlocks.PHYSICS_ASSEMBLER.get()))
            .build();

    public SimulatedNeoForge(final IEventBus modEventBus, final ModContainer modContainer) {
        // deferred register tab
        final DeferredRegister<CreativeModeTab> tabRegister = DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB, Simulated.MOD_ID);
        tabRegister.register("main_tab", () -> TAB);
        tabRegister.register(modEventBus);

        // 26.2 port: these classes carry @EventBusSubscriber, and that annotation now covers both buses
        // -- EventBusSubscriber.Bus is gone, and an event reaches the mod bus by implementing
        // IModBusEvent. In 1.21.1 an unqualified @EventBusSubscriber meant the game bus only, so the
        // explicit registration below was what put the mod-bus handlers on. Keeping both now delivers
        // every mod-bus event twice, which showed up as a duplicate trigger-type registration.

        SimParticleTypesImpl.register(modEventBus);
        SimNeoForgeRecipeTypes.register(modEventBus);

        NeoForgeSimEntityDataSerialization.register(modEventBus);
        Simulated.getRegistrate().registerEventListeners(modEventBus);

        NeoForgeSimStats.register(modEventBus);

        // 26.2 port: ComputerCraft has no 26.2 build, so its peripheral service is excluded from
        // this module's sources in build.gradle -- the registration that named it goes with it.

        Simulated.init();
        NeoForgeSimConfigService.register(ModLoadingContext.get(), modContainer);
    }
}
