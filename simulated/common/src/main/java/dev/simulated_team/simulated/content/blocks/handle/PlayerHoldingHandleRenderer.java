package dev.simulated_team.simulated.content.blocks.handle;

import dev.simulated_team.simulated.Simulated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.context.ContextKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Poses a player who is hanging from a handle.
 *
 * <h2>26.2 note</h2>
 * <p>{@code setupAnim} is handed a render state rather than the entity, and a render state carries
 * no player -- not even a UUID. So the answer to "is this player holding a handle?" has to be
 * decided during extraction, while the entity is still in hand, and travel to the model as render
 * data. NeoForge has a place for exactly that: an {@code AvatarRenderStateModifier} runs inside the
 * avatar's extraction and every render state can carry arbitrary data under a {@link ContextKey}.
 *
 * <p>The flag is written on every extraction rather than only when it is true, because render
 * states are reused between frames -- leaving a stale true behind would keep a player hanging after
 * they let go.
 */
public class PlayerHoldingHandleRenderer {

    /** Set during extraction, read in {@code setupAnim}. */
    public static final ContextKey<Boolean> HOLDING_HANDLE = new ContextKey<>(Simulated.path("holding_handle"));

    private static final Set<UUID> holdingPlayers = new HashSet<>();

    public static void updatePlayerList(final Collection<UUID> uuids) {
        holdingPlayers.clear();
        holdingPlayers.addAll(uuids);
    }

    /**
     * Records whether the avatar being extracted is holding a handle.
     *
     * @param uuid  the avatar's UUID, which the render state itself does not keep
     * @param state the state being filled in
     */
    public static void extractRenderState(final UUID uuid, final HumanoidRenderState state) {
        state.setRenderData(HOLDING_HANDLE, holdingPlayers.contains(uuid));
    }

    public static void afterSetupAnim(final HumanoidRenderState state, final HumanoidModel<?> model) {
        if (Boolean.TRUE.equals(state.getRenderData(HOLDING_HANDLE)))
            setHangingPose(model);
    }

    private static void setHangingPose(final HumanoidModel<?> model) {
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        model.leftArm.zRot = 0.0f;
        model.leftArm.zRot = 0.0f;

        model.leftArm.xRot = (float) Math.toRadians(-80.0f)+ model.head.xRot;
        model.rightArm.xRot = (float) Math.toRadians(-80.0f)+ model.head.xRot;

        model.rightArm.yRot = (float) Math.toRadians(-15);
        model.leftArm.yRot = (float) Math.toRadians(15);
    }
}
