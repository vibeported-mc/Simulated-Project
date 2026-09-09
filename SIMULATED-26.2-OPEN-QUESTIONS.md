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

## 3. The End Sea preset can no longer pre-complete the dragon fight — parked

The preset used to make the End reachable without killing the dragon, by writing
`PrimaryLevelData`'s `EndDragonFight.Data` at world creation:

```java
((PrimaryLevelDataExtension) worldData).setEndDragonFight(
        new EndDragonFight.Data(false, true, true, false, ...));
```

That record is gone. The fight became an `EnderDragonFight extends SavedData` with its own
`SavedDataType`, and it is no longer a field on the level data at all — so there is nothing for the
mixin to set.

**Decision (2026-09-07): parked.** The rest of both world-preset mixins is intact: game rules are
still applied, and the preset id is still stored and read back. Only the dragon-fight line is gone.

Un-parking it means creating that saved data for the End dimension when the world is made, which is
a server-side hook at a different point in world creation than the client-side screen mixin this
lived in.

---

## 3.5 Two crashes the e2e block census found, both fixed

Written down because both were dedicated-server-only, both were silent on a single-player client, and
both are shapes that will recur elsewhere in the port.

**`AllIcons` is no longer loadable on a dedicated server.** On 26.2 `AllIcons` nests a
`SubmitNodeCollector.CustomGeometryRenderer` (`AllIcons.java:203`), so the class cannot be linked
where the client classes are absent. `SwivelBearingBlockEntity.LockingSetting` and
`PropellerBearingBlockEntity.ThrustDirection` both named `AllIcons` constants in their enum
constants, which put that load in the enum's `<clinit>` -- and the enum is first touched by the block
entity's constructor, on the server. Placing a swivel bearing, a propeller bearing or a gyroscopic
propeller bearing on a dedicated server threw `NoClassDefFoundError`.

Both now resolve the icon lazily in `getIcon()`, which is what Create's own icon enums do; see
`RollerBlockEntity.RollingMode#getIcon`. **Any other enum in this family that holds an `AllIcons` in a
field is the same bug** -- a grep for `implements INamedIconOptions` is the way to find them, and the
two above were the only ones at the time of writing.

**`ItemAccess.forStack` now rejects the empty stack.** NeoForge 26.2 throws
`IllegalArgumentException("Expected stack to be non-empty")` where the old `IFluidHandlerItem` lookup
returned nothing. `NeoForgeSimFluidService.getFluidInItem` passed whatever it was given straight
through, and `OpticalSensorBlockEntity.tick` asks it about the filter slot **once per tick** -- which
is empty on a freshly placed sensor. Placing an optical sensor took the dedicated server down with a
`ReportedException: Ticking block entity`. The service now answers for the empty stack itself.

This is the same family of hazard as the transfer-API note in the port log: every call site that hands
a possibly-empty `ItemStack` to a 26.2 transfer API is worth a look.

**Covered by** `SimulatedRegistryTest` in Create-e2e, which places every block the three mods register
and ticks it.

## 3.6 Every render type lost its depth test, and the obvious repair was backwards

Eight of them, across all three mods, and the symptom is one a screenshot shows in a second and no
server-side assertion ever will: a burner's flame, an accumulator's diode, a laser, a rope and a
spring all drew **through solid blocks**, from any distance and any angle.

**Half one.** 1.21.1's `RenderType.CompositeState.builder()` started with `LEQUAL_DEPTH_TEST` and
`COLOR_DEPTH_WRITE` already set, so a render type that said nothing about depth still tested and wrote
it -- and most of these types said nothing. 26.2 moved that state onto `RenderPipeline`, whose builder
ends with:

```java
this.depthStencilState.orElse(null),
```

A pipeline holding a null `DepthStencilState` is given no depth attachment at all --
`wantsDepthTexture()` returns false -- so the draw neither tests nor writes. Every type carried across
verbatim silently became an overlay.

**Half two, which cost a run to find.** Naming `VeilRenderPipelines.lequalDepthTest()` -- the compare
op these types had on 1.21.1, by that name -- changes nothing. 26.2 reversed the depth buffer:

```java
public static final DepthStencilState DEFAULT = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true);
```

Near is 1 and far is 0, so the test that keeps the nearer fragment is the *greater* one, and LEQUAL
keeps whatever is furthest away instead. The screenshot after that fix was indistinguishable from the
one before it. **On 26.2, "lequal" is not a synonym for "normal depth testing" -- it is the exact
opposite of it**, and the name is the trap, because it is the name the 1.21.1 code used.

