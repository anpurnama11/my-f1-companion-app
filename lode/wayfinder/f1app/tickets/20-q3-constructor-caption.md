---
id: 20
title: "Q3 `Constructor` caption — keep as-is"
type: task
status: closed
blocked_by: []
owner: "pi"
---

## Question

The §1 Team card caption is "Constructor". For a Drive-to-Survive audience, is this the right caption? Alternatives considered: "My team" (matches the My Team tab), "Team" (shorter, more accessible). Should the caption change?

## Resolution (closed 2027-01-15)

**Decision: keep `Constructor` as the §1 Team card caption.**

### Why

- F1-orthodox, matches the `ConstructorStanding` domain model name and the Jolpica `Constructor` object shape.
- "My team" was rejected: misleading when the user has not yet picked favorites (the default state at first launch). The §1 pager is showing the **championship leader** by default (first-launch seed), not necessarily the user's team.
- "Team" was rejected: shorter but loses the F1-specific meaning. "Constructor" carries the F1 championship concept that the standings model is built around.

### Lode write-back

- **`lode/terminology.md`** — add a `Constructor` entry with rejected synonyms (Team, My team, Constructor team). The glossary becomes the authority the next session cites when the user reaches for "Team".

## Cross-references

- `lode/terminology.md` — the glossary entry.
- `lode/wayfinder/f1app/tickets/18-section-3-favorites-shape.md` — §3 favorites shape; caption lives inside whichever shape is chosen.
- `lode/wayfinder/f1app/tickets/02-homepage-section-1-and-section-3.md` (in `lode/plans/f1app-build/`) — current `TeamCard` caption.
