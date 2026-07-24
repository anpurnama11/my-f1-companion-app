package com.anpurnama.f1_app.ui.artwork

import androidx.annotation.DrawableRes
import com.anpurnama.f1_app.R

data class CircuitArtworkAsset(
    @DrawableRes val resourceId: Int,
    val tintable: Boolean,
)

/** Local, deterministic circuit artwork catalog keyed by f1api.dev circuit ID. */
object CircuitArtwork {
    private val placeholder = CircuitArtworkAsset(
        resourceId = R.drawable.circuit_placeholder,
        tintable = false,
    )

    private val assets: Map<String, CircuitArtworkAsset> = mapOf(
        "abudhabi" to R.drawable.circuit_abudhabi,
        "albert_park" to R.drawable.circuit_albert_park,
        "austin" to R.drawable.circuit_austin,
        "baku" to R.drawable.circuit_baku,
        "bahrain" to R.drawable.circuit_bahrain,
        "catalunya" to R.drawable.circuit_catalunya,
        "hungaroring" to R.drawable.circuit_hungaroring,
        "imola" to R.drawable.circuit_imola,
        "interlagos" to R.drawable.circuit_interlagos,
        "jeddah" to R.drawable.circuit_jeddah,
        "lusail" to R.drawable.circuit_lusail,
        "madring" to R.drawable.circuit_madring,
        "marina_bay" to R.drawable.circuit_marina_bay,
        "miami" to R.drawable.circuit_miami,
        "monaco" to R.drawable.circuit_monaco,
        "montmelo" to R.drawable.circuit_montmelo,
        "monza" to R.drawable.circuit_monza,
        "redbullring" to R.drawable.circuit_redbullring,
        "shanghai" to R.drawable.circuit_shanghai,
        "silverstone" to R.drawable.circuit_silverstone,
        "spa" to R.drawable.circuit_spa,
        "suzuka" to R.drawable.circuit_suzuka,
        "vegas" to R.drawable.circuit_vegas,
        "yasmarina" to R.drawable.circuit_yasmarina,
        "zandvoort" to R.drawable.circuit_zandvoort,
    ).mapValues { (_, resourceId) -> CircuitArtworkAsset(resourceId, tintable = true) }

    fun forId(circuitId: String): CircuitArtworkAsset = assets[circuitId] ?: placeholder
}
