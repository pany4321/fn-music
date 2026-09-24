# Implementation Plan

Ordered checklist (all in `:app` module):

1. [x] `ui/AdaptiveLayout.kt` — `AdaptiveWindow`, `LocalAdaptiveWindow`, `adaptiveWindowFor()`.
2. [x] `ui/FnMusicApp.kt` — root `BoxWithConstraints` + `LocalAdaptiveWindow` provider;
       inner `windowInsetsPadding(WindowInsets.safeDrawing)`; adaptive outer margins for
       BrandLoading / SessionRecoveryScreen; history dialog `widthIn(max = 600.dp)`.
3. [x] `ui/AuthenticatedApp.kt` — BrowseHome / BrowseMy / PagedCatalogPage / GridPage /
       DetailTrackCollection margins; PagedCatalogPage adaptive columns + scroll fallback;
       GridPage `GridCells.Adaptive`; ArtistAlbumGrid fitted columns; `CatalogPageArrowButton`
       42 → 48dp; `LibraryTab` 78×43 → 82×48 and container 162×49 → 170×54.
4. [x] `ui/SettingsScreen.kt` — adaptive margin; choice/action/checkbox heights 34 → 48dp.
5. [x] `ui/PlayerScreen.kt` — queue row delete button 40 → 48dp.
6. [x] Unit tests: new `AdaptiveWindowTest` (tier boundaries, column fitting math incl. the
       960dp-viewport == 4-column invariant); update any broken tests.

## Validation

```sh
./gradlew :app:compileSideloadDebugKotlin
./gradlew :app:testSideloadDebugUnitTest
./gradlew :core:playback:testDebugUnitTest :core:data:testDebugUnitTest :core:model:test
./gradlew :app:lintSideloadDebug
```

Flake risk: none expected — no service/data changes. Device tests (screenshot/instrumentation)
are not runnable in this environment; flag for the next connected-device run.

## Review gates

- After step 3, re-read the changed hunks for focus-graph safety (no focus modifier reordering).
- After step 6, confirm 960dp-width viewport produces identical margins/column counts to `main`.

## Result (2026-09-24)

- All gates green on this machine: `:app:compileSideloadDebugKotlin`,
  `:app:testSideloadDebugUnitTest` (98 tests, incl. 8 new `AdaptiveLayoutTest`),
  `:app:lintSideloadDebug`, `:app:lintStoreDebug`, `:core:model:test`.
- Pre-existing Windows-environment failures (verified failing on clean `main` via stash):
  `core:data` `AppDatabaseMigrationTest` (Robolectric/Room temp-path mismatch) and
  `core:playback` `PlaybackServiceConfigurationTest#legacy cleanup does not follow a media
  symlink` (symlink creation on Windows). Not caused by this task.
- Connected-device screenshot/focus tests (player 1920x1080 + 1280x720, settings checkbox,
  login) could not run in this environment — run them before release.
- Spec updated: `.trellis/spec/frontend/android-tv-interaction.md` (adaptive window, insets,
  fitted grid columns, 48dp touch bounds, narrow-viewport scroll fallback).
