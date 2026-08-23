# Release Notes - Version 1.1.4

Fixes Minecraft 1.21.x support. No gameplay or visual changes.

### Fixes

- **1.21.x support is now correct** ([#2](https://github.com/Drinfonty/RedFX/issues/2)):
  the 1.21 downloads were listed for every version from 1.21 to 1.21.11, but the mod
  was only ever built against 1.21.11. On NeoForge it refused to load, because it
  required NeoForge 21.11.x, which only exists for 1.21.11. On Fabric it would install
  and then fail at runtime, which was worse.

  There are now three separate 1.21 downloads, each built and tested against the
  versions it lists:

  | Download | Minecraft |
  | :--- | :--- |
  | `1.1.4-mc1.21.11` | 1.21.11 |
  | `1.1.4-mc1.21.10` | 1.21.9 - 1.21.10 |
  | `1.1.4-mc1.21.8`  | 1.21 - 1.21.8 |

  Pick the one matching your version. Minecraft renamed parts of its code inside the
  1.21 line, so a single build genuinely cannot cover all of it.

- **Correct dependency ranges on NeoForge**: the Minecraft dependency was written in
  Fabric's version format, which NeoForge cannot parse.

### If you are on 26.1 or 26.2

Nothing changes for you. 1.1.4 is identical in behaviour to 1.1.3; it is published for
all versions only to keep the numbering aligned.

Thanks to **En04ik** for reporting the 1.21.x problem.
