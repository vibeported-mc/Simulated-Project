# Mixins disabled for the 26.2 port

Each of these targets a method or a class that **no longer exists**. A mixin with no target is a
hard startup failure, so they are parked here (`.java.disabled`, removed from `aeronautics.mixins.json`)
rather than left to break the game.

All three are the same feature — drawing levitite as a **chunk layer** with its own shader — and all
three fail for the same underlying reason: in 26.2 chunk geometry is collected into vanilla's own
draw list, keyed by `ChunkSectionLayer`, a closed enum. A `RenderType` cannot be a chunk layer at
all, and nothing binds a shader per layer any more.

| Disabled mixin | Used to target | What it did | Why it has no target |
|---|---|---|---|
| `ChunkRenderTypeSetAccessor` | `net.neoforged.neoforge.client.ChunkRenderTypeSet` | widened `CHUNK_RENDER_TYPES_LIST` / `CHUNK_RENDER_TYPES` so the two levitite types could be appended to the set of chunk layers | the class is gone. NeoForge no longer has an extensible chunk-render-type set, because `ChunkSectionLayer` is a closed enum |
| `LevelRendererMixin` | `LevelRenderer.renderSectionLayer(RenderType, …)`, at `ShaderInstance#apply` | set the levitite program's `time` uniform and called `LevititeShaderManager.prepareShaderForWorld`, and skipped the ghost layer | `renderSectionLayer` takes a `ChunkSectionLayer` now and binds no `ShaderInstance` — the method it hooked, the parameter it matched on and the local it captured are all gone |
| `VanillaChunkedSubLevelRenderDataMixin` | `VanillaChunkedSubLevelRenderData.renderChunkedSubLevel(RenderType, ShaderInstance, …)` | set the per-sub-level levitite uniforms, and drew the ghost layer twice with `layerIndex` ±1 and depth test off | Sable's port replaced that method with `collectDraws(ChunkSectionLayer, …)`, which contributes sections to vanilla's draw list rather than issuing draws itself. There is no shader to prepare and no place to draw the same layer twice |

## What still works

`AeroRenderTypes.levitite()` and `levititeGhosts()` are rebuilt on Veil's render-type builder and are
still registered as **fixed buffers** at `AFTER_BLOCK_ENTITIES` and `AFTER_WEATHER`, which is a path
that survived intact. `LevititeShaderManager` is ported onto Veil's `ShaderProgram` and still
computes every uniform it used to; only the callers that pushed those uniforms are parked.

## The lead worth following

Veil's `VertexArray.draw()` already issues `GL_PATCHES` when the bound program has tessellation
stages. So levitite's patch topology — the thing 26.2's `PrimitiveTopology` has no constant for — is
reachable through Veil's own draw path. Rebuilding the levitite pass as geometry Veil draws, rather
than as a chunk layer vanilla draws, is the route that keeps the tessellation. That is recorded in
`AERONAUTICS-26.2-OPEN-QUESTIONS.md`.
