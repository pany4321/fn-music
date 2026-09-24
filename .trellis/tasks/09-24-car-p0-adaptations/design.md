# Design: Car head unit P0 adaptations

Frontend-only task (`:app` module). No data/playback changes; audio caching is out of scope
(backend contract forbids it; user confirmed 2026-09-24).

## 1. Adaptive window primitive

New file `app/src/main/java/com/fnmusic/tv/ui/AdaptiveLayout.kt`:

```kotlin
data class AdaptiveWindow(
    val horizontalMargin: Dp,   // 64 / 40 / 24 dp by width tier
    val compact: Boolean,       // width < 880.dp
    val shortHeight: Boolean,   // height < 560.dp
)
val LocalAdaptiveWindow = staticCompositionLocalOf { AdaptiveWindow(64.dp, false, false) }
fun adaptiveWindowFor(maxWidth: Dp, maxHeight: Dp): AdaptiveWindow
```

Width tiers: `>= 880dp → 64dp` (current TV value; a 960dp TV viewport keeps today's layout),
`600–880dp → 40dp`, `< 600dp → 24dp`.

Provided once from the `FnMusicApp` root via `BoxWithConstraints`; consumed through
`LocalAdaptiveWindow` so page signatures stay unchanged. Pages combine it with their own
historical margin, e.g. settings keeps `min(window.horizontalMargin, 40.dp)`, detail pages
`min(window.horizontalMargin, 42.dp)`, home/catalog/login use `window.horizontalMargin`.
Vertical margins shrink only when `shortHeight` (e.g. catalog 44 → 16dp, home 24 → 12dp).

## 2. Safe area (P0-2)

`FnMusicApp` root: outer `Box(fillMaxSize().background(FnColors.Background))` keeps edge-to-edge
background; an inner `Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))`
wraps all session content. On TVs insets are zero → identical rendering. IME is part of
`safeDrawing`, and no existing composable applies `imePadding`, so the touch-keyboard case gets
exactly one padding with no double-count. `UpdateDialogHost` stays above the inset box (dialogs
manage their own windows).

## 3. Grid / page adaptation (P0-1)

- `PagedCatalogPage` (All Artists / All Albums): replace fixed `columns = 4` /
  `horizontalPadding = 64.dp` / `verticalPadding = 44.dp` with
  `columns = min(4, fittedColumns)` where `fittedColumns = floor((maxWidth - 2*margin + 14) / 184)`
  (tile min 170dp + 14dp spacing), clamped to `>= 2`. At the 960dp TV viewport the result is
  exactly 4 columns / 3 rows / no scrolling (contract preserved). When `columns < 4` the grid
  cannot show 12 items in 3 fixed rows, so `userScrollEnabled` becomes `true`, the grid takes
  `Modifier.weight(1f)` instead of the fixed `gridHeight`, and `LazyVerticalGrid` focus-following
  scrolling keeps every item D-pad/touch reachable. Page size stays `FULL_CATALOG_PAGE_SIZE` (12);
  focus-neighbor math already parameterizes on `columns`.
- `GridPage` (All Playlists): `GridCells.Fixed(4)` → `GridCells.Adaptive(minSize = 170.dp)`.
  At 832dp of content width this yields exactly 4 columns; already vertically scrollable.
- `ArtistAlbumGrid` (artist detail): `GridCells.Fixed(3)` → fixed count computed as
  `min(3, max(1, fitted))` so the TV keeps 3 columns.
- Login/recovery/loading/history dialog keep one form tree (contract); only the outer margin
  becomes adaptive, and the history dialog width becomes `widthIn(max = 600.dp).fillMaxWidth(0.9f)`
  so narrow screens still show it.

## 4. Touch targets (P0-3)

Direct size bumps (visual size = touch size, keeps single focus/touch owner per control):

| Control | Before | After |
| --- | --- | --- |
| `SettingsChoiceButton` / `SettingsActionButton` | height 34 | height 48 |
| `SettingsCheckbox` row | height 34 | height 48 |
| Queue row delete button | size 40 | size 48 |
| `CatalogPageArrowButton` | size 42 | size 48 |
| `LibraryTab` + segmented container | 78×43 in 162×49 | 82×48 in 170×54 |

Player transport/side actions already own 48dp outer targets; `NowPlayingPill` is
spec-pinned (186×42dp + fontScale growth) and left unchanged — its 186dp width makes it an
easy target. Login controls are already ≥ 48dp.

## 5. Compatibility & risks

- 1920x1080 / 1280x720 TV: `AdaptiveWindow(64dp, compact=false)` reproduces current margins;
  `min(4, fitted)` reproduces 4 columns; insets are zero. Only the P0-3 size bumps change TV
  pixels (intended).
- Focus contracts (frontend spec) are untouched: no focus graph edits, no `focusable()` changes,
  no pager logic changes; column-dependent Down routing already reads the `columns` variable.
- Device tests that assert fixed bounds: screenshot test pins pill width 372px (unchanged);
  settings checkbox device test asserts behavior, not height.
- `BoxWithConstraints` at the root adds one constraints read; no measurable cost.

Rollback: single revert of the `:app` changes; no schema, service, or dependency changes.
