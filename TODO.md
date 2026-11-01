# TODO: RedFX Mod

## Version 1.0.0
### Phase 1: Setup & Initial Blood Particles
- [x] Create project documentation (`README.md`, `TODO.md`, `SPEC.md`)
- [x] Implement client-side damage detection mixin (`LivingEntity.animateHurt` and `handleEntityEvent`)
- [x] Implement directional blood particles using `BlockParticleOption` (Redstone Block)
- [x] Add directional spray physics using impact yaw angle
- [x] Verify compilation and launch the developer client to test mob damage interaction
- [x] Integrate with ModMenu and add configuration menu (toggles blood effects and adjusts particle multipliers)

### Phase 2: Particle Collision & Ground Decals
- [x] Add block collision check to make blood particles stop and slide on block surfaces
- [x] Render temporary blood splats/stains on blocks when particles land

### Phase 3: Entity-Specific Blood Colors
- [x] Detect entity type in damage mixin
- [x] Customize blood color:
  - [x] Red: Zombies, Creepers, Animals, Players
  - [x] Lime Green: Slimes
  - [x] Purple/Black: Endermen
  - [x] Bone White: Skeletons, Wither Skeletons
  - [x] Yellow/Flame: Blazes, Magma Cubes

### Phase 4: Splat Textures & Particle Scale
- [x] Implement custom splat textures for landed blood particles
- [x] Configure blood particle amount based on weapon type or damage value

---

## Version 1.1.0
- [x] **Splats on wall**: Wall splats with flat decals and correct surface alignment.
- [x] **Splat dust**: Color-matched `falling_dust` particles spawning upon surface impact.
- [x] **Drip effect**: When a mob or player is low on health, have blood dripping from them (not spewing) even when they are not being damaged.
- [x] **Underwater blood effect**: Instead of splatting down like on land, blood underwater should disperse, maybe using smoke particles.

## Version 1.1.1
- [x] Allows adjusting the particle sizes
- [x] Allows adjusting the color saturation of the particles
- [x] Add a reset button in the config for people to reset all settings to default

---

## Future Features
- [ ] **Screen Effects**: Add screen blood splatters when a player receives heavy damage or hits a mob up close.

### Combat Dynamics & Impact "Juice"
- [x] **Directional Weapon Slashes & Cast-off Arcs**: Spawn crescent arcs and elongated cast-off streaks along weapon swing planes (e.g. sword sweeps, jump crits) connecting weapon motion directly to decals.
- [ ] **Velocity-Stretched Flying Droplets (Needles / Teardrops)**: Elongate blood particle quads along their velocity vector $(\vec{v})$ while flying at high speed, easing into round drops as they decelerate.
- [ ] **Crit & Finisher Mist Bursts**: Spawn a lingering crimson vapor / aerosol mist cloud upon lethal hits or heavy critical strikes that gently drifts before dissipating.

### Fluid & Surface Realism
- [ ] **Drying & Coagulation Aging Progression**: Transition splatters over time through three distinct visual states:
  - *Fresh*: Bright, wet, translucent rim, rich crimson core.
  - *Coagulating*: Loses gloss/brightness, shifts towards darker, desaturated dried burgundy / dark maroon.
  - *Dried*: Edge crusting / flaking before final fade or erosion.
- [ ] **Surface-Aware Splash Dynamics**:
  - *Glass / Ice / Polished*: Higher droplet bounce, sharper splatter shapes, longer sliding wall drips.
  - *Porous / Earthy (Dirt, Sand, Wood, Sponge)*: Flatter matte stains; sand/gravel absorb droplets into smaller concentrated stains.
  - *Water / Lava*: Expanding diluted ripple rings in water; sizzling steam puffs in lava.
- [ ] **Corner & Stair Wrapping (Liquid Bleed)**: Allow droplets landing near block edges or stair risers to trickle over corners and down vertical faces.

### Creature Biology & Distinct Themes
- [x] **Ender Blood (Void Shimmer / Inverted Wisps)**: Endermen, Shulkers, and the Ender Dragon spill deep purple/black obsidian fluid laced with floating teleportation flecks or inverse-gravity wisps.
- [x] **Arthropod Hemolymph**: Spiders, Cave Spiders, and Silverfish splatter neon yellow-green or iridescent amber hemolymph.
- [x] **Wither & Nether Bleed**: Wither Skeletons, Magma Cubes, and Blazes leave scorched, smoking charcoal-black or tar-like residues.
- [x] **Sculk / Warden Resonance**: Wardens and sculk-infected entities yield faintly glowing cyan / teal bioluminescent splatter that pulses before dissolving.

### Dynamic Lighting & Specular Glisten
- [ ] **Faux-Normal / Wet-Highlight Shimmer**: Calculate a directional highlight using the boundary distance fields ($d_1, d_2, d_3$) to render subtle meniscus highlights and core ambient shadows for a thick, 3D beaded liquid look under vanilla lighting.
- [ ] **Shaders / PBR LabPBR Support**: Expose RedFX decal quads with specular smoothness channels for Iris/Oculus shader packs to realistically reflect sunlight, moonlight, and torchlight.

