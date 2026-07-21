# 0002 — SectionUiState is the VM→UI transport, Outcome stops at the VM

## Context

The VM→UI boundary was leaking the data-layer result type. `HomepageViewModel.UiState.Sections`
exposed each section atom as `Outcome<T>` (Success/Failure/Loading), the homepage composable
imported `com.anpurnama.f1_app.core.Outcome`, and the shared `OutcomeContent` renderer dispatched
on `Outcome`. `Outcome` is operation vocabulary ("did the fetch succeed?"). The UI cares about
screen vocabulary ("does this section show content, a spinner, or an error?"). Exposing a generic
result type straight onto the public state is the smell — justifiable by N=6 independently-failing
sections + a shared renderer, but a smell nonetheless; the composable re-derives UI intent from an
operation result every render.

PokeDV (the reference project) holds the clean view: each screen has its own sealed `UiState`
where variants are named for the screen (`Content(detail, isFavorite)`, `Error(message)`), and the
VM collapses `Outcome` into it before the composable sees it.

## Decision

Introduce `SectionUiState<T>` (`Loading / Error(message) / Content(data)`) in `core/ui/` as the
single VM→UI transport for sections. `Outcome<T>` (`Success/Failure/Loading`) stays as the
data-layer result type returned by use cases. The VM maps `Outcome → SectionUiState` at the
assignment site (`Outcome.toSection()` extension) so the composable never imports `Outcome`.

The vocabulary shift is the point, not a rename: `Success` (operation) → `Content` (screen);
`Failure` (operation) → `Error` (screen). The shared `OutcomeContent` renderer now dispatches on
`SectionUiState` and keeps amortizing the Loading/Error/Content render over every screen.

## Why

A screen-specific sealed state is idiomatic; a generic result type leaking past the VM seam is not.
The fix preserves the one benefit the deviation bought (shared renderer across N sections) while
removing the leak (`Outcome` imports retreat to `f1/` use cases + the VM's function-ref parameter
types only). Shortest path that un-smells it without losing the shared-renderer argument.

## Considered

- **Keep as-is, record as debt.** Rejected — the ceiling ("`Outcome` imports spread beyond
  Homepage once tickets 03/04/05 land") is exactly when the deviation stops paying for itself,
  and the fix is ~1 new file + a type rename.
- **Per-screen `HomepageSectionUiState`.** Rejected — `OutcomeContent` is shared across screens
  by design (ticket 01 OQ#2), so the transport it dispatches on is shared too. A screen-local type
  would rebuild the same shape per screen.

Status: accepted.
