package dev.simulated_team.simulated.content.blocks.nav_table.navigation_target;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableBlockEntity;
import dev.simulated_team.simulated.content.blocks.nav_table.NavTableRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What an item draws when it sits on a navigation table.
 *
 * <h2>26.2 note</h2>
 * <p>{@code renderInNavTable} split in two for the same reason every block entity renderer did: the
 * block entity and the level are readable during extraction and gone by submission. An implementor
 * resolves whatever it wants to draw in {@link #extractInNavTable} -- onto the table's own render
 * state, which is where the geometry has to live -- and queues it in {@link #submitInNavTable}.
 *
 * <p>The default is what {@code ItemRenderer.renderStatic} used to do: resolve the stack's model and
 * submit it. That call is gone with {@code MultiBufferSource}.
 */
public interface RenderableNavigationTarget extends NavigationTarget {

    /**
     * Resolves what to draw, while the block entity and its level are still readable.
     *
     * <p>Partial models must be centered at their origin: they are drawn centered on the
     * {@link NavTableRenderer Navigation Table's} item pedestal.
     */
    default void extractInNavTable(final ItemStack self, final NavTableBlockEntity navBE, final BlockState navState,
                                   final float partialTicks, final ItemModelResolver resolver,
                                   final NavTableRenderer.NavTableRenderState state) {
        final ItemStackRenderState item = new ItemStackRenderState();
        resolver.updateForTopItem(item, self, ItemDisplayContext.FIXED, navBE.getLevel(), null, 0);
        state.heldItem = item;
    }

    /**
     * Queues what {@link #extractInNavTable} resolved. The pose is already centered on the pedestal.
     */
    default void submitInNavTable(final NavTableRenderer.NavTableRenderState state, final PoseStack ms,
                                  final SubmitNodeCollector queue) {
        if (state.heldItem != null) {
            state.heldItem.submit(ms, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
