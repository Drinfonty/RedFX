# RedFX

RedFX is a client-side Minecraft mod that adds immersive, realistic combat visual effects (such as directional blood sprays, custom splatter decals, falling dust, and underwater bubbles) when attacking entities. Since it is entirely client-side, it works on vanilla servers.

It is built as a multi-platform project with a shared `:common` codebase supporting both **Fabric** and **NeoForge**.

---

## Mod Features
- **Client-Side Blood Particles**: Spawns flying blood particles when mobs are hit, completely client-side.
- **Directional Blood Spray**: Blood particles spray realistically away from the direction of the hit/attacker.
- **Multi-Platform Config GUI**: Fully integrated with in-game mod menus (via **ModMenu** on Fabric, and NeoForge's built-in **Mods List Config** button on NeoForge) to adjust particle styles, lifetimes, and count multipliers on the fly.

For feature specifications and roadmap details, see:
*   [SPEC.md](file:///home/ptphong/Projects/Minecraft/RedFX/SPEC.md): Feature specifications and technical design.
*   [TODO.md](file:///home/ptphong/Projects/Minecraft/RedFX/TODO.md): Task lists and progress tracking.

---

## Branch & Minecraft Version Mapping

The project maintains different branches to target different major Minecraft and loader versions:

| Branch Name | Minecraft Target | Mod Version | Build System Notes |
| :--- | :--- | :--- | :--- |
| **`main`** | **`26.2`** (1.21.4) | `1.1.2` | Uses Loom 1.17. |
| **`legacy-26.1`** | **`26.1.2`** (1.21.2 - 1.21.3) | `1.1.2` | Uses Loom 1.17. |
| **`legacy-1.21`** | **`1.21.11`** (1.20.5 - 1.21.1) | `1.1.2` | Uses Loom 1.14.10. Requires remapped common classes. |

---

## Building the Mod

To compile and package the production `.jar` files for release:

- **Build Fabric Mod:**
  ```bash
  ./gradlew :fabric:build
  ```
- **Build NeoForge Mod:**
  ```bash
  ./gradlew :neoforge:build
  ```
- **Build All Subprojects:**
  ```bash
  ./gradlew build
  ```

Output binaries are placed under the `${project.rootDir}/release/` directory:
- Fabric: `release/redfx-<version>-<mc-suffix>-fabric.jar`
- NeoForge: `release/redfx-<version>-<mc-suffix>-neoforge.jar`

---

## Running and Testing

### 1. Standard Development Run
By default, running the standard client tasks loads Minecraft using loose class directories and resource folders directly from your workspace compilation output (unpackaged classpath):
- **Fabric Dev Client:**
  ```bash
  ./gradlew :fabric:runClient
  ```
- **NeoForge Dev Client:**
  ```bash
  ./gradlew :neoforge:runClient
  ```

### 2. Standalone Package Testing (`-PtestJar`)
To perform integration testing using the actual built production `.jar` file on the classpath (to verify mixin refmaps, class packaging, resource folders, and manifest generation work outside the IDE):
- **Test Packaged Fabric Jar:**
  ```bash
  ./gradlew :fabric:runClient -PtestJar
  ```
- **Test Packaged NeoForge Jar:**
  ```bash
  ./gradlew :neoforge:runClient -PtestJar
  ```

*Note: The `-PtestJar` argument triggers the `jar` task and filters compile folders out of the loader classpath, forcing Minecraft to load only the resulting `.jar` file.*

---

## Development Workflow & Cross-Branch Porting

To maintain consistency across all Minecraft targets and avoid duplicating manual work:

1. **Implement on `main` branch first**:
   Apply and test new features or bug fixes directly on the `main` branch.
2. **Commit and push** to `main`.
3. **Port to legacy branches using Cherry-Picking**:
   Switch to the target branch and cherry-pick the relevant commit hashes:
   ```bash
   git checkout legacy-1.21
   git cherry-pick <commit-hash-from-main>
   ```
4. **Resolve conflicts**:
   Resolve any differences in build files or package configurations. Ensure that:
   - Mixin refmaps remain properly configured for the target loader versions.
   - Resource locations and mappings align.
5. **Verify package build locally**:
   Always run a full build and run testing on the legacy branch using the packaged jar command before pushing:
   ```bash
   ./gradlew clean build
   ./gradlew :fabric:runClient -PtestJar
   ./gradlew :neoforge:runClient -PtestJar
   ```
6. **Push changes** to the remote branch once verification succeeds.
