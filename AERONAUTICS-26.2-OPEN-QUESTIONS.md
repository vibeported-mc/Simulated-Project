# Aeronautics: what the 26.2 port left open

Everything here compiles. These are the places where 26.2 removed the mechanism a feature was built
on, and where the replacement is a judgement call that wants the real blocks in front of it rather
than a decision made on paper.

---

## 1. Levitite is no longer a chunk layer, and its tessellation has no home on a RenderPipeline

**What it did.** `AeronauticsClient.registerEvents()` registered `levitite()` and `levititeGhosts()`
two ways: as **chunk block layers**, so levitite blocks in the world were compiled into chunk
geometry drawn with the levitite program, and as **fixed buffers** at `AFTER_BLOCK_ENTITIES` /
`AFTER_WEATHER`. The program is tessellated — `levitite.tcsh` and `levitite.tesh` are real stages —
and drew with `GL_PATCHES` through Veil's `PatchesLayer`. `LevititeShaderManager` fed it the
sub-level's smoothed linear and angular velocity, its orientation, and the local gravity vector, so
the blocks visibly deform as the ship they are on accelerates and turns.

**What 26.2 removed.** Three things, independently:

- **Chunk layers are a closed enum.** `ChunkSectionLayer` has six constants and chunk geometry is
  collected into vanilla's own draw list keyed by them. `net.neoforged.neoforge.client.ChunkRenderTypeSet`
  is gone with it, so there is nothing to append a render type to. `ChunkRenderTypeSetAccessor` is
  parked.
- **Nothing binds a shader per layer.** `LevelRenderer.renderSectionLayer` takes a
  `ChunkSectionLayer` and no `ShaderInstance`; `VanillaChunkedSubLevelRenderData.renderChunkedSubLevel`
  was replaced during Sable's port by `collectDraws`, which contributes sections to vanilla's draw
  list rather than issuing draws. Both mixins that pushed levitite's uniforms are parked.
- **`PrimitiveTopology` has no patches constant**, and a `RenderPipeline` names its topology from
  that enum. Veil's `PatchesLayer` is parked for the same reason.

**What still works.** Both render types are rebuilt on Veil's builder with the levitite vertex and
fragment shaders, translucent blending and the block atlas, and both are still registered as fixed
buffers — that path survived intact. `LevititeShaderManager` is ported onto Veil's `ShaderProgram`
and still computes every uniform; only its callers are parked.

**The lead.** `VertexArray.draw()` in the ported Veil already issues `GL_PATCHES` when the bound
program has tessellation stages:

```java
if (shader != null && shader.hasTesselation() && shader.getProgram() == glGetInteger(GL_CURRENT_PROGRAM)) {
    if (this.drawMode == PrimitiveTopology.QUADS) {
        glDrawArrays(GL_PATCHES, 0, this.indexCount * 4 / 6);
```

So patch topology *is* reachable — through Veil's own draw path, not through vanilla's. That points
at rebuilding the levitite pass as geometry Veil draws (the fixed-buffer route, which is already
registered and already survives) rather than as a chunk layer vanilla draws.

**The question to settle in-game.** Do the fixed buffers alone put levitite on screen, and does the
levitite program link and run under them? If it does, the chunk-layer registration is dead weight and
should go, and the uniforms the two parked mixins used to push need a new home on the fixed-buffer
path. If it does not, the fallback is to give levitite an ordinary `ChunkSectionLayer` and lose the
deformation.

Bear in mind the program declares its tessellation stages unconditionally in `levitite.json`, while
the GLSL is guarded by a `TESSELLATION_SHADER` define — so "does it link without tessellation" is a
separate question from "does it draw", and the answer may require conditioning the JSON.

---

## 2. The enabled/disabled shader swap has nowhere to live

`LevititeShaderState` chose, **per frame**, between Veil's levitite program and a depth-only vanilla
state (solid shader, colour mask off, depth mask off). The point was that a machine without
tessellation support drew levitite geometry invisibly on this path and got its visible pass
elsewhere.

A 26.2 `RenderPipeline` is immutable and a shader is two identifiers baked into it, so nothing can be
swapped inside a render type. `LevititeShaderManager.isEnabled()` — which is still
`VeilRenderSystem.tessellationSupported() && enabled` — has to be asked at the call sites instead.
Right now nothing asks it, because the call sites that would are the two parked mixins. Settling §1
settles this too.

---

## 3. The armour material's durability factor is a guess

26.2's `ArmorMaterial` is a plain record that leads with a durability factor, from which
`ArmorType.HELMET.getDurability(n)` derives the item's durability. The 1.21.1 material had no such
field — the item carried its own durability. The value in `AeroArmorMaterials` is a stand-in chosen
to be in the right range for a leather-tier helmet; it has not been checked against what the goggles
actually had. Worth reading off the old item and correcting.

---

## 4. Smaller behavioural notes

- **The gust particle's back face.** It drew eight vertices by hand in two winding orders to be
  double-sided. A render state adds whole quads and the particle pipeline culls back faces, so the
  second face is now the same quad given a half-turn about Y. That is what a real double-sided quad
  looks like from behind, but it does mirror the sprite — if the gust texture is not symmetric,
  compare against 1.21.1.
- **The heat overlay's samplers moved into data.** `RenderSystem.setShaderTexture` is gone, so
  `hot_air_overlay.json` now declares `Sampler0` and `Sampler1`. Veil binds a program's textures at
  bind time rather than per draw, which is the same for these two constants but would not be for a
  texture that changed per frame.
- **`ColorModulator` is set by hand.** `setShaderColor` drove a vanilla uniform that no longer
  exists. The program declares its own, and the two places that used to call `setShaderColor` set it
  by name — including the reset to white at the end, which now only matters for this program rather
  than globally.
