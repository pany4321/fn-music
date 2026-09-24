# PRD: Car head unit P0 adaptations

## Background

A full project analysis (2026-09-24) identified four P0 gaps that block or severely degrade
sideloaded use on ordinary Android car head units (车机). The app is tuned for 1080p 16:9
Android TV; car screens are often ultra-wide or low-resolution, touch is the primary input,
and the network (phone hotspot / tunnels) is unstable. README already advertises car sideloading
but no adaptation exists.

## Requirements

1. **P0-2 Safe area**: root layout respects `WindowInsets.safeDrawing` (cutouts / system bars on
   car units). Zero visual change on TVs where insets are zero. Existing login `imePadding`
   behavior must not double-pad.
2. **P0-1 Responsive layout**: horizontal/vertical page margins scale down on narrow/short
   viewports; catalog grid column count adapts to available width; at 1920x1080 (and the
   equivalent ~960dp TV viewport) the rendering must remain identical to today
   (4 columns × 3 rows, 12 items per page, vertical scrolling disabled, fixed card sizes —
   see frontend contract).
   - API contract preserved: page size stays 12 (`size = 12` server pages).
   - On viewports where 12 items in 3 rows do not fit vertically, vertical scrolling becomes
     enabled as a fallback so no item is unreachable.
3. **P0-3 Touch targets**: every actionable control reachable by touch is at least 48dp in both
   dimensions: settings choice/action buttons and checkbox rows (34dp → 48dp), queue row delete
   (40dp → 48dp), catalog pager arrows (42dp → 48dp), segmented library tabs (43dp → 48dp).
   TV focus behavior unchanged.

**Out of scope (user decision, 2026-09-24)**: P0-4 audio/file caching. The backend contract
explicitly forbids `SimpleCache`/`CacheDataSource` in `PlaybackService`; do not add any
persistent audio cache in this task.

## Constraints

- minSdk 23, targetSdk 36, Kotlin/Compose for TV, no new runtime permissions.
- Do not regress the documented TV contracts in
  `.trellis/spec/frontend/android-tv-interaction.md` at 1920x1080 / 1280x720.
- Login remains one TV-first form for all landscape devices (no phone tree).
- FLAC decoder artifact, signing, and CI gates untouched.

## Acceptance criteria

- [ ] `:app` and `:core:playback` compile; existing unit tests pass (updated where the contract
      legitimately changed).
- [ ] At 960dp-wide viewport: margins/grids identical to current build.
- [ ] At narrow (e.g. 640dp) / short (e.g. 480dp tall) viewports: home, catalog, settings, and
      login fit without clipped content; all 12 catalog items reachable.
- [ ] All touch-actionable controls ≥ 48dp min bounds.
