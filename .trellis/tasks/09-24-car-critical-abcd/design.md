# Design

- **Re-home (A)**: `SessionRepository.rehomeConnection()` re-runs `restoreAttempt` for the
  active profile (full FNID probe order) and returns false on any failure without clearing the
  current binding; success re-publishes SignedIn, so the coordinator collector re-configures
  playback. The service wraps its HTTP factory in `ResolvingDataSource` with
  `rebaseApiUri(uri, currentBase)`: same path+query, scheme/authority from the newest base —
  stale absolute queue URIs open against the re-homed origin with zero timeline surgery.
  `ConfigureAuth` now carries `ApiBase`; `ClearAuth` clears it. `PlaybackFailure.isNetworkRetryable`
  (codes 2000-2003) drives a new `PlayerStatusRetry.Playback` whose action runs
  coordinator.retryPlaybackConnection() = rehome + configure + prepare/play.
- **Resumption (B)**: core:playback defines `PlaybackResumptionProvider`/`PlaybackResumptionData`
  and `PlaybackServiceDependencies`; `TvMusicApplication` implements the latter with an app-layer
  source: prefs `playback_resumption.last_namespace` (written by `LocalPlaybackSessionStore.save`)
  + LocalStore snapshot + `SessionRepository.persistedPlaybackAuth()` (remembered token/access
  code/relay mode, no state change). The service decodes the snapshot and returns
  `MediaItemsWithStartPosition(items, index, position)` after installing auth headers.
- **Background exit (C)**: device-scoped `AppPreferences.backgroundBackExit` StateFlow (deliberately
  outside the account-scoped `AppPreferencesState` so namespace binds never reset it);
  MainActivity exit callback moves the task to back when enabled, preserving the existing
  documented kill-path as default.
- **Wide chars (D)**: `ServerUrlNormalizer.normalizeWide` maps `。．｡：／` and full-width
  alphanumerics to ASCII inside `normalize`/`editableInput`/`ConnectionResolver.isFnId`/`resolve`;
  the login status slot gains a Coral `NAS 地址格式不正确` state.

Rollback: revert the touched files; snapshot schema, Room, and CI untouched.
