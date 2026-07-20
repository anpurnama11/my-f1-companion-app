---
id: 10
title: "Research: full podium on GP Schedule Past list (GAP-C)"
type: research
status: closed
blocked_by: []
owner: ""
closed_by: "Past list shows full podium (P1/P2/P3). /current gives P1 only; P2/P3 require per-round GET /{year}/{round}/race (results[0..2]) — no bulk podium endpoint exists. Lazy per-row GetRoundPodiumUseCase, cached by HttpCache. Research output: lode/wayfinder/f1app/past-list.md"
---

## Question

The GP Schedule Past list calls for "the name of the driver who in podium." The
initial build shows only the winner. Is full podium (top 3) worth the extra fetch cost,
and what is the lazy implementation?

## Context

- `/current` (full-season schedule) inlines one `winner: {driverId, name, surname, ...}`
  per completed race — winner comes free.
- Full podium (P1/P2/P3) requires fetching `/{year}/{round}/race` per past round and
  taking `results[0..2]`. N calls for N past rounds in the season (~half the calendar
  mid-season).
- All on f1api.dev — no second API needed.
- HttpCache + pull-to-refresh covers the per-round /race calls; WorkManager-for-sync is
  out (ticket 03 / ticket 04).

## Resolution needed

- Is "winner only" acceptable on the Past list, with podium shown on the round-result
  drilldown? (Lean: yes — Podium cell on the drilldown already shows top 3 from the
  `/race` fetch the drilldown needs anyway.)
- If full podium on the list is wanted: lazy-load per-row on scroll-into-view vs
  eager-batch — which is cheaper under HttpCache? (Lean: lazy per-row via a small
  `GetRoundWinnerUseCase` invoked when the row composes, cached.)

## Out of scope

- GAP-A (top speed) — ticket 08.
- GAP-B (most wins at circuit) — ticket 09.
- The round-result drilldown itself — in scope of ticket 03
  (`GetRoundResultsUseCase`).

## Default resolution if not investigated

Past list shows **winner only** (free from `/current`). Full podium lives on the
round-result drilldown where the `/race` fetch is already incurred.
