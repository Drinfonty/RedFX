# Release Notes - Version 3.3.0

A major combat feel and creature-identity update introducing **directional weapon slash & chop arcs**, **distinct creature biology themes**, **3D omnidirectional death bursts**, and an expanded **entity blood color palette** across all supported Minecraft versions.

### Highlights

- **Directional Weapon Slashes & Cast-Off Arcs**:
  - **Sword Sweeps**: Sword hits cast off blood particles in a sweeping horizontal crescent arc perpendicular to the swing direction.
  - **Axe Chops**: Axe hits launch heavy vertical downward-and-forward chopping sprays.
  - **Elongated Velocity Streaks**: High-velocity cast-off droplets elongate into directional streaks along impact surfaces.
  - **Configurable Toggle**: Controlled via `directionalSlashes` in [`RedfxConfig`](file:///home/ptphong/Projects/Minecraft/RedFX/common/src/main/java/com/drinfonty/redfx/config/RedfxConfig.java) and the in-game settings screen.

- **3D Omnidirectional Death Bursts**:
  - **Full 360° Radial Explosion**: Slaying any mob or player now unleashes a dense 3D spherical burst of blood (50–75 base particles, scaled by config and weapon multipliers) shooting outward in every direction to paint surrounding floors, walls, and ceilings.
  - **Reliable Trigger & Deduplication**: Triggers cleanly on both death entity events (`status == 3`) and client lethal-hit/death-tick fallbacks with tick-based deduplication to avoid duplicate bursts.
  - **Scaled Theme Bursts**: Creature theme particle bursts (portal wisps, Nether/Wither smoke, Sculk pops) scale up during death explosions.

- **Creature Biology & Distinct Themes**:
  - **Ender Creatures** (Enderman, Ender Dragon, Endermite, Shulker): Void-purple blood with `PORTAL` wisps and erratic low-gravity flutter physics.
  - **Nether & Wither Creatures** (Blaze, Magma Cube, Wither, Wither Skeleton): Molten/ash splatters paired with rising `SMOKE` puffs.
  - **Sculk / Warden**: Deep teal blood paired with `SCULK_CHARGE_POP` bursts.
  - **Arthropods** (Spider, Cave Spider, Silverfish): Neon yellow-green hemolymph blood.
  - **New Entity Blood Colors**:
    - **Snow Golem**: White blood (`#FFFFFF`)
    - **Creaking**: Orange blood (`#FF7700`)
    - **Glow Squid**: Turquoise blood (`#00E5C8`)
    - **Squid**: Inky black blood (`#141414`)
    - **Iron Golem**: Light gray blood (`#C0C0C0`)
    - **Phantom**: Green blood (`#33E633`)
  - **Non-Destructive Config Defaults**: Loading an existing `redfx.json` preserves user overrides while automatically populating newly added default entity colors.

- **Multi-Version Verification & Quality Assurance**:
  - Hardened [`BloodSplatterTest`](file:///home/ptphong/Projects/Minecraft/RedFX/common/src/test/java/com/drinfonty/redfx/canvas/BloodSplatterTest.java) for deterministic stage verification.
  - Fully verified with `./gradlew check` and automated in-game smoke tests across all 8 supported Minecraft versions (`26.3`, `26.2`, `26.1`, `1.21.11`, `1.21.10`, `1.21.8`, `1.21.4`, `1.21.1`) on both Fabric and NeoForge loaders.
