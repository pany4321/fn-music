# PRD: Car P1 runtime adaptations

Three P1 findings from the 2026-09-24 car-head-unit analysis that block reliable in-car use:

1. **Network-aware session recovery (P1-5)**: when the recovering startup session's network
   changes (car leaves home Wi-Fi, hotspot reconnects, tunnel exit), recovery must retry
   immediately on the next usable network instead of waiting out the remaining backoff delay.
   Signed-in or signed-out states must be unaffected.
2. **POST_NOTIFICATIONS runtime request (P1-7)**: on Android 13+ the media notification is
   hidden without the runtime grant; request it once at startup, non-blocking, denial keeps
   the app fully functional.
3. **Player wake mode (P1-8)**: the player must hold a local wake lock
   (`C.WAKE_MODE_LOCAL`) so screen-off / CPU-throttled car units keep playing without stutter.

Out of scope: day theme (P1-6) and font-scale/container elasticity (P1-9) — both are
design-level palette/typography work, tracked separately.

## Acceptance criteria

- [ ] A `NetworkRestoreMonitor` seam exists; the coordinator retries recovery only while the
      session state is `Recovering`, covered by unit tests for every session state.
- [ ] `ACCESS_NETWORK_STATE` added to the manifest (normal permission).
- [ ] MainActivity requests `POST_NOTIFICATIONS` on API 33+ only, via the ActivityResult API.
- [ ] PlaybackService sets `C.WAKE_MODE_LOCAL` on the player.
- [ ] The Windows-only `Files.createSymbolicLink` failure in
      `PlaybackServiceConfigurationTest` skips gracefully (`IOException` assumption) so the
      local gate is green; the real test still runs on Linux CI.
- [ ] `:app` compiles; app/playback unit tests and lint pass.
