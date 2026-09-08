# Release Notes - Version 2.0.1

A critical bugfix release resolving client rendering crashes when blocks with blood splatters are destroyed by server-side actions (such as Wither or TNT explosions).

### Fixes

- **Fix Render State Crash with Sodium & Explosions**:
  - Fixed `IllegalStateException: Tried to access render state from outside the main render thread!` when entities tick and modify or destroy blocks on the server thread (e.g. Wither spawning/explosions, TNT, Creepers) while blood splatters are on those blocks.
  - Ensured block removal listeners only trigger for client-side world changes and made chunk dirty-marking thread-safe by dispatching to the client render thread.
