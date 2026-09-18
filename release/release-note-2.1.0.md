# Release Notes - Version 2.1.0

A feature and compatibility release adding support for **Minecraft 26.3**, expanding multi-geometry splatter rendering across diverse surfaces, enhancing rendering performance, and introducing an end-to-end automated combat smoke testing framework.

### Highlights

- **Minecraft 26.3 Compatibility**:
  - Full support for Minecraft 26.3 on Fabric with updated mappings and runtime hooks.
  - Accommodates Mojang's internal entity class hierarchy refactors (`Enderman`) and interaction animations.
  - *(Note: NeoForge builds for 26.3 will be released as soon as the upstream NeoForge decompilation pipeline stabilizes).*

- **Multi-Block Surface Splatter Enhancements**:
  - Enhanced and hardened decal projection across non-standard block surfaces including chests, slabs, stairs, snow layers, and snow blocks.
  - Decals dynamically mesh and conform to half-slabs and complex bounding boxes without clipping or floating.
  - Thread-safe chunk marking and optimized decal mesh generation prevent frame drops or stutter during intense combat.

- **Performance & Canvas Engine Optimizations**:
  - Streamlined `CanvasMesher` quad generation and `ClientCanvasStore` caching.
  - Improved dirty-chunk scheduling and faster spatial key lookups.
  - Pixel-by-pixel erosion algorithm smoothly dissolves blood puddles with minimal CPU and GPU overhead.

- **Automated Combat Smoke Test Suite**:
  - Integrated a headless in-game combat test harness across all supported versions (`1.21.1` through `26.3`).
  - Automatically simulates player combat against live mobs to continuously verify particle flight, collision, and real-time decal projection.
