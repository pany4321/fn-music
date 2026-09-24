# Implementation Plan

1. [x] `NetworkRestoreMonitor.kt` (interface + Android impl + guard fn)
2. [x] `AuthenticatedAppCoordinator` wiring + `AppContainer` construction
3. [x] Manifest `ACCESS_NETWORK_STATE`
4. [x] `MainActivity` POST_NOTIFICATIONS request
5. [x] `PlaybackService` wake mode
6. [x] `NetworkRestoreMonitorTest` (guard states + Robolectric registration smoke)
7. [x] `PlaybackServiceConfigurationTest` symlink skip fix
8. [x] Spec sync (backend contract: recovery-on-network line, wake mode line, notification line)
9. [x] Gates: compile, app unit tests, playback unit tests, lint

Review gates: guard must be false for SignedIn/SignedOut/Loading; permission request must not
block or gate any login/playback path.


## Result (2026-09-24)

- All gates green: compile (app+playback), `:app:testSideloadDebugUnitTest`,
  `:core:playback:testDebugUnitTest` (symlink test now passes on Windows), lints, model tests.
- Bonus root-cause fix: the symlink test failure was NOT environment-only —
  `File.getCanonicalFile()` never resolves symlinks on Windows, so the legacy-cache
  symlink branch could not fire there and `deleteRecursively` traversed the target.
  `deleteLegacyAudioCache` now detects symlinks via NIO `BasicFileAttributes`
  (`NOFOLLOW_LINKS`) and unlinks with `Files.deleteIfExists`; spec updated accordingly.
- Wake mode has no unit assertion (no public getter on ExoPlayer); contract recorded in spec.
- P1-6 (day theme) and P1-9 (font-scale elasticity) deferred as design-level work.
