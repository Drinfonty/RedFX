# Release Notes - Version 3.3.1

A performance and stability update that eliminates severe frame drops around mob farms and keeps gameplay smooth during large battles across both Fabric and NeoForge.

### Performance & Fixes

- **Fixed Severe Frame Drops Around Mob & Spider Farms**:
  - Resolved an issue where repeated mob kills in tight spaces (such as spider farms or mob grinders) could cause periodic lag spikes or sudden FPS drops when blood dripped over block edges.
  - Blood splatters and edge drips now update smoothly together rather than triggering heavy repeated chunk redraws when many mobs die in the same spot.

- **Smoother Framerates in Large Fights**:
  - Added smart protection against extreme particle bursts so crowded fights and active mob farms stay responsive without bogging down your game.
  - Distant mobs now spawn fewer blood particles—and skip them entirely when far away—so off-screen or far-away skirmishes don't impact your FPS.

- **Lighter World & Chunk Rendering**:
  - Reduced overhead on blocks without blood decals, keeping normal exploration and building just as fast as vanilla.
  - Improved how blood decals are rendered on complex blocks (like stairs, slabs, and chests) and smoothed out memory usage during long play sessions to prevent sudden hitches.

- **Full Multi-Version Support**:
  - Tested and verified in-game on Minecraft 1.20.1, 1.21.1, 1.21.4, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2, and 26.3 for both Fabric and NeoForge.
