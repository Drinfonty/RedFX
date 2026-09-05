# Release Notes - Version 2.0.0

A major visual and performance overhaul introducing seamless block splatters, gradual dissolving effects, and independent sizing options.

### What's New

- **Seamless Block Splatters**:
  Landed blood splatters are now rendered directly onto blocks rather than lingering as floating particles in the world:
  - Splatters now naturally flow across block boundaries without getting cut off at block edges.
  - Fixes flickering, z-fighting, and camera clipping through blood on the ground and walls.
  - Much better performance during chaotic fights—blood splatters no longer count toward particle limits or cause FPS drops.
  - Splatters feature natural, irregular edges so they look organic rather than blocky or square.

- **Gradual Splatter Dissolving**:
  Blood puddles no longer disappear suddenly all at once when their timer expires. Splatters now slowly erode away pixel-by-pixel from the edges inward, fading out naturally over time.

- **Separate Droplet & Splatter Size Sliders**:
  You can now tune the size of flying blood drops and landed puddles separately in the mod config:
  - **Drop Size**: Adjusts how large airborne blood particles appear.
  - **Splat Size**: Adjusts the footprint of blood splatters on surfaces.

- **New & Updated Entity Blood Colors**:
  - **Endermites**: Now bleed Ender Purple matching Endermen and the Ender Dragon.
  - **All Skeleton Variants**: Skeletons, Strays, Wither Skeletons, Bogged, Parched, and Skeleton Horses now bleed bone-marrow brown instead of white.
  - **Sulfur Cubes**: Now bleed white sulfur goo.

- **Refreshed Configuration Screen**:
  The in-game configuration menu has been cleaned up with a neat, balanced layout and obsolete options removed, making it effortless to customize your combat effects.
