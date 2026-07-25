---
id: 25
title: "News tab (parked to v2) — RSS news replaces My Team tab"
type: grilling
status: parked
blocked_by: [11]
owner: "pi"
---

## Question

A news feature for F1app — does the design hold, where does it live, and why is it parked?

## Answer (locked, parked to v2)

The news feature is **parked to v2** because `../map.md` locks the v1 destination. The wayfinder's "If not covered by a free API, it's not built" rule excludes Firebase-Remote-Config-backed screens, but RSS feeds are public and free — the parking is **not** because of the API rule, it's because v1 destination is locked and no in-scope screen needs news.

When the parking is lifted, the news tab **replaces the My Team tab** in the nav (My Team content moves to Homepage §3 per wayfinder ticket 24 / plans ticket 11). The news tab is the 4th tab, not a 5th.

### Locked design (full detail in `../../../news/summary.md`)

- **Data layer** — `f1/data/RssApi.kt` with `RssFeedDto` / `RssChannelDto` / `RssItemDto` (annotated `@XmlSerialName("rss")` on the root), `HttpClient.getRssFeed()` extension, internal DTO→domain mapper. XML goes through `bodyAsText()` + top-level `XML { ignoreUnknownKeys = true }.decodeFromString(text)` — **not** Ktor's ContentNegotiation plugin. New dep: `kotlinx-serialization-xml` (the underlying lib, not the Ktor wrapper).
- **Sources** — `f1/data/RssSources.kt` with `RssSource(key, kind, url, articleLinkFilter)`, `SourceKey` and `SourceKind` enums, `DefaultRssSources` (3 sources: Autosport, The Race, FIA), `BbcAugmentedRssSources` (4-source variant with BBC's `/sounds/` and `/iplayer/episode/` link filter).
- **Domain model** — `f1/model/NewsArticle.kt` (id, sourceKey: String, sourceKind: String, headline, snippet, link, publishedAt: Instant; **no `isFresh` field**). `f1/model/NewsFeed.kt` (articles + failedSourceCount + totalSourceCount).
- **Use case** — `f1/GetNewsFeedUseCase.kt` fans out N sources in parallel via `coroutineScope { sources.map { async { runCatching { ... } } }.awaitAll() }`. Returns `Outcome<NewsFeed>`. All-fail returns `Outcome.Failure("Couldn't load news (N/N sources failed)")` — **not** silent `Success(emptyList())`. Per-source `runCatching` swallows network errors; rethrows `CancellationException` before the generic catch. No `clock` injection; `isFresh` is derived in the UI from a VM-owned `now` ticker.
- **ViewModel** — `feature/news/NewsViewModel.kt`. Single `SectionUiState<NewsFeed>` (not a `Sections` envelope). Init-less + `Lazily`. The 60s `now` tick is **VM-owned** (a `MutableStateFlow<Instant>` updated by a `warmUp`-launched coroutine), not `produceState` in the screen. `refresh()` re-fires with `forceRefresh = true`.
- **Screen** — `feature/news/NewsScreen.kt`. `PullToRefreshBox` + `OutcomeContent` + `LazyColumn<NewsCard>`. `EmptyNewsState` for the empty case. `openInCustomTabs(context, link)` for tap handler.
- **Card** — `feature/news/NewsCard.kt`. Source pill (`outlineVariant` background + `onSurface` text), headline, snippet (HTML-stripped + entity-decoded), `AgeLabel(publishedAt, now)`, `FreshDot` derived from `isFreshAt(publishedAt, now)` (24h threshold, **UI-derived from the same `now`**).
- **Navigation** — `Route.News` replaces `Route.MyTeam` in `homepageTabs`. `TopLevelDestination` enum drops `MyTeam`, adds `News` with `R.drawable.ic_news_outline.xml` (new drawable). `entry<Route.MyTeam>` deleted; `entry<Route.News>` added.
- **Wiring** — `Wiring.kt` instantiates `GetNewsFeedUseCase` with `DefaultRssSources` (or `BbcAugmentedRssSources` for the 4-source variant). One new import + 5 lines.
- **Build files** — `libs.versions.toml` +2 aliases (`androidx-browser`, `kotlinx-serialization-xml`); `app/build.gradle.kts` +2 `implementation(...)` lines. `HttpClientFactory.kt` **unchanged** (no Ktor XML plugin).
- **Test surface** — 5 JVM unit test files: `RssItemToArticleTest` (pure mapping), `RssApiParseTest` (XML roundtrip), `RssPodcastFilterTest` (BBC link filter), `GetNewsFeedUseCaseTest` (fan-out + partial-fail + all-fail + dedup + sort + cancellation), `NewsViewModelTest` (init-less + Lazily + refresh + all-fail-maps-to-Error).

### Three product questions (locked for v2, picked now)

- **Placement:** 4th tab replacing My Team (not 5th tab, not Homepage §4, not Schedule enrichment). My Team tab is removed per ticket 24 / plans ticket 11.
- **Source list:** 3 sources (Autosport + The Race + FIA). BBC variant is `BbcAugmentedRssSources` in `Wiring` — a 1-line swap.
- **Dedup:** `id = normalizeLink(link).toShortSha1()`. `normalizeLink` strips fragment + trailing slash + tracking params (`utm_*`, `fbclid`, `gclid`, `mc_cid`, `mc_eid`). Original `link` is preserved on the model for opening.

## My Team migration audit (6 items — must run before ticket 25 ships)

The My Team tab removal + News tab addition is a coupled change. The audit items below must all pass before ticket 25 can land:

1. **Deep links to `Route.MyTeam`** — any external `f1app://myteam` URLs (notifications, app links, tests) need a redirect to `Route.Homepage`. The Countdown widget deep-links to `Route.RoundDetail`, not `Route.MyTeam` — that path is already safe.
2. **Saved-state restoration** — users upgrading with a saved back stack on `Route.MyTeam` must not restore into a missing destination. Nav3's `entryProvider` falls back to the start route (`Route.Homepage`); the test should cover the upgrade case.
3. **Back stack behavior** — the old flow was `My Team tab → picker → return to My Team tab`. The new flow is `Homepage → modal bottom sheet → dismiss`. Tests asserting the old flow need to be updated. The per-tab back stack itself is unchanged (Nav3 multi-backstack).
4. **Top-level destination selected state** — any `when (currentRoute)` logic that includes `Route.MyTeam` (analytics, screenshot tests, deep-link handlers) breaks. Audit all `NavShell`, analytics, and test files.
5. **Icon deletion** — `ic_myteam_outline.xml` is safe to delete **after** `TopLevelDestination.MyTeam` is removed. The drawable is referenced only from the enum entry.
6. **My Team content refactor** — the existing `feature/myteam/MyTeamScreen.kt` content (slot cards + `ModalBottomSheet` picker) moves to `feature/homepage/` as a sheet hosted by `HomepageScreen`. Per plans ticket 11.

## v2 parking list (11 enhancements, not in initial build)

When the feature un-parks, the following are recorded as v2 enhancements (not blockers):

1. **Per-source result list** — add `sources: List<SourceResult>(key, articleCount, errorMessage?)` to `NewsFeed` so the screen can distinguish "feed empty" from "feed failed" from "items dropped due to date parse".
2. **`pubDate` fallback** — when `<pubDate>` is null, fall back to `channel.lastBuildDate` or fetch time. v1 drops the item.
3. **Atom feed support** — add Atom DTOs OR pre-reject with a clearer error if any source moves to Atom. v1 fails the source on Atom.
4. **`<guid>`-based dedup** — prefer `<guid>` for dedup when present, fall back to normalized link. v1 uses link only.
5. **Recency-precedence dedup** — on dedup match, prefer the most recent `publishedAt`. v1 uses first-wins.
6. **Kind-precedence dedup** — `OFFICIAL > ANALYSIS > NEWS`. v1 uses first-wins.
7. **`value class NewsSourceKey` and `enum class NewsSourceKind`** in the model layer for type safety. v1 uses `String`.
8. **`Map<String, RssSource>` for `byKey`** when the source list grows past ~10. v1 uses linear scan.
9. **Remote config or build-time config** for the source list so feed outages don't require app releases. v1 hardcoded.
10. **App-level cache (DataStore<Preferences>)** so cold News on cellular shows last-known news. v1 HttpCache only.
11. **"Showing X of Y sources" hint** when `failedSourceCount > 0 && articles.isNotEmpty()`. v1 silently partial-fails.

## Why parked

- `../map.md` is locked at v1 destination. Tickets 01–15 are the v1 spec (closed); 16–24 are the v1 polish pass (closed). News is not in v1.
- `../../../specs/f1app.md` §Out of Scope (line 622) explicitly lists "News + collaborator content screens" as out of v1.
- Ticket 25 cannot ship until plans ticket 11 (My Team tab removal) is built, because the news tab takes the My Team slot. Plans ticket 11 is `ready` (blocked on wayfinder ticket 24, which is `closed`).

## Cross-references

- `../../../news/summary.md` — the full design (file structure, type signatures, data flow, test surface).
- `24-favorites-on-homepage.md` — wayfinder decision that removes the My Team tab.
- `../../../plans/f1app-build/tickets/11-favorites-on-homepage.md` — plans ticket for the My Team removal.
- `../../../specs/f1app.md` §Out of Scope — news + collaborator content screens are out of v1.
- `../../../practices.md` — domain-purity invariant, init-less VM pattern, `Lazily` convention, `internal` mapper convention.
- `../map.md` — "Build vs decide" rule for wayfinder vs plans tickets.
