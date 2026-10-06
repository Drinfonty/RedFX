# Release Notes - Version 3.0.1

A critical hotfix release resolving a client startup crash on Minecraft 1.21.1 through 1.21.8 on both NeoForge and Fabric loaders.

### Fixes

- **Resolve Minecraft 1.21.1–1.21.8 Startup Crash**:
  - Fixed `InvalidMixinException: @Shadow field alpha was not located in the target class net.minecraft.client.particle.SingleQuadParticle` causing Minecraft to crash during bootstrap on versions 1.21.1 through 1.21.8.
  - In Minecraft 1.21.1–1.21.8, particle alpha is declared in `Particle` rather than `SingleQuadParticle` (where Mojang moved it starting in 1.21.10+).
  - Replaced the brittle Mixin with a robust, version-agnostic `ParticleAlphaHelper` utilizing cached `MethodHandle` lookups to dynamically invoke `setAlpha` across the particle class hierarchy with near-native performance and zero bytecode transformation risk.
  - Fully verified and passed complete in-game automated combat smoke tests across all 8 supported Minecraft versions on both Fabric and NeoForge (16 configurations total).
