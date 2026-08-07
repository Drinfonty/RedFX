# Release Notes - Version 1.1.2

This release fixes the mod failing to load on production Minecraft clients.

### Fixes
- **Fixed crash / mixins not applying on production clients**: The mixin classes live in the shared `:common` module, but Loom's legacy mixin annotation processor writes its `@Shadow` member mappings into `:common`'s build cache while `:fabric:remapJar` looks for them in its own. The mappings were silently dropped, so `@Shadow` members such as `Particle.remove()` shipped with Mojang names inside an otherwise intermediary-remapped jar and Mixin threw `InvalidMixinException: @Shadow method remove()V ... was not located in the target class net.minecraft.class_703`. Both Loom modules now use Loom's hard mixin remapping (`useLegacyMixinAp = false`), which reads the `@Mixin` annotations straight from the bytecode and therefore works regardless of which module compiled the class.
- **Removed refmaps**: With hard remapping the mixin references are rewritten in place, so `redfx.mixins.json` / `redfx.client.mixins.json` no longer declare a refmap and none is packaged. This also removes an intermediary-namespace refmap that had no business being in the NeoForge jar, which runs against Mojang mappings.
- **Fixed mod version reported as `unspecified`**: `project.version` was never set, so `fabric.mod.json`'s `${version}` placeholder expanded to `unspecified` and Fabric Loader warned that the version could not be parsed as SemVer. The version is now set from `mod_version` for all subprojects.
