package com.anpurnama.f1_app.f1

import com.anpurnama.f1_app.core.Outcome
import com.anpurnama.f1_app.f1.model.SessionResult
import com.anpurnama.f1_app.f1.model.SessionType

/** Single session boundary used by the SessionResult screen. */
class GetSessionResultUseCase(
    private val getRoundResults: GetRoundResultsUseCase,
    private val getRoundQualifying: GetRoundQualifyingUseCase,
    private val getPractice: GetPracticeResultUseCase,
    private val getSprint: GetSprintResultUseCase,
    private val getSprintQualifying: GetSprintQualifyingResultUseCase,
) {
    suspend operator fun invoke(
        year: Int,
        round: Int,
        session: SessionType,
        forceRefresh: Boolean = false,
    ): Outcome<SessionResult> = when (session) {
        SessionType.Race -> getRoundResults(year, round, forceRefresh).map {
            SessionResult(
                year = it.year, round = it.round, raceName = it.raceName,
                circuit = it.circuit, session = session,
                raceResults = it.results,
                fastestLap = fastestLap(it.results),
            )
        }
        SessionType.Quali -> getRoundQualifying(year, round, forceRefresh).map {
            SessionResult(
                year = it.year, round = it.round, raceName = it.raceName,
                circuit = it.circuit, session = session,
                qualifyingResults = it.results,
            )
        }
        SessionType.FP1, SessionType.FP2, SessionType.FP3 ->
            getPractice(year, round, session, forceRefresh)
        SessionType.Sprint -> getSprint(year, round, forceRefresh)
        SessionType.SprintQuali -> getSprintQualifying(year, round, forceRefresh)
    }
}

internal fun fastestLap(results: List<com.anpurnama.f1_app.f1.model.RoundResult>) = results
    .mapNotNull { result ->
        result.fastLap?.let { time ->
            com.anpurnama.f1_app.f1.model.FastestLap(
                driverNumber = result.driverNumber,
                driverName = result.driverName,
                driverShortName = result.driverShortName,
                time = time,
            )
        }
    }
    .minByOrNull { it.time }
