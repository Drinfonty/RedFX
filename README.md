# RedFX

RedFX is a client-side Minecraft mod that adds immersive, realistic combat visual effects (such as directional blood sprays, custom splatter decals, falling dust, and underwater bubbles) when attacking entities. Since it is entirely client-side, it works on vanilla servers.

It is built as a multi-platform project with a shared `:common` codebase supporting both **Fabric** and **NeoForge**.

---

## Mod Features
- **Client-Side Blood Particles**: Spawns flying blood particles when mobs are hit, completely client-side.
- **Directional Blood Spray**: Blood particles spray realistically away from the direction of the hit/attacker.
- **Multi-Platform Config GUI**: Fully integrated with in-game mod menus (via **ModMenu** on Fabric, and NeoForge's built-in **Mods List Config** button on NeoForge) to adjust particle styles, lifetimes, and count multipliers on the fly.

For feature specifications and roadmap details, see:
*   [SPEC.md](SPEC.md): Feature specifications and technical design.
*   [TODO.md](TODO.md): Task lists and progress tracking.

---

## Branch & Minecraft Version Mapping

The project maintains different branches to target different major Minecraft and loader versions:

| Branch Name | Built Against | Supported Minecraft | NeoForge | Java |
| :--- | :--- | :--- | :--- | :--- |
| **`mc-26.2`** | **`26.2`** | `26.2` | `26.2.0.81` | 25 |
| **`mc-26.1`** | **`26.1.2`** | `26.1` – `26.1.2` | `26.1.2.94` | 25 |
| **`mc-1.21.11`** | **`1.21.11`** | `1.21.11` only | `21.11.45` | 21 |
| **`mc-1.21.10`** | **`1.21.10`** | `1.21.9` – `1.21.10` | `21.9`–`21.10` | 21 |
| **`mc-1.21.8`** | **`1.21.8`** | `1.21.5` – `1.21.8` | `21.5`–`21.8` | 21 |
| **`mc-1.21.4`** | **`1.21.4`** | `1.21.2` – `1.21.4` | `21.2`–`21.4` | 21 |
| **`mc-1.21.1`** | **`1.21.1`** | `1.21` – `1.21.1` | `21.0`–`21.1` | 21 |

"Supported Minecraft" is the range declared in `minecraft_dependency`, and is what
`modrinth_game_versions` publishes. Only the "Built Against" version is compiled and
launched during verification.

The 1.21 line needs five branches because Mojang overhauled rendering and APIs across the line:
- 1.21.2 introduced `DelegateBakedModel` and changed block sprite atlas access (`ModelManager.getAtlas(...)`).
- 1.21.5 redesigned block models from `BakedModel` to `BlockStateModel` / `DynamicBlockStateModel` (NeoForge) and `WrapperBlockStateModel` (Fabric API), and changed `LevelChunk.setBlockState`'s signature.
- 1.21.8 introduced `ChunkSectionLayer` in Fabric rendering (bridged backwards to 1.21.5 via dynamic layer shim).
- 1.21.9 replaced `ParticleRenderType` with `SingleQuadParticle.Layer`.
- 1.21.11 renamed `ResourceLocation` to `Identifier` and moved `AbstractSkeleton` into `monster.skeleton`.
- NeoForge also swapped `FMLEnvironment.dist` for `getDist()` after 21.8. Each branch differs
from its neighbour by only those few edits.

**Declare only what compiles.** `mc-1.21.11` previously advertised `1.21`–`1.21.11` while
the source compiled against 1.21.11 alone, because 1.21.11 renamed `ResourceLocation` to
`Identifier`, moved `AbstractSkeleton` into `monster.skeleton`, and 1.21.9 replaced
`ParticleRenderType` with `SingleQuadParticle.Layer`. NeoForge caught it — its
`[21.11.0,)` range only resolves on 1.21.11 — but Fabric would have *installed* on 1.21.5
and crashed at runtime, which is worse. Before widening a range, compile against the
oldest version in it. The mod version is deliberately not listed per branch: it
lives in `gradle/mod.properties` and is identical everywhere — see
[Branch Layout](#branch-layout).

### Fabric mapping namespaces

This differs per branch and decides how a Fabric jar must be verified before publishing:

| Branch Name | Loom | Loom Plugin | Fabric Production Namespace | Verify Fabric With |
| :--- | :--- | :--- | :--- | :--- |
| **`mc-26.2`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |
| **`mc-26.1`** | 1.17 | `fabric-loom` | Mojang (`official`) — no remap step | `:fabric:runClient -PtestJar` |
| **`mc-1.21.11`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |
| **`mc-1.21.10`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |
| **`mc-1.21.8`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |
| **`mc-1.21.4`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |
| **`mc-1.21.1`** | 1.14.10 | `fabric-loom-remap` | **`intermediary`** — `remapJar` rewrites the mod | `:fabric:runProdClient` |

Fabric does not publish intermediary mappings for Minecraft 26.x, so on `mc-26.2` and
`mc-26.1` the `jar` output *is* the publishable artifact and the dev client runs in the
same namespace as production. On the `mc-1.21.x` branches the published artifact is `remapJar`'s
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

- **Fabric — packaged jar:**
  ```bash
  ./gradlew :fabric:runClient -PtestJar
  ```
- **NeoForge — packaged jar:**
  ```bash
  ./gradlew :neoforge:runClient -PtestJar
  ```

> **On the `mc-1.21.x` branches, Fabric is verified with `./gradlew :fabric:runProdClient` instead.**
> `-PtestJar` is wired to refuse to run there.

#### Why the Fabric step differs per branch

On `mc-26.2` and `mc-26.1`, Fabric publishes no intermediary mappings for Minecraft 26.x.
Those branches use the `fabric-loom` plugin, there is no `remapJar` step, and the `jar`
output *is* the publishable artifact. The dev client runs in the **same** Mojang namespace
as production, so a mixin that resolves under `-PtestJar` resolves for a user.

On the `mc-1.21.x` branches the published jar is remapped to the `intermediary` namespace while
`runClient` stays named. A production jar loaded into a dev client there cannot match its
own mixin targets — Mixin logs `@Mixin target net.minecraft.class_703 was not found`, skips
every mixin, and the client still reaches the main menu looking healthy. That false pass is
how 1.1.1 and 1.1.2 were published broken. `runProdClient` launches a real
intermediary-namespace client instead. Do not assume a verification step is portable
between branches.

#### NeoForge `-PtestJar`

NeoForge runs Mojang mappings in both dev and production, so `-PtestJar` is faithful — but
it must actually load the jar. Declaring source sets in `neoForge.mods` hands them to FML's
in-dev folder locator, which **wins over** a jar in `mods/`. Check the log says:

```
 - redfx (jar(mods/redfx-<version>-<mc>-neoforge.jar))
```

and *not* `composite(folder(...build/classes/java/main), ...)`. The build now arranges the
former by staging the jar as the only mod in an isolated `neoforge/run-testjar/`.

---

## Branch Layout

Eight branches. Seven target a Minecraft version; one holds everything that does not.

| Branch | Role |
| :--- | :--- |
| **`main`** | Version-agnostic content only. Never built or published directly. |
| **`mc-26.2`** | Minecraft 26.2 |
| **`mc-26.1`** | Minecraft 26.1.x |
| **`mc-1.21.11`** | Minecraft 1.21.11 |
| **`mc-1.21.10`** | Minecraft 1.21.9 – 1.21.10 |
| **`mc-1.21.8`** | Minecraft 1.21.5 – 1.21.8 |
| **`mc-1.21.4`** | Minecraft 1.21.2 – 1.21.4 |
| **`mc-1.21.1`** | Minecraft 1.21 – 1.21.1 |

`main` is an ancestor of all seven version branches, so its changes reach them by
`git merge` rather than by separate cherry-picks. Anything edited there lands
everywhere in one step: docs, release notes, the mod version, assets, and the Java files
that carry no version-specific API.

### What lives where

**Edit on `main`** — merged into every version branch:

- `README.md`, `SPEC.md`, `TODO.md`, `LICENSE`
- `gradle/mod.properties` — `mod_version`, `maven_group`, the store project ids
- `release/release-note-*.md`
- `common/src/main/resources/**` (assets, mixin configs)
- `common/.../RedfxMod.java`, `RedfxConfig.java`, `ParticleMixin.java`, `BloodSmokeAccessor.java`, `BloodParticle.java`
- `common/src/main/java/com/drinfonty/redfx/canvas/**`, `.../render/**`, `.../ClientCanvasStore.java`
- `common/src/test/**` (regression and unit test suites)
- the Fabric and NeoForge platform entry points
- `settings.gradle`, `gradlew`, `gradle/wrapper/**`

**Edit on the version branch** — never merged from `main`:

| File | Why it is per-branch |
| :--- | :--- |
| `gradle.properties` | Every Minecraft, Loom, NeoForge and Java version value |
| `build.gradle`, `common/build.gradle` | The Loom plugin id — `plugins {}` needs a literal, so it cannot be a property |
| `fabric/build.gradle`, `neoforge/build.gradle` | Shared between `mc-26.2` and `mc-26.1`; `mc-1.21.x` branches differ (`modImplementation`, explicit Mojang mappings, `remapJar`) |
| `RedfxConfigScreen.java`, `LivingEntityMixin.java`, `ClientLevelChunkMixin.java` | Real Minecraft API / mixin signature differences |
| `release/release-note-1.1.2.md` | Frozen per-branch history |

> **`main` still contains a copy of every per-branch file**, frozen at the commit the
> branch was cut from. They are deliberately never touched there: if `main` modified
> `gradle.properties`, every merge would conflict, and if it deleted it, every merge would
> hit a modify/delete conflict instead. Treat those copies as inert. If you find yourself
> editing one on `main`, you are on the wrong branch.

Build scripts avoid drifting by pushing every varying value into `gradle.properties` and
templating the metadata files through `processResources`, so `fabric.mod.json`,
`neoforge.mods.toml` and the mixin configs are byte-identical across branches.

### Making a change

**Version-agnostic change** (docs, release notes, version bump, shared Java):

```bash
git checkout main
# ...edit, commit...
for b in mc-26.2 mc-26.1 mc-1.21.11 mc-1.21.10 mc-1.21.8 mc-1.21.4 mc-1.21.1; do
  git checkout $b && git merge main
done
```

**Version-specific change** (anything touching Minecraft API):

1. Implement and test on the relevant version branch (e.g. `mc-26.2`).
2. Adapt or cherry-pick to other branches if applicable.
3. Re-derive rather than force anything touching mappings, mixins, or which task produces
   the published jar — that is genuinely not portable between branches.

If a `git merge main` conflicts, the file is in the wrong tier: either it should not be
on `main`, or the varying part belongs in `gradle.properties`. A conflict is the design
working, not a failure — the old cherry-pick flow let the same divergence pass silently.

### Before pushing

```bash
./gradlew clean build

# Fabric, on mc-26.2 / mc-26.1
./gradlew :fabric:runClient -PtestJar
# Fabric, on mc-1.21.x branches
./gradlew :fabric:runProdClient

# NeoForge, every branch
./gradlew :neoforge:runClient -PtestJar
```

See [Pre-Publish Verification](#2-pre-publish-verification) for what each one proves and
what to check in the log. A dev client reaching the main menu is **not** sufficient
evidence that a Fabric jar works on `mc-1.21.x`.
