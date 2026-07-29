# 0012 — GAP-F detail-page data sources: F1DB build-time + Wikipedia REST

**Status: accepted**

The redesigned DriverDetail and TeamDetail screens source their
new fields (stats, first entry/win, world championships, chassis,
power unit, base country, "About" biography) from F1DB build-time
+ Wikipedia REST + the already-wired f1api.dev. No new paid API.
No new OpenF1 runtime dependency (ADR 0009 preserved). The bar
chart, base city, and team principal fields were dropped because
they have no free JSON source. The all-time "Grands Prix" count
is race-only (sprint rounds filtered) to match the screenshot and
the casual F1 fan mental model.

Detail: [current data contract](../data-sources/f1db-detail-data.md) and
[historical API probes](https://github.com/anpurnama11/my-f1-companion-app/issues/56).
Resolution: ticket 26 + follow-up tickets 27/28/29.

## Source split

- **Stats + team facts** → F1DB build-time (catalog files generated
  alongside the existing `tools/f1db/import-circuit-artwork.py`).
  Generated artifacts are checked in. No runtime network, no
  live-window risk, no alpha-endpoint risk.
- **"About" biography** → Wikipedia REST
  `GET /api/rest_v1/page/summary/{title}`, slug from f1api.dev
  `url` (auto-redirects to canonical title). Free, stable,
  CC BY-SA 4.0. The user-visible text is the Wikipedia editorial
  summary with attribution; no third-party summarizer, no LLM,
  no HTML parsing.
- **Everything else** → f1api.dev (already wired), Cloudinary
  (already wired), `TeamColors.forId()` (already wired).

## Rules locked

- **Bar chart, base city, team principal dropped.** No free JSON
  source; the design dropped the fields rather than introduce a
  hardcoded map or scrape HTML.
- **All-time "Grands Prix" = race-only.** F1DB includes sprint
  rounds; the screen excludes them to match the screenshot.
- **F1DB runtime network = none.** F1DB is build-time only; the
  runtime source graph is unchanged.
- **Wikipedia `User-Agent`** = `F1app/1.0 (+contact URL)` per
  Wikipedia's API etiquette. Below the 200 req/s Wikimedia edge
  limit.

## Considered options

- **F1DB build-time** for stats + team facts (chosen) — same
  precedent as the existing `tools/f1db/import-circuit-artwork.py`
  script.
- **OpenF1** for stats (rejected) — ADR 0009 forbids adding
  OpenF1 to the runtime source graph; OpenF1's 30-day live
  window also breaks the off-season favorites surface.
- **Jolpica alpha** for some fields (rejected for now) — alpha
  tree is still stabilizing per issue #304. Tracked as the
  future source for some fields when the alpha tree stabilizes.
- **Sportmonks** (paid, ~€79/mo) — near-perfect data shape,
  ruled out per the "if not covered by a free API, it's not
  built" rule.
- **HTML scraping** (F1 Fandom Wiki / Liquipedia) (rejected) —
  Fandom is Cloudflare-protected; Liquipedia has no JSON API.
  Both rule themselves out.
- **TheSportsDB** (rejected) — no Cadillac, no chassis/PU/
  principal coverage.
- **LLM-generated biography** (rejected) — non-free (API cost),
  non-canonical (drift over time), license-ambiguous. Wikipedia
  REST is the editorial source of truth.
