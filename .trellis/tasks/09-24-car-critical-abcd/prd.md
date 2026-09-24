# PRD: Car critical fixes (A/B/C/D)

Four findings from the 2026-09-24 critical review of car-head-unit behavior:

- **A — dead-end playback failure**: leaving home Wi-Fi strands the session (connection
  fixed at login) and the player shows a retry-less error. Required: session re-discovery
  (`rehomeConnection`), service-side URI rebasing for stale queue items, and a retry action
  for network-class playback failures.
- **B — dead media buttons after process death**: `PlaybackService` never implemented
  `onPlaybackResumption`, so steering-wheel play after ACC restart does nothing. Required:
  a resumption provider that serves the persisted snapshot plus persisted auth headers.
- **C — double-back kills the process**: TV exit habit is hostile in cars. Required: an
  opt-in device setting "返回键后台运行（车载）"; when on, confirmed Home back backgrounds
  the task (playback continues) instead of exiting.
- **D — full-width character trap**: CJK keyboards turn `192.168.1.10` into
  `192。168。1。10`; normalization rejects it with no user-visible error. Required:
  wide-char normalization at the normalizer boundary and an explicit invalid-address
  message on the login form.

## Acceptance criteria

- [ ] `SessionRepository.rehomeConnection()` re-runs discovery + reconnects while SignedIn,
      returning false without disturbing the current binding when unreachable (unit test).
- [ ] Network-class Media3 failures (2000-2003) surface a player retry that re-homes then
      prepares/plays; `playerStatus` covered by unit tests for retryable and non-retryable codes.
- [ ] The service rebases queued stream URIs onto the re-homed api base (pure-function unit
      tests); ConfigureAuth carries the api base, ClearAuth clears it.
- [ ] Media-button resume works after process death via `onPlaybackResumption` using the last
      saved namespace's snapshot + persisted auth (app-source unit test).
- [ ] The background-exit setting persists device-globally and MainActivity backgrounds the
      task when enabled (round-trip test).
- [ ] Full-width addresses normalize to valid input (normalizer + isFnId tests) and the login
      form shows `NAS 地址格式不正确` for invalid addresses.
- [ ] All gates green: app/playback/data unit tests, lint.
