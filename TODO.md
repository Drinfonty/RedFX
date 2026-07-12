# TODO: RedFX Mod

## Phase 1: Setup & Initial Blood Particles
- [x] Create project documentation (`README.md`, `TODO.md`, `SPEC.md`)
- [x] Implement client-side damage detection mixin (`LivingEntity.animateHurt` and `handleEntityEvent`)
- [x] Implement directional blood particles using `BlockParticleOption` (Redstone Block)
- [x] Add directional spray physics using impact yaw angle
- [x] Verify compilation and launch the developer client to test mob damage interaction
- [x] Integrate with ModMenu and add configuration menu (toggles blood effects and adjusts particle multipliers)

## Phase 2: Refined Particle Physics & Custom Textures
- [ ] Implement custom high-fidelity `BloodParticle` with proper texture sheets
- [ ] Add block collision check to make blood particles stop and slide on block surfaces
- [ ] Configure blood particle amount based on weapon type or damage value

## Phase 3: Entity-Specific Blood Colors
- [ ] Detect entity type in damage mixin
- [ ] Customize blood color:
  - Red: Zombies, Creepers, Animals, Players
  - Lime Green: Slimes
  - Purple/Black: Endermen
  - Bone White: Skeletons, Wither Skeletons
  - Yellow/Flame: Blazes, Magma Cubes

## Phase 4: Ground Decals & Screen Effects
- [ ] Render temporary blood splats/stains on blocks when particles land
- [ ] Add screen blood splatters when a player receives heavy damage or hits a mob up close
