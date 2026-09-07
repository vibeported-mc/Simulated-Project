package dev.simulated_team.simulated.index.client;

import com.simibubi.create.foundation.item.render.CustomRenderedItems;

import dev.simulated_team.simulated.content.items.plunger_launcher.PlungerLauncherItemRenderer;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer;
import dev.simulated_team.simulated.index.SimItems;

/**
 * The items Simulated draws with a renderer of its own.
 *
 * <h2>26.2 note</h2>
 * <p>Each item used to name its renderer from its own class, through NeoForge's
 * {@code IClientItemExtensions} -- which is why both were declared by a mixin onto the item rather
 * than in the item itself, the item being common code and the extension being loader-specific. 26.2
 * removed that hook, so the renderers are listed here and registered during client setup, and the
 * two mixins go away entirely.
 *
 * <p>This has to run before models are baked: Create's {@code ModelSwapper} wraps each of these
 * items' baked models so the renderer is reached through the item's render state.
 */
public class SimCustomItemRenderers {

    public static void register() {
        CustomRenderedItems.register(SimItems.PHYSICS_STAFF.get(), PhysicsStaffItemRenderer::new);
        CustomRenderedItems.register(SimItems.PLUNGER_LAUNCHER.get(), PlungerLauncherItemRenderer::new);
    }

    private SimCustomItemRenderers() {
    }
}
