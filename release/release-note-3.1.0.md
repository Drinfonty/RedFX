# Release Notes - Version 3.1.0

A feature update introducing **true directional blood spray & combat momentum**, **customizable directional spray bias**, an updated **in-game configuration GUI**, and perfected particle dispersion mechanics across all supported Minecraft versions.

### Highlights

- **True Directional Blood Spray & Combat Momentum**:
  - **Accurate Damage Source Vector**: In vanilla Minecraft, `LivingEntity.getHurtDir()` is hardcoded to return `0.0F` for all non-player entities, causing previous blood spray calculations to collapse to the victim's facing direction. Blood spray momentum is now computed directly using the relative vector between the damaged mob and the `DamageSource` / attacker position.
  - **Preserved Particle Velocity**: Bypassed vanilla Minecraft's default `Particle` constructor Gaussian noise and velocity normalization in `BloodParticle`, preserving intended directional velocities without spherical scrambling.
  - **Natural Splatter Burst with Directional Bias**: Combines an organic 360° radial splatter around the damaged mob with a forward bias pulled towards the attacker. Blood scatters naturally on the ground and surrounding walls while concentrating along the axis of attack.
  - **Balanced Arc & Bloom Dynamics**: Balanced vertical velocity (`vy = 0.08..0.18`) and radial spread (`omniSpeed = 0.04..0.11`) to prevent excessive projectile distance while giving satisfying arc flight and splatter coverage.

- **Directional Blood Spray Configuration & GUI**:
  - **Config Option `directionalBlood`**: Added a new configuration toggle (default `true`) in `redfx.json` allowing players to enable or disable directional spray momentum.
  - **In-Game Settings Screen**: Added a clean `Directional: ON / OFF` toggle button to the RedFX configuration screen in the top row next to the `Blood: ON / OFF` button.
  - **Omnidirectional Fallback**: When `Directional` is toggled OFF, blood splatters burst in a pure, uniform 360° radial pattern around damaged mobs.

- **Multi-Version Verification & Quality Assurance**:
  - Fully verified and passed complete automated in-game combat smoke tests across all 8 supported Minecraft versions: `26.3`, `26.2`, `26.1`, `1.21.11`, `1.21.10`, `1.21.8`, `1.21.4`, and `1.21.1` on both Fabric and NeoForge loaders (16 configurations total).
