# Specification: RedFX Mod

RedFX is a client-only Fabric Minecraft mod designed to enhance combat feedback with dynamic blood splatters and surface impact particles.

## 1. Environment & Support
*   **Modern Branch (`main`)**:
    *   **Minecraft Version**: 26.1.2 (Java 25)
    *   **Modding Toolchain**: Fabric Loader (>=0.19.3), Fabric Loom (1.17-SNAPSHOT)
*   **Legacy Branch (`legacy-1.21`)**:
    *   **Minecraft Version**: 1.21 to 1.21.11 (Java 21)
    *   **Modding Toolchain**: Fabric Loader, Fabric Loom (1.14-SNAPSHOT)
*   **Side**: Client-Side Only (Must run on vanilla/unmodded servers without registry mismatch or server-side dependencies).

---

## 2. Features

### 2.1 Dynamic Blood Particles
When a living entity (mob, player, animal) is damaged:
1.  **Hurt Detection**: Detects damage client-side by monitoring the entity's `hurtTime` in the client tick loop.
2.  **Directional Spray**: Particles spray **away** from the attacker based on the relative impact angle retrieved via `getHurtDir()`.
3.  **Physical Properties**: Spawns flying blood droplets affected by gravity and friction.
    *   **Airborne Lifetime**: Spawns with a short 2-second flying phase lifetime (`this.lifetime = 40`) to prevent failed landing drops from floating in midair indefinitely.

### 2.2 Entity-Specific Blood Colors
Blood particles automatically inherit unique colors based on the entity type:
*   **Red**: Typical animals/monsters (Cows, Pigs, Zombies, Players, Villagers, Spiders).
*   **Slime Green**: Slimes.
*   **Enderman Purple**: Endermen.
*   **Teal/Sculk Blue**: Wardens.
*   **Bone Gray/White**: Skeletons and Wither Skeletons.
*   **Fire/Orange-Yellow**: Blazes and Magma Cubes.

### 2.3 Surface Splattering & Alignment (Decals)
*   **Collision Detection**: Hooks into Minecraft's internal `Particle.move()` physics signals (`xd == 0.0` or `zd == 0.0`) to detect collision with solid surfaces.
*   **Surface Alignment**: Supports 6 surface normals (`UP`, `DOWN`, `NORTH`, `SOUTH`, `EAST`, `WEST`). Flat quads align parallel to the block face they land on.
*   **Flat Texturing**: Switches to custom splatter textures (`blood_splat_1` through `5`) on landing.
*   **Depth Staggering**: Uses a **64-tier discrete depth ring buffer** (`0.005` to `0.043` blocks offset) to ensure overlapping splats never share identical depth values, mathematically eliminating Z-fighting lines.

### 2.4 Splatter Impact Dust Effect
*   Spawns 1 `minecraft:falling_dust` particle upon surface collision to simulate a droplet splatter burst.
*   **Color-Matched**: The falling dust particle dynamically copies the exact RGB color tint of the parent blood droplet.

### 2.5 In-Game Configuration (ModMenu)
*   Provides a clean in-game screen to modify settings on the fly.
*   **Configurable Settings**:
    *   **Blood Effects**: Toggle all blood rendering ON/OFF.
    *   **Particle Amount**: Low (0.4x), Medium (1.0x), High (2.0x), Ultra (4.0x) multiplier.
    *   **Particle Style**: Default (Wool textures), Spray (Poof textures), Shred (TNT textures).
    *   **Splat Texture**: Toggle flat landing decals ON/OFF.
    *   **Splat Dust**: Toggle falling dust impact particles ON/OFF.
    *   **Landed Lifetime**: Slider to adjust landed splat lifetime (1s to 30s).
*   **Storage**: Saves to `config/redfx.json`.

---

## 3. Technical Design

### 3.1 Mixins
Targeting `net.minecraft.world.entity.LivingEntity`:
*   **Target Method**: `tick` at `HEAD`.
*   **Trigger**: `self.hurtTime == self.hurtDuration && self.hurtTime > 0`.
*   **Yaw Retrieval**: Calls `self.getHurtDir()` to align directional spray.

### 3.2 High-Resolution Assets
*   **Mod Menu Icon**: Single high-resolution 256x256 pixel-art droplet icon (`assets/redfx/icon.png`) for crisp rendering on high-DPI displays.
