---
id: 28
title: "Wikipedia REST extension"
type: task
status: closed
blocked_by: []
owner: agent
closed_by: "Planned and recorded as build ticket 13. `HttpClient.getWikipediaSummary()` extension + `WikipediaSummary` DTO + `User-Agent: F1app/1.0 (+contact URL)` per Wikipedia etiquette + HttpCache coverage on re-opens. Slug source: f1api.dev `url` field (auto-redirects to canonical title). CC BY-SA 4.0 attribution surfaced in UI per ADR 0012. Implementation contract: build ticket 13. No new fog."
---

## Question

Add a `HttpClient.getWikipediaSummary(title: String)` extension
so DriverDetail/TeamDetail can fetch the "About" biography text
(ticket 26's "About" source). The summary is the Wikipedia REST
canonical extract — no third-party summarizer, no LLM, no HTML
parsing. The user-visible text is the Wikipedia editorial summary,
with CC BY-SA 4.0 attribution surfaced in the UI.

## Scope

- One DTO: `WikipediaSummary { title, extract, contentUrl,
  description, thumbnail? }` in `f1/data/Dtos.kt` (or a new file
  if cleaner).
- One `HttpClient` extension method
  (`getWikipediaSummary(title: String): WikipediaSummary`) in
  `f1/data/F1Api.kt` (or a new `WikipediaApi.kt`).
- One line in the `GetDriverDetailUseCase` /
  `GetTeamDetailUseCase` join — pass the f1api.dev `url` slug
  (auto-redirects to canonical title) as the input.
- `User-Agent: F1app/1.0 (+contact URL)` header per Wikipedia's
  API etiquette.
- HttpCache hit on re-opens.
- The "About" UI section's attribution line is handled in
  ticket 29; this ticket only delivers the data layer.

## Acceptance

- `getWikipediaSummary("Andrea_Kimi_Antonelli")` returns the
  Antonelli summary; the title is normalized to `Kimi Antonelli`
  (auto-redirect).
- `getWikipediaSummary("Mercedes-Benz_in_Formula_One")` returns
  the Mercedes summary; the title is normalized to
  `Mercedes-Benz in Formula One`.
- `User-Agent` header is set on the Wikipedia call (verifiable
  with Ktor's logging plugin or a test `MockEngine`).
- HttpCache hit on the second call within the cache TTL
  (verifiable in a JVM unit test).

## Out of scope

- The UI attribution line (ticket 29).
- Atom feed support (Wikipedia REST is JSON only for the summary
  endpoint; this is fine).
- Multiple language wikis (English only for v1).

## Cross-references

- [26 — GAP-F research](../tickets/26-research-gap-f-detail-redesign.md) —
  the "About" row in the source split.
- [driver-team-detail.md](../../driver-team-detail.md) — row
  "About" / biography in the field inventory.
- [driver-team-detail-api-wrangling.md](../../driver-team-detail-api-wrangling.md) —
  Wikipedia REST probe + payload shape + redirect behavior.
- [core/network.md](../../core/network.md) — `HttpClient` factory
  + per-request base URL pattern (the Wikipedia call uses a
  per-request base URL just like OpenF1 did).
- [ADR 0012](../../decisions/0012-gap-f-detail-page-data-sources.md) —
  records the Wikipedia REST decision + rejected alternatives.
