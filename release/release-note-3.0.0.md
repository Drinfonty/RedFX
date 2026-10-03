# Release Notes - Version 3.0.0

A major milestone release introducing **natural edge and wall dripping physics**, **advanced multi-block and cavity splatter projection**, a completely redesigned **interactive color picker & entity customization GUI**, and extensive rendering performance and fidelity improvements.

### Highlights

- **Natural Edge & Wall Dripping Physics**:
  - **Ledge & Cliff Wrapping**: Blood puddles near block edges now naturally wrap over corners and form creeping vertical drip lines down adjacent walls and ledges.
  - **Organic Drip Trails**: Variable-rate teardrop beads and procedural growth stages create organic, tapering drip lines.
  - **Falling Drips & Ground Splashes**: Drips reaching the bottom of a block over open air generate falling drip particles tinted to match the entity's exact blood color.
  - **Splash Landings & Dust**: Falling drips trigger custom splash effects (`BloodDripSplash`) and stamp tiny impact splatters and dust upon hitting the ground below.
  - **Stacked Block Continuity**: Edge drips flow seamlessly between vertically stacked blocks and stop cleanly when meeting non-permeable obstacles.

- **Advanced Multi-Block & Cavity Splatter Projection**:
  - **Anvils & Lecterns**: Full support for complex and multi-box block shapes. Blood wraps across anvil faces, bases, steps, and lectern reading slopes.
  - **Stairs & Risers**: Decals now wrap seamlessly across treads, risers, inner corners, and interior cavities.
  - **Cavity Blocks & Containers**: Splatters and edge drips properly project into the interiors and rim walls of cauldrons, composters, and hoppers.
  - **Partial-Footprint Blocks**: Blocks like lanterns, torches, and decorative props no longer mask or punch holes in floor decals beneath them.
  - **Leaves & Foliage Refinements**: Restored natural blood splattering, decal stamping, and edge drips on tree leaves (`LeavesBlock`), while non-solid foliage plants (bushes, saplings, vines, and tall grass) remain ignored to prevent unnatural floating decals.

- **Interactive Color Picker & Entity Configuration GUI**:
  - **In-Game RGB & Palette Screen**: Brand-new interactive color customization interface featuring direct RGB sliders, palette swatches, and hex code text inputs.
  - **Live Preview & Preset Matching**: Live color preview swatches with alpha support and instant preset matching against existing entity colors.
  - **Per-Entity Customization**: Fully customizable blood colors per entity type with autocomplete search and per-entity blood toggles.
  - **Blood Opacity Slider**: New opacity slider to smoothly adjust decal translucency to your preference.
  - **Soft Edge Toggle**: Configurable translucent edge toggle for crisp or diffused blood decal borders.

- **Performance & Architectural Enhancements**:
  - **Dynamic Face Plane Offsets**: Decal meshing uses generic VoxelShape hitbox evaluation and dynamic face-plane offsets, eliminating decal clipping, z-fighting, and floating gaps on inset blocks.
  - **Automated Smoke Test Arena**: Expanded headless combat smoke test suite with complex block geometries (anvils, snow layers, snow blocks, and foliage) to continuously verify particle flight, decal stamping, and edge drips.
  - **Multi-Version Parity**: Verified and supported across all 8 target Minecraft versions: `26.3`, `26.2`, `26.1`, `1.21.11`, `1.21.10`, `1.21.8`, `1.21.4`, and `1.21.1` on Fabric and NeoForge.
