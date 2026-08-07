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

| Branch Name | Built Against | Supported Minecraft | Mod Version | NeoForge | Java |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`main`** | **`26.2`** | `26.2` | `1.1.2` | `26.2.0.45-beta` | 25 |
| **`legacy-26.1`** | **`26.1.2`** | `26.1` – `26.1.2` | `1.1.2` | `26.1.2.94` | 25 |
| **`legacy-1.21`** | **`1.21.11`** | `1.21` – `1.21.11` | `1.1.2` | `21.11.45` | 21 |

"Supported Minecraft" is the range declared in `minecraft_dependency`, and is what
`modrinth_game_versions` publishes. Only the "Built Against" version is compiled and
launched during verification.

### Fabric mapping namespaces

This differs per branch and decides how a Fabric jar must be verified before publishing:

| Branch Name | Loom | Loom Plugin | Fabric Production Namespace | Verify Fabric With |
| :--- | :--- | :--- | :--- | :--- |
| **`main`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |
| **`legacy-26.1`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |
| **`legacy-1.21`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |

Fabric does not publish intermediary mappings for Minecraft 26.x, so on `main` and
`legacy-26.1` the `jar` output *is* the publishable artifact and the dev client runs in the
same namespace as production. On `legacy-1.21` the published artifact is `remapJar`'s
output, and the dev client namespace does **not** match production — see
[Pre-Publish Verification](#2-pre-publish-verification).

NeoForge runs Mojang mappings on every branch, so its jar is never remapped.

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

### 2. Pre-Publish Verification

**Always run these before publishing.** A dev client is not enough to tell you a jar works.

- **Fabric — production client (this branch only):**
  ```bash
  ./gradlew :fabric:runProdClient
  ```
- **NeoForge — packaged jar:**
  ```bash
  ./gradlew :neoforge:runClient -PtestJar
  ```

#### Why Fabric needs `runProdClient` here

On this branch the published Fabric jar is remapped to the **intermediary** namespace, but
Loom's `runClient` runs the game in the **named** (Mojang) namespace. A production jar
loaded into a dev client therefore cannot match its own mixin targets: Mixin logs

```
@Mixin target net.minecraft.class_703 was not found ... ParticleMixin
```

as a *warning*, skips every mixin, and the client still reaches the main menu looking
perfectly healthy. That false pass is how 1.1.1 and 1.1.2 were published broken.
`:fabric:runClient -PtestJar` now refuses to run on this branch and points here instead.

`runProdClient` launches Fabric Loader the way the vanilla launcher does — the obfuscated
Minecraft jar plus intermediary mappings, with the mod loaded from an isolated
`fabric/run-prod/mods/` directory alongside the real Fabric API. Loader remaps the game at
runtime exactly as it does on a user's machine, so a mapping mistake fails here the same
way it fails for them. Everything comes from caches Loom has already populated, so there is
nothing extra to download and `~/.minecraft` is untouched.

*(On the `main` and `legacy-26.1` branches Minecraft runs in Mojang mappings in production,
so there is no remap step and no namespace gap — `:fabric:runClient -PtestJar` is a faithful
test there and `runProdClient` does not exist.)*

#### NeoForge `-PtestJar`

NeoForge runs Mojang mappings in both dev and production, so `-PtestJar` is faithful — but
it must actually load the jar. Declaring source sets in `neoForge.mods` hands them to FML's
in-dev folder locator, which **wins over** a jar in `mods/`. Check the log says:

```
 - redfx (jar(mods/redfx-<version>-<mc>-neoforge.jar))
```

and *not* `composite(folder(...build/classes/java/main), ...)`. The build now arranges the
former by staging the jar as the only mod in an isolated `neoforge/run-testjar/`.

### 3. Static Mixin Verification (Fabric, this branch)

`:fabric:verifyRemappedMixins` reads the remapped jar back with ASM and fails the build if
any `@Mixin` target, `@Shadow` member, injector selector or `@At` target into an obfuscated
Minecraft class is still in Mojang names. It runs automatically as part of `check`, after
every `remapJar`, and before the jar is copied to `release/` or the mods directory.

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
   Resolve any differences in build files or package configurations. Note that build
   configuration is **not** uniformly portable between branches — `main` and `legacy-26.1`
   use the `fabric-loom` plugin with no remap step, while `legacy-1.21` uses
   `fabric-loom-remap` and publishes `remapJar`'s output. Anything touching mappings,
   mixins, or which task produces the published jar has to be re-derived per branch rather
   than cherry-picked blindly.
5. **Verify package build locally** — always, before pushing:
   ```bash
   ./gradlew clean build
   ```
   Then run the Fabric verification for the branch you are on, plus NeoForge:
   ```bash
   # main / legacy-26.1        # legacy-1.21
   ./gradlew :fabric:runClient -PtestJar
   ./gradlew :fabric:runProdClient

   ./gradlew :neoforge:runClient -PtestJar
   ```
   See [Pre-Publish Verification](#2-pre-publish-verification) for what each one proves and
   what to check in the log. A dev client reaching the main menu is **not** sufficient
   evidence that a Fabric jar works on `legacy-1.21`.
6. **Push changes** to the remote branch once verification succeeds.
