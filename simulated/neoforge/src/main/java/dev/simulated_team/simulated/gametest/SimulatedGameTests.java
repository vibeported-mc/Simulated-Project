package dev.simulated_team.simulated.gametest;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.infrastructure.gametest.CreateTestFunction;

import dev.simulated_team.simulated.Simulated;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Puts Simulated's game tests into the game.
 *
 * <h2>26.2 note</h2>
 * <p>Tests used to be found by {@code @GameTestHolder} on the class and {@code @GameTest} on the
 * method, both of which the game provided. A test is a registry object now -- built in code or read
 * from a data file -- and the annotations went with the old way of finding them.
 *
 * <p>Create rewrote the finding rather than the tests, and its {@code CreateTestFunction} takes any
 * class, so Simulated's tests are registered through it: the annotations are Create's, the test
 * bodies are unchanged, and only this class is new.
 */
@EventBusSubscriber
public class SimulatedGameTests {

    private static final Class<?>[] TEST_HOLDERS = { ExtraKineticsTest.class };

    /**
     * Every test instance must name a kind of test it is, and the kinds live in a registry of their
     * own. Simulated's tests are Create's kind, so nothing is registered here beyond the hook that
     * makes the mod bus aware of the register at all.
     */
    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, Simulated.MOD_ID);

    public static void register(final IEventBus modEventBus) {
        TEST_TYPES.register(modEventBus);
    }

    @SubscribeEvent
    public static void registerTests(final RegisterGameTestsEvent event) {
        // Nothing is asked of the world these tests run in beyond what the structure itself brings.
        final Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(Simulated.path("default"));

        for (final CreateTestFunction.Found test : CreateTestFunction.getTestsFrom(environment, TEST_HOLDERS)) {
            event.registerTest(test.id(), test.instance());
        }
    }
}
