package com.anpurnama.f1_app.widget.countdown

/**
 * The cached next-race state read by the Countdown widget and written
 * by the [CountdownWorker]. The shape is the *rendered* state: every
 * field has a default so an off-season sentinel (`startMillis == 0L`)
 * can be represented without the worker fabricating fake race data.
 *
 * Field semantics:
 *  - [sessionName] names the session the widget is counting down to
 *    or showing as live (Free Practice 1/2/3, Sprint Qualifying,
 *    Sprint, Qualifying, Race).
 *  - [startMillis] is that session start in epoch millis (UTC).
 *    `0L` is the off-season sentinel — the worker writes this when
 *    `/current/next` returns an empty `race` array, and the reducer
 *    maps it to [CountdownState.SeasonOver].
 *  - [lastSyncedMillis] is the wall-clock at the worker's last
 *    successful (or attempted) write. Used by the worker gate to
 *    decide whether to fetch on the next tick.
 *  - The other fields default to empty/zero; the widget ignores them
 *    when [startMillis] is `0L`, so the worker doesn't need to
 *    fabricate a fake race when the season is over.
 *
 * **Domain-purity:** the data class is pure Kotlin (no `android.*`
 * imports). The cache wrapper in `data/NextRaceCache.kt` is the only
 * place that touches DataStore.
 */
data class NextRaceSnapshot(
    val year: Int = 0,
    val round: Int = 0,
    val raceName: String = "",
    val circuitName: String = "",
    val circuitCountry: String? = null,
    val circuitId: String = "",
    val sessionName: String = "Race",
    val startMillis: Long = 0L,
    val lastSyncedMillis: Long = 0L,
)
