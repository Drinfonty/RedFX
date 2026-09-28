# Multi-Version Workflow Rule

- **Step 1**: Prototype, iterate, and verify changes on `mc-26.2` first.
- **Step 2**: Commit verified shared changes to `main`. Never commit version-divergent files to `main`.
- **Step 3**: Merge `main` into all version branches using `git merge main` (NEVER cherry-pick).
  - Version branches: `mc-26.3`, `mc-26.2`, `mc-26.1`, `mc-1.21.11`, `mc-1.21.10`, `mc-1.21.8`, `mc-1.21.4`, `mc-1.21.1`.
- **Step 4**: Run `./gradlew check` across all branches to verify everything compiles and tests pass.
