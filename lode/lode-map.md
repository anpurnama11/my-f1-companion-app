# Lode map — F1app

Hierarchical index of all lode files. Update when files are added/removed.

```
lode/
  summary.md                         # living one-paragraph snapshot
  terminology.md                     # domain + project glossary
  practices.md                       # patterns, conventions, invariants
  lode-map.md                        # this file
  architecture/
    architecture.md                  # module, DI, layers, tech choices (ticket 01) [BUILT]
  core/
    navigation.md                    # Navigation 3 4-tab shell shape (ticket 01) [BUILT]
    network.md                       # Ktor HttpClient factory + F1Api extensions
  design-system/
    theme.md                         # dark-only M3 theme + Circuits/Tyres/Shapes/Spacing (ticket 02)
    icons.md                         # launcher adaptive icon + 4 tab bar icons (mipmap/drawable rules)
  leaderboard/
    summary.md                       # standings tab + driver/team detail joins (ticket 04)
  my-team/
    summary.md                       # 3-slot favorites management + atomic uniqueness (ticket 05)
  news/
    summary.md                       # parked — RSS news tab design (post-v1; replaces My Team per ticket 25)
  specs/
    f1app.md                         # full F1app design contract + current build status
  testing/
    scope.md                         # testing scope + libs + placement + KMP portability rule (ticket 14)
  decisions/
    0001-openf1-qualydate-not-racedate.md  # OpenF1 join uses qualyDate, not raceDate (correction of ticket 11 research)
    0002-sectionuistate-is-vm-to-ui-transport.md  # SectionUiState is VM→UI transport; Outcome stops at the VM
    0003-derived-sections-load-after-nextrace.md  # Derived sections load after loadNextRace(); no reactive observer
    0004-multi-backstack-tab-navigation.md   # Multi-backstack; per-tab NavBackStack avoids ViewModel destruction on tab switch
    0005-session-results-use-two-apis.md     # Session results: f1api.dev for R/Q/FP, Jolpica alpha for Sprint/SprintQuali
    0006-race-results-hybrid-source.md       # Race results: f1api.dev metadata/fastest-lap + Jolpica standard status/grid
    0007-podium-shape-locked.md            # Past-row podium: text-only InlinePodium, no red P1 background; red = current/active (LIVE only) (ticket 19)
    0008-screen-inset-bottom-only-top-bleeds.md  # Screen inset treatment: navigationBarsPadding only; top bleeds (ticket 21)
    0009-remove-openf1-runtime-dependency.md  # OpenF1 removed; f1api/Jolpica/local F1DB artwork; top speed absent from v1 (ticket 10)
    0010-my-team-content-into-homepage-§3.md  # Variant A wins; no separate My Team tab; §3 is the management surface (ticket 24, build 11)
    0011-countdown-widget-cache-narrow-to-racestart.md  # Widget cache stores only raceStartMillis; pre-race window is a fixed 3d buffer, not cached FP1 (ticket 07)
  plans/
    f1app-build/tickets/
      01-foundation-and-homepage-section-2.md   # shipped — Foundation + Homepage §2 (pins UX family)
      02-homepage-section-1-and-section-3.md    # shipped — Homepage §1 countdown + §3 combined favorites/nearest-GP cards (2027-01-15)
      03-schedule-tab-and-round-detail.md       # shipped — Schedule + two-mode Round detail + SessionResult/hybrid results
      04-leaderboard-and-driver-team-detail.md  # shipped — leaderboard + driver/team detail
      05-my-team-tab-and-favorites-picker.md    # shipped — My Team slots + bottom-sheet picker
      06-circuit-detail.md                      # shipped — Circuit detail page (most-wins + metadata, ticket 06, 2026-07-25)
      07-countdown-widget.md                    # shipped — Countdown widget (Glance + worker + deep link, ticket 07, 2026-07-25)
      08-imagery.md                            # shipped — driver headshots + team / car imagery (Cloudinary formula1.com, 2026+); driverRef derived from name+last surname token (no map)
      09-testing-cut.md                         # partial — shipped-slice unit/lifecycle coverage; widget reducer/full instrumentation remain
      10-remove-openf1-runtime-dependency.md   # shipped — f1api/Jolpica/local F1DB artwork source cut
    11-favorites-on-homepage.md              # ready — fold My Team content into Homepage §3 (wayfinder 24, ADR 0010)
  release/
    build-and-signing.md           # release buildType, signing, R8 (AGP 9 DSL), versioning, output (ticket 15)
  widget/
    countdown.md                   # CountdownWidget, CountdownWorker, NextRaceCache [BUILT ticket 07]
  tooling/
    pi-usage.md                    # pi session token/cost aggregation and provider→model report
  wayfinder/
    f1app/
      map.md                         # destination spec + scope + fog patches (v1 spec + v1 polish)
      homepage.md                    # Homepage three-section composition + data sources
      past-list.md                   # Schedule>Past full-podium fetch strategy (ticket 10) + locked visual treatment
      my-team-on-homepage.md         # Finding: 3 variants for folding My Team into Homepage §3 (no separate tab)
      tickets/
        01-architecture-and-modules.md   # closed (single :app, Wiring DI, MVVM init-less, Nav3, Ktor/CIO)
        02-design-system-theme.md        # closed (dark-only M3 theme; also the only ticket shipped as code)
        03-data-layer-and-refresh.md     # closed (f1api.dev primary, HttpCache, 1 CountdownWorker; DataStore + HttpCache)
        04-api-client-and-enrichment-scope.md  # closed (multi-source: f1api.dev + OpenF1 + jolpica on one HttpClient)
        05-navigation-and-deep-links.md  # closed (Navigation 3 × 7 routes, f1app:// custom-scheme deep link to RoundDetail)
        06-widget-technology.md          # closed (Glance; RemoteViews interop as escape hatch)
        07-countdown-widget-specifics.md # closed (adaptive 15-min/hourly gate; render-time LIVE window; off-season/no-cache/stale states)
        08-research-top-speed.md        # closed (research; ships via OpenF1) — see top-speed.md
        09-research-most-wins-at-circuit.md  # closed (research; ships via jolpica) — see circuit-most-wins.md
        10-research-past-list-podium.md  # closed (research; full-podium via per-row /race) — see past-list.md
        11-research-openf1-join-all-time-top-speed.md  # closed (research; date-match join + latest-peak semantics)
        12-design-favorites-picker-ux-storage.md      # closed (re-opened then closed 2027-01-11; favorites picker UX + storage human-locked)
        13-additive-ui-enrichments.md               # closed (planning — code lives in build 08; headshots parked for human A/B/C pick)
        14-testing-scope.md                          # closed (strategy: pure mappings + VM transitions + MockEngine; Compose UI + macrobenchmark deferred to 15)
        15-release-signing-r8.md                    # closed (release build: PKCS12 keystore in ~/.android, signingConfigs from git-ignored keystore.properties, optimization.enable=true + android.r8.gradual.support; versionCode 1/versionName "1.0.0"; sideload APK; macrobenchmark rung folded in)
        16-team-accent-source.md                    # closed (research; TeamColors.forId hardcoded map for v1, Jolpica alpha migration noted) — see team-accent.md
        17-q1-q4-homepage-layout.md                 # closed (grilling; one §1 hero card with countdown on top and 5-row weekend schedule below)
        18-section-3-favorites-shape.md             # closed (combined 3-row card; per-row constructor-color bars; single CTA empty state)
        19-q2-podium-shape-locked.md                # closed (task; text-only InlinePodium, no red P1 background, ADR 0007)
        20-q3-constructor-caption.md                # closed (task; keep Constructor caption, terminology.md entry added)
        21-edge-to-edge-insets-bug.md               # closed (task; navigationBarsPadding on Homepage + Schedule root Columns, top bleeds per ADR 0008)
        22-remaining-minor-observations.md          # closed (task batch; combined favorites card, a11y + visual polish + schedule logic)
        23-default-predictive-back.md                # closed (default Android/Navigation 3 behavior; no custom handler or animation)
        24-favorites-on-homepage.md                  # closed (variant A wins; no separate My Team tab; see ADR 0010 + build 11)
        25-news-rss.md                                 # parked — RSS news tab replaces My Team (post-v1); see lode/news/summary.md
      top-speed.md                     # top-speed/fastest-lap stat source + cost (ticket 08)
      top-speed-api-wrangling.md       # API wrangling detail for the top-speed stat (ticket 08)
      circuit-most-wins.md             # most-wins-at-circuit stat source + cost (ticket 09)
      circuit-most-wins-api-wrangling.md  # API wrangling detail for the most-wins-at-circuit stat (ticket 09)
      openf1-removal.md                # accepted runtime source replacement plan (ADR 0009)
      f1db-data.md                     # F1DB coverage for Driver of the Day and fastest laps
      team-imagery.md                  # formula1.com CDN: two systems, slug maps, car/team renders (ticket 13 enrichment #4)
      team-accent.md                   # TeamColors.forId hardcoded map: which API has it, why hardcoded v1, Jolpica alpha migration (ticket 16)
      cloudinary-headshot-paths.md     # driverRef = {name3}{last-surname-token3}01 — pure function, no map, no OpenF1; rules out option C, reframes option B as D (ticket 08 parked)
  tmp/                               # git-ignored session scraps
```

Related-but-outside: `lode/wayfinder/f1app/map.md` is the destination spec (the
"where we're going" doc). The lode files above describe the *current state* of the
system as it gets built.
