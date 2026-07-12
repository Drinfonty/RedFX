# Specification: RedFX Mod

RedFX is a client-only Fabric Minecraft mod designed to enhance combat feedback with dynamic blood and impact particles.

## 1. Environment & Target
*   **Minecraft Version**: 26.1.2
*   **Modding Toolchain**: Fabric Loader (>=0.19.3), Fabric Loom (1.17-SNAPSHOT)
*   **Java Version**: Java 25
*   **Side**: Client-Side Only (Must run on vanilla/unmodded servers without registry mismatch or server-side dependencies).

## 2. Features

### 2.1 Dynamic Blood Particles (First Effect)
When a living entity (mob, player, animal) is damaged:
1.  **Hurt Detection**: Detect damage via client-side ticking. Since client rendering might override `animateHurt(float)` due to other combat/visual mods, we monitor the entity's `hurtTime` in the client tick loop.
2.  **Blood Spawning**:
    *   Spawn a cluster of red particles representing blood when `hurtTime == hurtDuration` (at the exact start of being damaged) and when the entity dies (`handleEntityEvent` with status 3).
3.  **Particle Physics**:
    *   **Initial Velocity**: Particles spray **away** from the attacker based on the relative impact angle retrieved via `getHurtDir()`.
    *   **Gravity & Collision**: Particles pull downward and stop on solid block surfaces (handled automatically by `BlockParticleOption` physics).

### 2.2 In-Game Configuration (ModMenu Integration)
*   Integrates with ModMenu to allow modifying settings on the fly.
*   **Configurable Settings**:
    *   **Blood Effects Toggle**: Enable or disable all blood rendering.
    *   **Particle Amount Slider/Cycle**: Low (0.4x), Medium (1.0x), High (2.0x), Ultra (4.0x) multiplier for particle counts.
*   **Saving**: Saves settings to `config/redfx.json` inside the standard Minecraft configuration directory.

### 2.3 Future Features
*   **Entity-Specific Blood Colors**:
    *   Red for typical animals/monsters (Cows, Pigs, Zombies, Players).
    *   Green/Lime for Slimes.
    *   Purple/Black particles for Endermen.
    *   Bone-colored/white particles for Skeletons.
    *   Lava/fire particles for Blazes/Magma Cubes.
*   **Blood Decals (Stains)**:
    *   Particles that land on the ground leave temporary blood splats on blocks.
*   **Screen Splatters**:
    *   Direct hits close to the player splash blood onto the screen/HUD.

## 3. Technical Design

### 3.1 State Ticking Mixin
We inject a Spongepowered Mixin targeting `net.minecraft.world.entity.LivingEntity`.
*   **Target Method**: `tick` at `HEAD`.
*   **Trigger**: `if (self.hurtTime == self.hurtDuration && self.hurtTime > 0 && self.deathTime == 0)`.
*   **Yaw retrieval**: Calls `self.getHurtDir()` to align directional spray.

### 3.2 Configuration Storage
*   Class: `com.drinfonty.redfx.config.RedfxConfig`
*   Deserializer: GSON
*   Saves dynamically on closing the configuration screen.

### 3.3 GUI rendering
*   Uses Minecraft's modern snapshot rendering engine where `Screen` classes implement state extraction instead of drawing calls:
    ```java
    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick)
    ```
*   Provides button widgets to cycle settings and links directly into ModMenu using `ModMenuApi` entrypoint.
