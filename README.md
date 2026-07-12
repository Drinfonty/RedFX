# RedFX

A client-only Fabric Minecraft Mod that adds combat and blood effects to the game, targeting Minecraft version **26.1.2**.

## Features
- **Client-Side Blood Particles**: Spawns flying blood particles when mobs are hit, completely client-side. Works on vanilla servers!
- **Directional Blood Spray**: Blood particles spray realistically away from the direction of the hit/attacker.
- **ModMenu Integration**: Custom configuration screen to toggle blood effects and adjust particle counts on the fly.

For features specification and roadmap details, see:
*   [SPEC.md](file:///home/ptphong/Projects/Minecraft/RedFX/SPEC.md): Feature specifications and technical design.
*   [TODO.md](file:///home/ptphong/Projects/Minecraft/RedFX/TODO.md): Task lists and progress tracking.

## Development Setup

1.  **Build the Mod**:
    Compile, remap, and deploy the mod JAR to your Minecraft mods folder (`/home/ptphong/.minecraft/mods`):
    ```bash
    ./gradlew build
    ```

2.  **Run/Debug in Development Environment**:
    Launch the Minecraft client directly with the mod loaded:
    ```bash
    ./gradlew runClient
    ```

## Mod Information
*   **Mod ID**: `redfx`
*   **Package**: `com.drinfonty.redfx`
*   **Main Class**: `com.drinfonty.redfx.RedfxMod`
*   **Client Entrypoint**: `com.drinfonty.redfx.client.RedfxModClient`
