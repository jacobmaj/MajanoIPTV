package com.majano.iptv

import org.json.JSONArray
import org.json.JSONObject

/** Un elemento del historial: película o serie (con el episodio donde se quedó) y la posición. */
data class Watch(
    val key: String,
    val server: String,
    val entry: Entry,
    val episodeId: String = "",
    val season: Int = 0,
    val number: Int = 0,
    val epTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L,
)

fun encodeHistory(list: List<Watch>): String {
    val arr = JSONArray()
    for (w in list) {
        val e = w.entry
        val o = JSONObject()
        o.put("key", w.key)
        o.put("server", w.server)
        o.put("id", e.id)
        o.put("name", e.name)
        o.put("cat", e.categoryId)
        o.put("image", e.image)
        o.put("url", e.url)
        o.put("kind", e.kind.name)
        o.put("rating", e.rating)
        o.put("plot", e.plot.take(300))
        o.put("year", e.year)
        o.put("ep", w.episodeId)
        o.put("season", w.season)
        o.put("num", w.number)
        o.put("epTitle", w.epTitle)
        o.put("pos", w.positionMs)
        o.put("dur", w.durationMs)
        o.put("at", w.updatedAt)
        arr.put(o)
    }
    return arr.toString()
}

fun decodeHistory(json: String): List<Watch> {
    if (json.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        val out = ArrayList<Watch>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kind = try { Kind.valueOf(o.optString("kind")) } catch (e: Exception) { continue }
            val entry = Entry(
                id = o.optString("id"),
                name = o.optString("name"),
                categoryId = o.optString("cat"),
                image = o.optString("image"),
                url = o.optString("url"),
                kind = kind,
                rating = o.optString("rating"),
                plot = o.optString("plot"),
                year = o.optString("year"),
            )
            out.add(
                Watch(
                    key = o.optString("key"),
                    server = o.optString("server"),
                    entry = entry,
                    episodeId = o.optString("ep"),
                    season = o.optInt("season"),
                    number = o.optInt("num"),
                    epTitle = o.optString("epTitle"),
                    positionMs = o.optLong("pos"),
                    durationMs = o.optLong("dur"),
                    updatedAt = o.optLong("at"),
                )
            )
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}
