# Implementation Plan

1. [x] D: normalizeWide + normalizer/isFnId integration + login message
2. [x] C: AppPreferences device flag + SettingsScreen row + MainActivity branch
3. [x] A: rehomeConnection + rebaseApiUri + service resolver + ConfigureAuth api-base +
       isNetworkRetryable + PlayerStatusRetry.Playback + UI wiring + controller retry
4. [x] B: resumption contracts + service override + app source + persistedPlaybackAuth
5. [x] Tests: normalizer/isFnId wide chars, rebaseApiUri, playerStatus retry codes,
       AppPreferences flag, resumption source, SessionRepository rehome
6. [x] Spec sync (backend contracts) + full gates

Review gates: no state changes on failed re-home; resumption must not require the UI to have
run; the background-exit default stays the documented kill-path.


## Result (2026-09-24)

- All gates green: compile (app/playback/data), app unit tests (105+), playback unit tests
  (incl. new PlaybackRehomingTest), data tests for touched areas (normalizer wide-chars,
  isFnId, rehome, persistedPlaybackAuth, AppPreferences flag), both lints.
- Media3 1.10.1 API notes: `MediaItemsWithStartPosition` is nested in
  `MediaSession` (session package); `onPlaybackResumption` lives on `MediaSession.Callback`
  (two overloads, both overridden); `ResolvingDataSource.Resolver` is a nested class;
  there is no `ERROR_CODE_IO_NETWORK_FAILURE` (codes are 2000/2001/2002 + 1003 timeout).
- Pre-existing Windows-only failure remains: `AppDatabaseMigrationTest` (Robolectric/Room
  temp-path), verified failing on clean main.
- Device verification pending (user tests on physical car units): re-home retry while
  switching Wi-Fi->hotspot, media-button resume after process death, background-exit toggle.