The repair is `VeilRenderPipelines.defaultDepthTest()`, added for this: vanilla's own
`DepthStencilState.DEFAULT`, which stays right whichever way round the buffer is.

| Type | Where | Now |
|---|---|---|
| burner flame | `HotAirBurnerRenderer.flameType` | `defaultDepthTest()` |
| accumulator diode | `RedstoneAccumulatorRenderer.DIODE_RENDER_TYPE` | `defaultDepthTest()` |
| levitite, levitite ghosts | `AeroRenderTypes` | `defaultDepthTest()` |
| laser, lens, rope, spring | `SimRenderTypes` | `defaultDepthTest()` |
| End Sea | `SimRenderTypes` | `noDepthWrite()` alone; it had named LEQUAL, backwards |
| staff overlay | `SimRenderTypes` | `noDepthTestOrWrite()`, see below |
| lock marker | `SimRenderTypes` | unchanged: `noDepthTest()` is right |

Two further defects fell out of the same reading, both in Veil and both fixed there:

- **`noDepthWrite()` reversed the test as well as dropping the write.** It was
  `depth(LESS_THAN_OR_EQUAL, false)`, so a type asking only to stop writing depth quietly got the
  wrong compare op too. It reads the op off `DepthStencilState.DEFAULT` now.
- **A depth state is one snippet, not two.** `STAFF_OVERLAY` set `noDepthWrite()` and then
  `noDepthTest()`; the later one replaces the whole `DepthStencilState`, so the overlay was *writing*
  depth and occluding everything drawn after it. `noDepthTestOrWrite()` says both at once.

**Any render type added from here on must name a depth state**, and `defaultDepthTest()` is almost
always the one meant. A Veil-built type that omits it, or that reaches for `lequalDepthTest()` because
the 1.21.1 source said LEQUAL, is this bug again.

**Covered by** `RenderThroughWallsTest` in Create-e2e, which stands a lit burner and an accumulator
behind a stone wall, on the ground and in a sub-level, and photographs the scene with the wall and
then without it from the same camera. It is a picture for a person rather than an assertion: the
defect is invisible from the server side, and it is what caught the backwards first fix.

## 4. Smaller things noted in passing

- **The docking connector's unpair-on-turn.** `onRemove` used to see the replacing state, so a
  connector that merely *turned* could unpair. `affectNeighborsAfterRemoval` only runs when the block
  actually changes, so that case moved to `onPlace`, which still gets the old state. Behaviour is
  preserved; the mechanism is not the same one, so it is worth a look in game.
- **The nameplate's neighbour check.** `neighborChanged` carries an `Orientation` — which way a
  redstone update travelled, null otherwise — rather than the position it came from. The nameplate
  had two branches on "was it my clockwise neighbour"; both ended up walking the controller chain, so
  it now always takes the safe one. Worth checking a long nameplate row re-forms correctly.
- **Three ponder structures name blocks that no longer exist.**
  `aeronautics/ponder/propeller_bearing/size.nbt` contains `simulated:phantom_sail` and
  `simulated:wooden_wing`; `offroad/ponder/borehead_bearing/excavating.nbt` and the orphaned
  `offroad/ponder/borehead_bearing.nbt` contain `simulated:stirling_engine`. None of the three ids
  exist in the source tree, and `StructureTemplate` maps an unknown id to air, so those scenes draw
  with holes in them. The structures need re-saving by whoever owns the ponder assets.
- **The physics staff's Iris workaround** ended every batch, because Iris would not let one render
  type be ended alone. There is no batching left to end, and no Iris 26.2 build, so it is gone. If
  Iris returns, the translucent parts of the staff are the thing to look at first.
- **Paused sprite animation now holds the sub-frame too.** The old wrap blocked only the frame
  counter, so an interpolating sprite kept sweeping toward the next frame while "paused" — a wobble
  rather than a hold. Holding is what the caller asks for, but it is a visible difference.

## The diagram's lighting is corrected against measurement, not derivation

The diagram now matches 1.21.1 on terrain exactly. Both versions were dumped from their own
framebuffer, before the paper pass, rendering the same contraption:

| region | 26.2 | 1.21.1 | ratio |
|---|---|---|---|
| stripped oak wood | 0.240 | 0.240 | 1.00 |
| blue seat | 0.135 | 0.133 | 0.98 |
| diagram board | 0.296 | 0.248 | 0.84 |
| chest | 0.113 | 0.129 | 1.14 |

