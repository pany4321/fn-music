# Design

- `NetworkRestoreMonitor.kt` (app module): `fun interface NetworkRestoreMonitor { register(onNetworkAvailable) }`
  + `AndroidNetworkRestoreMonitor` wrapping `ConnectivityManager.registerNetworkCallback` with a
  `NET_CAPABILITY_INTERNET` request (no VALIDATED so captive portals still trigger a probe attempt;
  failure just re-enters backoff). Pure guard `shouldRetrySessionOnNetworkAvailable(state) == state is Recovering`.
- Coordinator: constructor gains `networkRestoreMonitor`; `start()` registers once; the callback
  launches on `applicationScope` (Main.immediate) and calls the existing idempotent
  `retrySessionRestore()` (cancelAndJoin + relaunch). No unregister — process-lifetime component.
  Callbacks fire immediately for already-active networks; the `Recovering` guard makes those no-ops.
- `AppContainer` constructs `AndroidNetworkRestoreMonitor(application)`.
- MainActivity: `registerForActivityResult(RequestPermission)` property; request in `onCreate`
  when API >= 33 and not granted; result ignored (fire-and-forget).
- PlaybackService: add `setWakeMode(C.WAKE_MODE_LOCAL)` next to the existing audio-attribute call.
  No unit assertion is possible without ExoPlayerImpl reflection; the contract line in the spec is
  the durable record.
- Manifest: `ACCESS_NETWORK_STATE` only (POST_NOTIFICATIONS already declared).
- Test fix: `PlaybackServiceConfigurationTest` symlink test catches `IOException` (Windows
  privilege error is a FileSystemException, not UnsupportedOperationException) via
  `assumeNoException`, preserving real execution on Linux CI.

Rollback: revert the five touched source files + manifest line; no schema/service behavior change.
