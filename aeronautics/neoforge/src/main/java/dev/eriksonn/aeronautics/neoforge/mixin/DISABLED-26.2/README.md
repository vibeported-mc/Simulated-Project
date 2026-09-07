# Mixins disabled for the 26.2 port

| Disabled mixin | Used to target | What it did | Why it has no target |
|---|---|---|---|
| `BlockRenderDispatcherMixin` | `net.minecraft.client.renderer.block.BlockRenderDispatcher#renderBreakingTexture` | nudged the breaking overlay outward by a ten-thousandth on levitite blocks, so the crack texture did not z-fight with the levitite shader's displaced surface | `BlockRenderDispatcher` does not exist in 26.2 — block rendering was restructured around render states and draw collection, and there is no single dispatcher method that draws one block's breaking texture |

The overlay this fixed is drawn from a `ModelFeatureRenderer.CrumblingOverlay` carried on the block's
render state now. Whether the z-fight even reappears depends on the levitite pass, which is itself
parked — see `AERONAUTICS-26.2-OPEN-QUESTIONS.md`. Look at this again once levitite draws.
