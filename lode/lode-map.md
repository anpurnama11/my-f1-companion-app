# Lode map — F1app

Hierarchical index of all lode files. Update when files are added/removed.

```
lode/
  summary.md                         # living one-paragraph snapshot
  terminology.md                     # domain + project glossary
  practices.md                       # patterns, conventions, invariants
  lode-map.md                        # this file
  architecture/
    architecture.md                  # module, DI, layers, tech choices (ticket 01)
    id-namespaces.md                 # two id namespaces (Ergast canonical + Jolpica alpha opaque) + the car-number translator (step 6)
  core/
    navigation.md                    # Navigation 3 4-tab shell shape (ticket 01)
    network.md                       # Ktor HttpClient factory + F1Api extensions
  circuit/
    circuit-most-wins.md             # Jolpica P1-history aggregation contract
  data-sources/
    f1db-coverage.md                 # build-time F1DB scope and invariants
    f1db-detail-data.md              # detail-page catalog source and aggregation rules
    formula1-imagery.md              # car and driver CDN URL contracts
  design-system/
    theme.md                         # dark-only M3 theme + Circuits/Shapes/Spacing (ticket 02)
    icons.md                         # launcher adaptive icon + 4 tab bar icons (mipmap/drawable rules)
    team-colors.md                   # local constructor accent source and fallback
  leaderboard/
    summary.md                       # standings tab + driver/team detail joins (ticket 04)
  my-team/
    summary.md                       # 3-slot favorites management + atomic uniqueness (ticket 05)
  news/
    summary.md                       # parked — RSS news tab design (post-v1; replaces My Team per ticket 25)
  offline-data-cache/
    summary.md                       # Proto DataStore current-season typed resource snapshot cache + repositories
    refresh-coordination.md          # foreground per-resource refresh + WorkManager bundle sync + worker result policy
  specs/
    f1app.md                         # problem + solution + user stories
    data-layer.md                    # architecture, data sources, API client, caching, navigation, schedule
    cache-correctness-hardening.md   # truthful refresh outcomes, payload compatibility, recovery, and coverage
    screens.md                       # screen contracts: deep links, round detail, favorites, countdown, enrichments
    build.md                         # design system, package layout, release, signing, build floor
    testing.md                       # testing decisions, out-of-scope, further notes
    offline-data-cache.md            # spec — durable current-season structured-data cache
  testing/
    scope.md                         # testing scope + libs + placement + KMP portability rule (ticket 14)
  decisions/
    0001-openf1-qualydate-not-racedate.md  # OpenF1 join uses qualyDate, not raceDate (correction of ticket 11 research)
    0002-sectionuistate-is-vm-to-ui-transport.md  # SectionUiState is VM→UI transport; Outcome stops at the VM
    0003-derived-sections-load-after-nextrace.md  # Derived sections load after loadNextRace(); no reactive observer
    0004-multi-backstack-tab-navigation.md   # Multi-backstack; per-tab NavBackStack avoids ViewModel destruction on tab switch
    0005-session-results-use-two-apis.md     # accepted (amended 2026-07-26; supersedes 0006) — R+Q on Jolpica standard; FP+SR+SQ on Jolpica alpha (car-number translator to Ergast canonical); f1api.dev only for schedule+catalogs. Two namespaces only (Ergast canonical + alpha opaque); see architecture/id-namespaces.md.
    0006-race-results-hybrid-source.md       # superseded by 0005 (amended 2026-07-26) — was: f1api.dev metadata/fastest-lap + Jolpica standard status/grid merge; hybrid merge retired (Jolpica standard /results.json is single source)
    0007-podium-shape-locked.md            # Past-row podium: text-only InlinePodium, no red P1 background; red = current/active (LIVE only) (ticket 19)
    0008-screen-inset-bottom-only-top-bleeds.md  # Screen inset treatment: top safe at rest; scroll-under bleed (ticket 21)
    0009-remove-openf1-runtime-dependency.md  # OpenF1 removed; f1api/Jolpica/local F1DB artwork; top speed absent from v1 (ticket 10)
    0010-my-team-content-into-homepage-§3.md  # Variant A wins; no separate My Team tab; §3 is the management surface (ticket 24, build 11)
    0011-countdown-widget-cache-narrow-to-racestart.md  # superseded by 0014; prior race-only widget cache decision
    0012-gap-f-detail-page-data-sources.md       # GAP-F data sources: F1DB build-time + Wikipedia REST (ticket 26)
    0013-compare-card-vs-teammate.md         # Compare card on DriverDetail = vs teammate; rejected: vs another driver = dropped Driver Comparison feature (ticket 29)
    0014-countdown-widget-shows-next-session.md  # Widget cache stores selected current/next session name+start, falling back to Race
    0015-upcoming-session-result-button-buffer.md  # UpcomingWeekend Results button gated by per-session start buffer (6–12h); not raw start<=now
    0016-standings-source-move-to-jolpica.md  # Standings source: f1api.dev → Jolpica; MRData envelope, string→Int coercion, Constructors[] array handling
    0017-offline-refresh-coordination.md  # foreground per-resource; background best-effort current-season bundle; per-resource single-flight + schedule-gated rollover
    0018-cache-status-on-section-content.md  # SectionUiState.Content carries Fresh/Stale/Refreshing/RefreshFailed sync status
    0019-offline-cache-uses-datastore-snapshots.md  # Proto DataStore CacheState.snapshots; Room only on measured scale/query/contention tripwires
  release/
    build-and-signing.md           # release buildType, signing, R8 (AGP 9 DSL), versioning, output (ticket 15)
  repository/
    public-release.md              # README, MIT license, screenshots, attribution, and public-release contracts
  session-results/
    qualifying-segment-tabs.md     # Quali/SprintQuali SessionResult renders Q1/Q2/Q3 tabs derived from q1/q2/q3 rows
  widget/
    countdown.md                   # CountdownWidget, CountdownWorker, NextRaceCache
  tooling/
    pi-usage.md                    # pi session token/cost aggregation and provider→model report
  tmp/                               # git-ignored session scraps
```

The Lode describes current durable system knowledge. GitHub Issues is the
canonical tracker for future work and implementation history.