Two corrections get it there, and they pull in **opposite** directions -- which is why no single
change to the lighting ever brought both into line, and why this needed measuring rather than
reasoning about.

**Terrain was 0.80x too dark** and is scaled by 1/0.80. The light texture is identical texel for
texel by then -- 1.21.1's loop is transcribed, not approximated -- so the shortfall is somewhere else
in the terrain path. 26.2 samples the lightmap through `sample_lightmap` where 1.21.1 used a plain
`texelFetch`, and section meshes carry their own shading; neither has been confirmed. **The 1/0.80 is
measured, not explained.**

**Features were too bright** and are scaled by 0.59, standing in for the diffuse that
`minecraft_mix_light` used to apply: 1.21.1 drew them through entity render types, and Create has
since moved its `SuperByteBuffer` rendering to `solidMovingBlock()`, whose `block.vsh` applies no
directional light.

That 0.59 is a compromise and cannot be anything else. The board is 2.0x too bright and the chest
1.5x, because the board goes through the block shader and the chest through the entity shader -- but
one lightmap serves both. 0.59 sits between them. What matters is that it puts the board at 0.296,
under the 0.352 at which the paper pass saturates to flat white and destroys the schematic drawing.

To separate them, the board would have to be dimmed at its source -- a colour on the
`SuperByteBuffer` in `DiagramEntityRenderer`, gated on `SimpleSubLevelGroupRenderer.RENDERING_SIMPLE`
-- leaving the lightmap to serve the entity-shader path alone. Not done; the remaining error is 12-16%
and invisible next to the saturation problem it replaced.

**Reproducing any of this:** see `compare-against-1211-worktree` -- a 1.21.1 worktree is kept at
`create-26.2/Simulated-1.21.1`, and four lines in `DiagramScreen.draw` dump the framebuffer from
either side.

## The diagram's paper pass, and the one thing still off

Resolved. The post pass now reproduces 1.21.1 essentially exactly. Measured off both versions'
`finalFbo`, same contraption:

| region | 26.2 | 1.21.1 |
|---|---|---|
| stripped oak wood | 0.665 | 0.665 |
| chest | 0.312 | 0.312 |

Wood comes out `(170.1, 167.2, 156.1)` against `(169.5, 166.7, 155.6)`, and the palette entries the
dither picks match one for one in both count and proportion.

**The bug was a sampler filter, and the assets had been declaring it all along.**
`diagram_palette.png.mcmeta` and `dither.png.mcmeta` both say `"blur": true`. On 1.21.1 Veil passed no
filter for a shader texture, so GL fell back to the texture's own state, which the texture manager had
already set from that metadata -- linear. On 26.2 Veil only builds a sampler object when the JSON
declares one (`ShaderTexture.create`), and with the short `"Name": "<texture>"` form it binds sampler 0
and inherits sampler state that no longer carries the mcmeta. Both textures are now declared
explicitly in `outline_diagram.json`, matching their mcmeta: linear, palette clamped, dither repeating.

Linear filtering is the whole point of the palette lookup. It blends adjacent entries, and those
in-between tones are what give the diagram its gradation -- with nearest, every surface snaps to one
entry and the result reads as flat, blocky panels. The arithmetic that identified it: at
`col_sample = 0.635` the port returned texel 187 while 1.21.1 returned 173, and
`154 + 0.58 * (187 - 154) = 173.1` is exactly the linear blend of texels 4 and 5.

### Still open: the diagram board is about 20% too bright

The board measures 0.814 against 1.21.1's 0.665. Its raw luminance is 0.296 against 0.248, which the
now-linear palette faithfully carries through.

This is the residual of a single lightmap serving two shading paths that no longer agree. Features are
scaled by 0.59 to stand in for the diffuse that Create's move to `solidMovingBlock` dropped, and that
factor is right for the chest -- which matches exactly -- but not for the board, because the chest goes
through `entity.vsh` (which applies `minecraft_mix_light`) and the board through `block.vsh` (which
does not).

To close it, dim the board at its source rather than through the lightmap: a colour on the
`SuperByteBuffer` in `DiagramEntityRenderer`, gated on `SimpleSubLevelGroupRenderer.RENDERING_SIMPLE`,
leaving the 0.59 to serve the entity-shader path alone. It is legible and correctly dithered as it
stands, so this is polish.

**Reproducing any of this:** see `compare-against-1211-worktree` -- a 1.21.1 worktree is kept at
`create-26.2/Simulated-1.21.1`, and a few lines in `DiagramScreen.draw` dump either framebuffer.
