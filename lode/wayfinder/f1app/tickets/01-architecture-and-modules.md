---
id: 01
title: Architecture & module structure
type: grilling
status: closed
blocked_by: []
owner: "session-1"
resolved_at: 2025-07-15
---

## Resolution

Mirrors the developer's `PokemonDataViewer` project. Single `:app` module. Manual
`Wiring(context)` DI on a custom `Application` (`app.wiring`) — no Hilt — so the widget
shares the same service locator. MVVM with sealed `UiState` + `StateFlow`, states derived
via `combine` + `stateIn(WhileSubscribed(5_000))`, **init-less** (`Flow.onStart { load() }`).
UseCase seam between ViewModels and data (function refs). Sealed `Outcome<T>` at `core/`.
Navigation 3 (`NavKey` + `@Serializable` routes + `Navigator`/`NavigationState`).

**Network layer divergence from PokeDV:** Ktor Client (CIO engine, KMP-safe) replaces
Retrofit+OkHttp. Retrofit `@GET` interfaces are JVM-tied; Ktor endpoints are plain
`suspend fun`s in pure Kotlin, so the API definition itself satisfies the domain-purity
invariant and ports to a future KMP `:shared` module for free.

**Domain-purity invariant (hard):** `f1/` (domain models, DTOs, repository interface,
use cases, Ktor API extensions) must contain zero `android.*` imports. Platform concerns
injected as interfaces from `core/`.

**KMP plan (deferred, not started):** if KMP is greenlit later, extract `:shared`
(commonMain = today's `f1/`) + `:app` + `:ios` (SwiftUI). Pre-splitting now is YAGNI; the
invariant + Ktor choice make the port cheap when it happens. Compose/ViewModel/Nav3 are
Android-UI-layer and will be rewritten as SwiftUI at port time (inherent to learning
SwiftUI, not avoided by any present choice).

Room is NOT an architectural tenet — storage choice is deferred to ticket 03. Current
lean: no Room; Ktor `HttpCache` + long `max-stale` + pull-to-refresh + one DataStore key
for the widget's cached next-race timestamp.

Lode: [../../architecture/architecture.md](../../architecture/architecture.md),
[../../practices.md](../../practices.md), [../../terminology.md](../../terminology.md).

## Question (original)

What's the module and layering structure — single `:app` or multi-module? MVVM +
repository? Hilt or dependency-light? See Resolution above.
