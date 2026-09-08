package dev.eriksonn.aeronautics.neoforge;


import dev.eriksonn.aeronautics.Aeronautics;
import dev.eriksonn.aeronautics.neoforge.events.AeroNeoForgeCommonEvents;
import dev.eriksonn.aeronautics.neoforge.index.AeroFluidsNeoForge;
import dev.eriksonn.aeronautics.neoforge.index.AeroParticleTypesNeoForge;
import dev.eriksonn.aeronautics.neoforge.service.NeoForgeAeroConfigService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Aeronautics.MOD_ID)
public class AeronauticsNeoForge {
    public AeronauticsNeoForge(final IEventBus modBus, final ModContainer modContainer) {
        // 26.2 port: these classes carry @EventBusSubscriber, and that annotation now covers both buses
        // -- EventBusSubscriber.Bus is gone, and an event reaches the mod bus by implementing
        // IModBusEvent. In 1.21.1 an unqualified @EventBusSubscriber meant the game bus only, so the
        // explicit registration below was what put the mod-bus handlers on. Keeping both now delivers
        // every mod-bus event twice, which showed up as a duplicate trigger-type registration.

        AeroParticleTypesNeoForge.registerEventListeners(modBus);
        Aeronautics.getRegistrate().registerEventListeners(modBus);

        Aeronautics.init();
        AeroFluidsNeoForge.init();

        NeoForgeAeroConfigService.register(modContainer);
    }
}
