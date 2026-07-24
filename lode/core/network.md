# Network layer

Ktor `HttpClient` configuration and API extensions.

## HttpClientFactory

`core/network/HttpClientFactory.kt` builds the single `HttpClient` at `F1App` startup:

- **Engine:** CIO (KMP-safe)
- **ContentNegotiation:** `kotlinx.serialization` JSON with
  `ignoreUnknownKeys = true; coerceInputValues = true`
- **HttpCache:** ~10 MB `FileStorage` under `cacheDir/http_cache`, `max-stale`
  tolerance for offline cold launch
- **HttpTimeout:** 15s connect / 10s request
- **expectSuccess = true** — 4xx/5xx throw before body deserialization (use cases
catch them)

Held by `Wiring`; one client per process, shared by the widget.

## F1Api

`f1/data/F1Api.kt` holds base URL consts and Ktor endpoint extensions:

- `F1API_BASE` — f1api.dev primary
- `JOLPICA_BASE` — jolpica (ticket 04)

Extensions: `getCurrent`, `getNextRace`, `getDriversChampionship`,
`getConstructorsChampionship`, `getJolpicaPitStops`, and the existing
f1api.dev/Jolpica result extensions.
Pure Kotlin, zero `android.*` imports (domain-purity invariant).

f1api.dev and Jolpica extensions take a `forceRefresh` flag and add
`Cache-Control: no-cache` when true. Circuit artwork is resolved from the
Android UI-layer local catalog, not from this network layer.

## Cross-references

- [../architecture/architecture.md](../architecture/architecture.md) — `Wiring`, module shape, KMP plan.
- [../practices.md](../practices.md) — build floor, `androidTestImplementation(platform(...))` note.
