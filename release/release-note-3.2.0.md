# Release Notes - Version 3.2.0

A major visual and polish update introducing **organic procedural splatter shapes with satellite droplets**, **seamless blood pool melding**, **world-space mottled liquid shading**, and important **bug fixes and rendering optimizations** across all supported Minecraft versions.

### Highlights

- **Procedural Organic Splatters & Satellite Droplets**:
  - **Dynamic Blob Generation**: Replaced fixed 16×16 static bitmasks with procedural organic silhouettes perturbed by low-frequency angular harmonics and per-texel jitter. Every blood impact now forms a uniquely shaped puddle rather than repeating the same stamp patterns.
  - **Satellite Droplets & Tendrils**: Impacts now cast realistic satellite droplets and thin connecting tendrils outward, capturing the high-velocity kinetic spray of combat and naturally bridging adjacent splatter impacts.
  - **Organic Pool Melding**: Overlapping blood splatters in [`ClientCanvasStore`](file:///home/ptphong/Projects/Minecraft/RedFX/common/src/main/java/com/drinfonty/redfx/client/ClientCanvasStore.java) now smoothly blend colors (averaging RGB while preserving the dominant alpha) rather than creating harsh cuts or hard overwrites, forming cohesive pools of blood on the ground.
  - **Continuous Mottled Shading**: Liquid color texturing is now computed using global world-space coordinates, ensuring seamless, unified lighting and liquid variation across adjacent blocks and merging splatters.

- **Bug Fixes & Correctness**:
  - **Particle Alpha Reflection Fix on 26.x**: Fixed a silent `WrongMethodTypeException` in `ParticleAlphaHelper` on Minecraft 26.x where `setAlpha` was moved to `SingleQuadParticle`, properly adapting method handle types and prioritizing `setAlpha` / `method_3084` name lookups.
  - **Removed Duplicate Underwater Smoke**: Fixed an issue where underwater blood clouds were registered twice with `ParticleEngine`, causing them to tick twice per game tick and expire at $2\times$ speed.
  - **Broken Block Pending Decal Cleanup**: Decal clearing now purges queued delayed stages from [`ClientCanvasStore`](file:///home/ptphong/Projects/Minecraft/RedFX/common/src/main/java/com/drinfonty/redfx/client/ClientCanvasStore.java), preventing floating blood decals from appearing in mid-air if a block was destroyed while gradual splatter or wall-drip blossoms were still in progress.
  - **Accurate Weapon Scaling Attribution**: Damage source attribution now checks the actual `LivingEntity` attacker from `DamageSource` rather than falsely applying the local player's held weapon multiplier to environmental damage or mobs fighting nearby.
  - **Low-Health Drip Alpha Consistency**: Ensured low-health dripping particles properly apply configured `bloodOpacity` and `particleLifetimeSeconds` identically to impact particles.

- **Performance & Code Simplifications**:
  - **NeoForge Dynamic Model Cache Optimization**: Normalized `Canvas` timestamps in `SurfaceCanvas` so NeoForge's quad cache keys solely on texel content and surface geometry, preventing cache thrashing from timestamp and erosion ticks.
  - **Deduplicated GUI & Rendering Logic**: Consolidated 24-color preset tables between configuration screens and streamlined surface box-comparison calculations in [`PaintSurface`](file:///home/ptphong/Projects/Minecraft/RedFX/common/src/main/java/com/drinfonty/redfx/client/render/PaintSurface.java).

- **Multi-Version Verification & Quality Assurance**:
  - Fully verified and tested across all 8 supported Minecraft versions: `26.3`, `26.2`, `26.1`, `1.21.11`, `1.21.10`, `1.21.8`, `1.21.4`, and `1.21.1` on both Fabric and NeoForge loaders.
