# Release Notes - Version 1.1.3

A small fix release. No gameplay or visual changes.

### Fixes
- **No more console spam during combat** ([#1](https://github.com/Drinfonty/RedFX/issues/1)):
  every hit and every entity death wrote a line to the game log, for example
  `Spawning 99 blood particles (totalMultiplier=5.2, weaponScale=1.3) for entity ...`.
  During normal play this filled the log continuously. These were development
  diagnostics that were never meant to be enabled in a release build.

  They are now off in released builds and can only be switched on from a development
  environment, so there is nothing you need to configure and no setting to find. Beyond
  the quieter log, the messages are no longer assembled at all while fighting, so the
  work they were doing on every hit is gone too.

Thanks to the reporter of #1 for the clear write-up.
