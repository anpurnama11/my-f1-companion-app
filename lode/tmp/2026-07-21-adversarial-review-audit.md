# Adversarial review audit — uncommitted homepage section changes

**Scope:** Uncommitted changes adding the Homepage §1 countdown card, `SectionUiState`, OpenF1-derived race-weekend schedule, circuit image, and related VM/Screen/tests.

**Method:** Pinned diff with `git diff HEAD` + listed untracked files; reviewer read `lode/specs/f1app.md` and supporting Lode docs. Reviewed by `adversarial-reviewer` agent.

**Result:** 10 findings. After re-reading the updated code, **#1 and #2 are closed**; the remaining 8 are still open.

| # | Severity | File / Line | Finding | Verdict | Status |
|---|----------|-------------|---------|---------|--------|
| 1 | Critical | `HomepageViewModel.kt:173` `loadRaceDerivedSections()`; `HomepageScreen.kt:348-355` `RACE_DURATION` gate | Race ends → card stays “LIVE” forever; derived sections not reactive to `nextRace` advances. | **Fixed** — VM now calls `loadRaceDerivedSections()` after every `loadNextRace()`; Screen gates `live` to `now < race.start + 3.hours` and shows “RACE COMPLETE” after. | Closed |
| 2 | Critical | `build.gradle.kts:81`, `libs.versions.toml:52`, `HomepageScreen.kt:421` | `coil-compose` cannot decode SVG; OpenF1 circuit images fail to render. | **False positive** — OpenF1 returns PNGs (`.../Track%20icons%204x3/*.png`), and Coil’s default decoder handles PNG. Residual risk is broken 404 URLs, not format. | Closed |
| 3 | Important | `HomepageViewModel.kt:149` `.onStart { warmUp() }` + `SharingStarted.WhileSubscribed(5_000)`; `line 177` `favoritesFlow.onEach { }.launchIn(viewModelScope)` | Re-subscription past the 5 s grace re-fires `warmUp()` and leaks favorites collectors in `viewModelScope`. | Confirmed | Open |
| 4 | Important | `HomepageViewModel.kt:246-264` `loadRaceDerivedSections()` / `loadTopSpeed()` | If `nextRace` fails on first launch, derived atoms are left at `Loading` because `loadRaceDerivedSections()` reads `nextRace.value as? Content` and returns early without setting an error state. | Confirmed | Open |
| 5 | Important | `GetCircuitImageUseCaseTest.kt` test client lacks `expectSuccess = true`; `HttpClientFactory.kt:50` production sets it | 4xx test passes through the generic `catch (Exception)` because the test client does not throw `ClientRequestException`. The dedicated 4xx branch is not actually exercised. | Confirmed | Open |
| 6 | Suggestion | `HomepageViewModel.kt:173` `warmUp()` + `line 188` `refresh()` | `refresh()` dispatched while `warmUp()` is in flight can race to write the same derived atoms; stale/failed results can overwrite fresh ones. | Confirmed | Open |
| 7 | Suggestion | `HomepageScreen.kt:319-326` 30 s tick; `HomepageViewModel` has no clock-driven refresh | The countdown ticks every 30 s only to update `now`; nothing re-fetches `nextRace` when the calendar rolls over, so the card can stay stale until the user pulls to refresh. | Confirmed | Open |
| 8 | FYI | `GetRaceWeekendScheduleUseCase.kt:80` `Instant.parse(it)` | If OpenF1 ever drops the `+00:00` offset from `date_start`, the session is silently dropped. Current data always includes the offset. | Confirmed | Open |
| 9 | FYI | `HomepageScreen.kt:461` `countdownTo` | `if (ms <= 0) return "LIVE"` is dead code — `countdownTo` is only called when `next != null`, and `next` is always a future session. | Confirmed | Open |
| 10 | FYI | `F1Api.kt:132-134` `F1API_TO_OPENF1_COUNTRY` | Country-name fallback has only one entry (`Great Britain` → `United Kingdom`). Any other f1api.dev/OpenF1 mismatch yields `Success(null)` and an empty card without signaling the mismatch. | Confirmed | Open |

## Notes

- **#1 closure:** The initial adversarial reviewer flagged the non-reactive `loadWeekendSchedule()` / `loadCircuitImage()` calls and the `live = last session <= now` fallback. The updated code addresses both: `loadRaceDerivedSections()` is invoked after every `loadNextRace()`, and the UI uses `raceSession?.takeIf { now < it.start.plus(RACE_DURATION) }` with `RACE_DURATION = 3.hours`.
- **#2 closure:** Verified against OpenF1 documentation examples; `circuit_image` URLs point to `media.formula1.com` PNG assets. The missing `coil-svg` dependency is not required today.
- **Remaining work:** The 8 open findings are still valid against the current code. #3 and #4 are the most likely to cause visible misbehavior (leaky collectors + stuck loading state). #5 is a test-fidelity gap.
