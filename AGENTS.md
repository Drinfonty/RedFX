# RedFX Agent & Development Guidelines

## Multi-Version Branch & Development Workflow
Always follow this strict workflow for any feature, refactor, bugfix, or test:

1. **Prototype on `mc-26.2`**:
   - Always implement, iterate, and verify changes on `mc-26.2` first.
   - Run in-game testing or smoke test on `mc-26.2` to confirm behavior.

2. **Commit to `main`**:
   - `main` is the shared, canonical trunk containing version-agnostic code.
   - Never commit version-divergent files to `main`.
   - Commit the verified shared changes directly to `main`.

3. **Merge `main` to ALL Version Branches**:
   - **NEVER cherry-pick** commits from `main` to version branches. Always use `git merge main`.
   - The version branches are:
     - `mc-26.3`
     - `mc-26.2` (fast-forward to `main`)
     - `mc-26.1`
     - `mc-1.21.11`
     - `mc-1.21.10`
     - `mc-1.21.8`
     - `mc-1.21.4`
     - `mc-1.21.1`
   - When merging `main` into a version branch:
     - Retain branch-specific adaptations (`gradle.properties`, Loom configurations, version-specific model renderers, and GUI text drawing methods).
     - Integrate all shared feature changes from `main`.

4. **Verify**:
   - Run `./gradlew check` across all branches to ensure clean builds and test passes.
