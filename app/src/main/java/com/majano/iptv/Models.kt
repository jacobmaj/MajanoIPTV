package com.majano.iptv

enum class Kind { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

/** Un canal, película o serie. Si [url] está vacía (series Xtream) hay que cargar los episodios antes de reproducir. */
data class Entry(
    val id: String,
    val name: String,
    val categoryId: String,
    val image: String,
    val url: String,
    val kind: Kind,
    val rating: String = "",
    val plot: String = "",
    val year: String = "",
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val plot: String = "",
    val duration: String = "",
)

/** Datos extra de una película o serie (sinopsis, año, etc.). */
data class Info(
    val plot: String = "",
    val year: String = "",
    val rating: String = "",
    val duration: String = "",
    val genre: String = "",
)

fun ratingText(r: String): String {
    val v = r.trim().toDoubleOrNull() ?: return ""
    return if (v <= 0.0) "" else "★ " + String.format(java.util.Locale.US, "%.1f", v)
}

/** Datos de la conexión ya validada. */
class Session(
    val base: String,
    val user: String,
    val pass: String,
    val liveExt: String,
    val isXtream: Boolean,
    val m3uUrl: String,
)
