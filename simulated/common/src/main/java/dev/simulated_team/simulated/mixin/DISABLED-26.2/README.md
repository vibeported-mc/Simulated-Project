# Mixins disabled for the 26.2 port

| Disabled mixin | Used to target | What it did | Why it has no target |
|---|---|---|---|
| `LightTextureMixin` | `net.minecraft.client.renderer.LightTexture` | shadowed the lightmap's pixels, its dynamic texture and the `updateLightTexture` flag, so the diagram could rewrite the whole lightmap flat while `RENDERING_SIMPLE` was set | `LightTexture` does not exist in 26.2. Its successor, `Lightmap`, holds a `GpuTexture` and a uniform buffer and is filled from a `LightmapRenderState` rather than from a `NativeImage` the mod can write into. None of the six shadowed members has an equivalent |

Nothing called `simulated$makeDiagramLightTexture` any more in any case: the diagram's off-screen
render is itself parked. See §1 of `SIMULATED-26.2-OPEN-QUESTIONS.md` — if that comes back, the flat
lighting has to be rebuilt against `LightmapRenderState`, not against a texture.
