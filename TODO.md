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
- [ ] **Underwater blood effect**: Instead of splatting down like on land, blood underwater should disperse, maybe using smoke particles.

---

## Future Features
- [ ] **Screen Effects**: Add screen blood splatters when a player receives heavy damage or hits a mob up close.
