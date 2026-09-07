package dev.simulated_team.simulated.content.blocks.redstone.directional_receiver;

import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * <h2>26.2 note</h2>
 * <p>This only ever delegated to {@code super.renderSafe}, so with the split into extract and submit
 * there is nothing left to override -- the base class does both halves. Kept as its own renderer
 * because the block entity type is registered against it.
 */
public class DirectionalLinkedReceiverRenderer
        extends SmartBlockEntityRenderer<DirectionalLinkedReceiverBlockEntity, SmartBlockEntityRenderer.SmartRenderState> {

    public DirectionalLinkedReceiverRenderer(final BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
