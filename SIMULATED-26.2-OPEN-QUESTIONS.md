# Simulated-Project on 26.2 — parked features and open questions

Things the 26.2 port could not carry across as they were, with the reason and what would
un-park each one. Every entry here is a **decision**, not a bug: the code compiles and runs
without it.

Companions: `sable/SABLE-26.2-OPEN-QUESTIONS.md`, `sable/SUBLEVEL-RENDER-TESTS.md`.

---

## 1. The off-screen sub-level group render — parked

**What it was.** `SimpleSubLevelGroupRenderer.renderGroup` drew a chosen chain of sub-levels into
an `AdvancedFbo` under a chosen camera and projection. Two features were built on it:

| Feature | Call site |
|---|---|
| The contraption **diagram** | `DiagramScreen` → `renderChain` |
| The End Sea's **sky-light shadow map** | `EndSeaShadowRenderer.renderShadowMap` → `renderGroup` |

**Why it cannot be ported as written.** It drove the chunk pass by hand:

```java
for (RenderType layer : RenderType.chunkBufferLayers()) {
    layer.setupRenderState();
    ShaderInstance shader = RenderSystem.getShader();
    shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, window);
    shader.apply();
    ...
}
```

Every one of those is gone in 26.2. `RenderType.chunkBufferLayers()` became the closed
`ChunkSectionLayer` enum, `ShaderInstance` was deleted, and `setupRenderState`/`apply`/`clear` went
with the move to pipeline objects. `RenderSystem.getProjectionMatrix`, `getModelViewStack`,
`applyModelViewMatrix` and `GameRenderer.resetProjectionMatrix` are all gone too.

Underneath, Sable's default `VANILLA` dispatcher no longer draws sub-level terrain itself at all —
it contributes the sections to *vanilla's own* chunk draw list during `prepareChunkRenders`, with the
sub-level's pose folded into each section's model-view uniform. That path is tied to the main frame:
it cannot be pointed at a different framebuffer, camera or subset of sub-levels.

**Decision (2026-09-07): parked.** `renderChain` and `renderGroup` are no-ops.
`getRenderedChain` is untouched — it is pure graph-walking and has no rendering in it.

**The three ways out, for whoever picks this up:**

1. **Veil's perspective renderer.** `VeilLevelPerspectiveRenderer.render(...)` is ported and working
   and does exactly this job — an off-screen level render into an `AdvancedFbo`. It renders the
   *whole level* from that viewpoint, so the diagram would show surrounding terrain behind the
   sub-levels unless a culling hook is added. Least new code; stays on a path Veil maintains.
2. **Sable's `FANCY` dispatcher.** It compiles its own meshes into its own buffers through Veil's
   shader and vertex-array API, so its `renderSectionLayer` is a real, self-contained draw with
   settable uniforms — everything this feature needs, including a home for the flat-lighting uniform
   in §2. Cost: two dispatchers alive at once, and `FANCY` is not the path the rest of the game uses.
3. **Rebuild the hand-rolled pass on 26.2's draw groups.** Most faithful, most work, and it is the
   thing Sable already concluded is not expressible — see its open-questions doc.

---

## 2. Two dispatcher mixins with no target — removed

Both of Simulated's `VanillaSubLevelRenderDispatcherMixin`s hung on

```java
VanillaSubLevelRenderDispatcher.setupDynamicEffects(ShaderInstance, boolean, boolean)
```

which Sable's 26.2 port deleted, recording why: it pushed Veil uniforms onto the vanilla chunk
shader, and that shader's uniforms live in engine-filled buffers now. There is no per-draw hook a mod
can push a uniform through on the default path.

| Mixin | What it did | Status |
|---|---|---|
| `mixin/diagram/…` | Forced flat lighting while `RENDERING_SIMPLE` | removed — the feature it served is parked (§1) |
| `mixin/end_sea/…` | Set `EndSeaCameraY`, tinting sub-level terrain by its depth in the sea | removed — see below |

**Decision (2026-09-07): both stubbed, to revisit.**

The End Sea *itself* is unaffected — `EndSeaRenderer` uses Veil's `ShaderProgram` and `AdvancedFbo`,
which all survive the port. What is lost is only the depth tint applied to **sub-level terrain**
inside the sea. Ordinary terrain still tints.

Un-parking it means giving the uniform somewhere to be filled. Either the `FANCY` route in §1, or
adding it to the shader Sable ships for its own sub-level sections — which couples Simulated to
Sable's shader more tightly than they are today, and is a conversation to have with Sable first.

---

## 3. Smaller things noted in passing

- **The docking connector's unpair-on-turn.** `onRemove` used to see the replacing state, so a
  connector that merely *turned* could unpair. `affectNeighborsAfterRemoval` only runs when the block
  actually changes, so that case moved to `onPlace`, which still gets the old state. Behaviour is
  preserved; the mechanism is not the same one, so it is worth a look in game.
- **The nameplate's neighbour check.** `neighborChanged` carries an `Orientation` — which way a
  redstone update travelled, null otherwise — rather than the position it came from. The nameplate
  had two branches on "was it my clockwise neighbour"; both ended up walking the controller chain, so
  it now always takes the safe one. Worth checking a long nameplate row re-forms correctly.
- **The physics staff's Iris workaround** ended every batch, because Iris would not let one render
  type be ended alone. There is no batching left to end, and no Iris 26.2 build, so it is gone. If
  Iris returns, the translucent parts of the staff are the thing to look at first.
- **Paused sprite animation now holds the sub-frame too.** The old wrap blocked only the frame
  counter, so an interpolating sprite kept sweeping toward the next frame while "paused" — a wobble
  rather than a hold. Holding is what the caller asks for, but it is a visible difference.
