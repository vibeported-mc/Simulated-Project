package dev.simulated_team.simulated.ponder.instructions;

import dev.simulated_team.simulated.mixin_interface.ponder.PonderSceneExtension;
import net.createmod.ponder.api.client.scene.PonderScene;
import net.createmod.ponder.api.client.instruction.PonderInstruction;

public class CustomToggleBaseShadowInstruction extends PonderInstruction {
    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(final PonderScene scene) {
        ((PonderSceneExtension)scene).simulated$toggleRenderBasePlateShadow();
    }
}
