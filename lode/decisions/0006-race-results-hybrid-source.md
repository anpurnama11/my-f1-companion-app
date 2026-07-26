# 0006 — Race results use a hybrid source

**Status:** superseded by [0005-session-results-use-two-apis.md](0005-session-results-use-two-apis.md) (amended 2026-07-26)

> **Superseded.** The hybrid merge of f1api.dev race metadata + Jolpica
> standard `status`/`grid` is fully retired. Jolpica standard
> `/ergast/f1/{year}/{round}/results.json` carries the full Ergast richness
> (circuit block, per-row `Constructor`, `status`, `grid`, `fastestLap`,
> time/gap) in one call, so the driver-number merge bought nothing. `GetRoundResultsUseCase`
> now makes a single Jolpica standard call; the f1api.dev `/{year}/{round}/race`
> fetch and its DTO are deleted (step 5). The amended 0005 is the current
> decision. The body below is retained for the point-in-time record of the
> hybrid merge.

## Context

`GetRoundResultsUseCase` needs reliable race results for `RoundDetail` past mode, the Race `SessionResult` screen, and the `GetRoundPodiumUseCase`.

Research showed:

- f1api.dev `/{year}/{round}/race` provides circuit metadata, per-driver fastest-lap data (`fastLap`), and time/gap strings.
- f1api.dev does **not** expose a reliable `status` field. It mislabels Did-Not-Start rows as DNF — e.g., 2026 Chinese GP, Alex Albon: `grid: "not available"`, `time: "DNF (0)"`, `retired: null`.
- Jolpica standard `/ergast/f1/{year}/{round}/results.json` provides an authoritative `status` (`Finished`, `Lapped`, `Retired`, `Did not start`, etc.) and a numeric grid, but lacks circuit metadata and per-driver fastest-lap data.

## Decision

Use **both** sources for race results:

- **f1api.dev** — circuit metadata, per-driver fastest-lap data, and time/gap strings.
- **Jolpica standard** — authoritative status and grid.

Merge the two responses by driver number. The `RoundResult` domain model carries a `status` derived from Jolpica. UI mapping:

- `Finished` / `Lapped` → show the f1api.dev `time` string.
- `Retired` → **"DNF"**.
- `Did not start` → **"DNS"**.
- `grid: "0"` → display **"PL"** (pit lane) and hide the position-change arrow.
- DNF/DNS rows still compute the position-change arrow from grid vs position.

## Why

- Gives accurate DNF/DNS classification without losing f1api.dev's circuit metadata and fastest-lap data.
- Cheaper than fully migrating race results to Jolpica, which would require an extra circuit fetch and lose per-driver fastest-lap info.
- Driver number is stable across both sources, making the merge straightforward.

## Consequences

- `GetRoundResultsUseCase` now makes two parallel network calls for race results.
- A new Jolpica standard race-results DTO and mapper is needed.
- `RoundResult` domain model needs a `status` field.
- Tests and fixtures must be updated for the hybrid shape.
- Sprint (Jolpica alpha) reuses the same status-label logic; Sprint Qualifying and Qualifying skip DNF/DNS labels because their sources do not expose reliable status.

