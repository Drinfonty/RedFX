# Release Notes - Version 1.1.2

A maintenance release. No gameplay or visual changes.

### Fixes
- **Mod version reported as `unspecified`**: the version placeholder in
  `fabric.mod.json` was never substituted, so Fabric Loader could not parse the
  mod version and warned about it. Mod menus now show 1.1.2 correctly.
- **License is now included in the jar**: the `LICENSE` file was silently
  omitted from every published build.
- **Corrected mod metadata**: the author, description and homepage were still
  template placeholders — the homepage pointed at fabricmc.net rather than the
  mod page.
