@file:OptIn(ExperimentalMaterial3Api::class)

package com.majano.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun buildRows(all: List<Entry>, cats: List<Category>, favKeys: List<String>): List<Pair<String, List<Entry>>> {
    val out = ArrayList<Pair<String, List<Entry>>>()
    val favs = all.filter { favKeys.contains("${it.kind.name}:${it.id}") }
    if (favs.isNotEmpty()) out.add("★ Mis favoritos" to favs.take(40))
    val byCat = all.groupBy { it.categoryId }
    for (c in cats) {
        val l = byCat[c.id]
        if (!l.isNullOrEmpty()) out.add(c.name to l.take(40))
        if (out.size >= 30) break
    }
    if (out.isEmpty() && all.isNotEmpty()) out.add("Todo el catálogo" to all.take(60))
    return out
}

/** Películas / Series: banner destacado + filas de pósters por categoría. */
@Composable
fun MediaHome(model: AppModel, kind: Kind, wide: Boolean) {
    LaunchedEffect(kind) { model.loadKind(kind) }
    val title = if (kind == Kind.MOVIE) "Películas" else "Series"
    val all = model.items[kind].orEmpty()
    val cats = model.cats[kind].orEmpty()
    val loading = model.isLoading(kind)
    var query by remember(kind) { mutableStateOf("") }
    var featured by remember(kind) { mutableStateOf<Entry?>(null) }
    val favKeys = model.favorites.toList()
    val rows = remember(all, cats, favKeys) { buildRows(all, cats, favKeys) }
    val hero = featured ?: rows.firstOrNull()?.second?.firstOrNull()
    val q = query.trim()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SearchField(query, "Buscar", Modifier.width(if (wide) 260.dp else 160.dp)) { query = it }
            Btn("↻", { model.loadKind(kind, force = true) })
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading && all.isEmpty() -> LoadingBox("Cargando catálogo del proveedor…")
            all.isEmpty() -> EmptyBox("No hay contenido en esta sección.")
            q.isNotEmpty() -> {
                val results = remember(all, q) { all.filter { it.name.contains(q, ignoreCase = true) }.take(300) }
                if (results.isEmpty()) EmptyBox("Sin resultados para \"$q\".")
                else PosterGrid(model, results, Modifier.weight(1f).fillMaxWidth())
            }
            else -> {
                if (hero != null) {
                    Hero(model, hero, wide)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows) { row -> PosterRow(model, row.first, row.second) { featured = it } }
                }
            }
        }
        StatusLine(model)
    }
}

@Composable
private fun Hero(model: AppModel, e: Entry, wide: Boolean) {
    val h = if (wide) 200.dp else 150.dp
    val meta = listOf(if (e.kind == Kind.MOVIE) "Película" else "Serie", e.year, ratingText(e.rating))
        .filter { it.isNotBlank() }.joinToString("  ·  ")
    Box(
        Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF2A1A22), Color(0xFF12151F)))),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("DESTACADO", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(e.name, color = MainText, fontSize = if (wide) 24.sp else 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Text(
                    e.plot.ifBlank { "Presiona para ver la sinopsis y reproducir." },
                    color = Muted, fontSize = 12.sp, maxLines = if (wide) 3 else 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.kind == Kind.MOVIE) {
                        Btn("▶ Reproducir", { model.playMovie(e) }, primary = true)
                        Btn("Más info", { model.openDetail(e) })
                    } else {
                        Btn("▶ Ver temporadas", { model.openDetail(e) }, primary = true)
                    }
                }
            }
            PosterImage(e.image, e.name, Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)))
        }
    }
}

@Composable
private fun PosterRow(model: AppModel, title: String, list: List<Entry>, onFocus: (Entry) -> Unit) {
    Column {
        Text(title, color = MainText, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            items(list) { e -> PosterCard(model, e, list, Modifier.width(112.dp), onFocus) }
        }
    }
}

@Composable
fun PosterCard(model: AppModel, e: Entry, list: List<Entry>, modifier: Modifier, onFocus: ((Entry) -> Unit)? = null) {
    Focusable(
        onClick = { model.open(e, list, immersive = true) },
        modifier = modifier,
        color = Color.Transparent,
        focusScale = 1.05f,
        onFocus = if (onFocus != null) ({ onFocus(e) }) else null,
    ) {
        Column {
            Box {
                PosterImage(e.image, e.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)), crop = e.kind != Kind.LIVE)
                if (model.isFav(e)) {
                    Text("★", color = Accent, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
            Text(
                e.name, color = SoftText, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
fun PosterGrid(model: AppModel, list: List<Entry>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(6.dp),
    ) {
        items(list) { e -> PosterCard(model, e, list, Modifier.fillMaxWidth()) }
    }
}

@Composable
fun FavoritesScreen(model: AppModel) {
    LaunchedEffect(Unit) { Kind.values().forEach { model.loadKind(it) } }
    val favKeys = model.favorites.toList()
    val live = model.items[Kind.LIVE].orEmpty()
    val movies = model.items[Kind.MOVIE].orEmpty()
    val series = model.items[Kind.SERIES].orEmpty()
    val favs = remember(live, movies, series, favKeys) {
        (live + movies + series).filter { favKeys.contains("${it.kind.name}:${it.id}") }
    }
    Column(Modifier.fillMaxSize()) {
        Text("Favoritos", color = MainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (favs.isEmpty()) EmptyBox("Aún no tienes favoritos.\nMárcalos con ☆ en canales, películas o series.")
        else PosterGrid(model, favs, Modifier.weight(1f).fillMaxWidth())
    }
}

// ───────────────────────── Detalle ─────────────────────────

@Composable
fun DetailScreen(model: AppModel, wide: Boolean) {
    val e = model.detail
    if (e == null) return
    val info = model.info
    val isSeries = e.kind == Kind.SERIES && e.url.isBlank()
    val seasons = remember(model.episodes) { model.episodes.map { it.season }.distinct().sorted() }
    // La temporada elegida vive en el modelo: al salir de un episodio se vuelve a la misma temporada.
    val season = if (seasons.contains(model.detailSeason)) model.detailSeason else (seasons.firstOrNull() ?: 1)
    val eps = remember(model.episodes, season) { model.episodes.filter { it.season == season } }
    val watch = model.watchFor(e)
    val playFocus = remember { FocusRequester() }
    val epFocus = remember { FocusRequester() }

    LaunchedEffect(e) {
        if (!isSeries) {
            delay(250)
            runCatching { playFocus.requestFocus() }
        }
    }
    LaunchedEffect(model.episodes) {
        if (isSeries && model.episodes.isNotEmpty()) {
            delay(250)
            runCatching { if (watch != null && watch.episodeId.isNotBlank()) playFocus.requestFocus() else epFocus.requestFocus() }
        }
    }

    val plot = (info?.plot.orEmpty()).ifBlank { e.plot }
    val meta = listOf(
        (info?.year.orEmpty()).ifBlank { e.year },
        info?.duration.orEmpty(),
        ratingText((info?.rating.orEmpty()).ifBlank { e.rating }),
        info?.genre.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString("  ·  ")

    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PosterImage(e.image, e.name, Modifier.width(200.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
            DetailList(model, e, meta, plot, isSeries, seasons, season, eps, watch, playFocus, epFocus, false, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        DetailList(model, e, meta, plot, isSeries, seasons, season, eps, watch, playFocus, epFocus, true, Modifier.fillMaxSize().padding(12.dp))
    }
}

@Composable
private fun DetailList(
    model: AppModel,
    e: Entry,
    meta: String,
    plot: String,
    isSeries: Boolean,
    seasons: List<Int>,
    season: Int,
    eps: List<Episode>,
    watch: Watch?,
    playFocus: FocusRequester,
    epFocus: FocusRequester,
    posterHeader: Boolean,
    modifier: Modifier,
) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Btn("← Volver", { model.closeDetail() }) }
        if (posterHeader) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosterImage(e.image, e.name, Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        if (meta.isNotBlank()) Text(meta, color = SoftText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        } else {
            item {
                Column {
                    Text(e.name, color = MainText, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    if (meta.isNotBlank()) Text(meta, color = SoftText, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isSeries) {
                    if (watch != null && watch.episodeId.isNotBlank()) {
                        Btn(
                            "▶ Continuar T${watch.season} · E${watch.number}",
                            {
                                val ep = model.episodes.firstOrNull { it.id == watch.episodeId }
                                if (ep != null) model.playEpisode(e, ep, maxOf(0L, watch.positionMs - 5_000L))
                            },
                            primary = true, focusRequester = playFocus,
                        )
                    }
                } else if (watch != null && watch.positionMs > 30_000L) {
                    Btn(
                        "▶ Continuar desde ${fmtTime(watch.positionMs)}",
                        { model.playMovie(e, maxOf(0L, watch.positionMs - 5_000L)) },
                        primary = true, focusRequester = playFocus,
                    )
                    Btn("↺ Desde el inicio", { model.playMovie(e) })
                } else {
                    Btn("▶ Reproducir", { model.playMovie(e) }, primary = true, focusRequester = playFocus)
                }
                Btn(if (model.isFav(e)) "★ En favoritos" else "☆ Añadir a favoritos", { model.toggleFav(e) })
            }
        }
        if (plot.isNotBlank()) {
            item { Text(plot, color = SoftText, fontSize = 13.sp, lineHeight = 19.sp) }
        } else if (model.infoLoading) {
            item { Text("Cargando información…", color = Muted, fontSize = 12.sp) }
        }
        if (isSeries) {
            if (seasons.isNotEmpty()) {
                item {
                    Picker("Temporada $season", seasons.map { "Temporada $it" }, Modifier.width(220.dp)) { i ->
                        model.detailSeason = seasons[i]
                    }
                }
            } else if (model.infoLoading) {
                item { Text("Cargando temporadas…", color = Muted, fontSize = 12.sp) }
            }
            itemsIndexed(eps) { i, ep ->
                val resuming = watch != null && watch.episodeId == ep.id
                Focusable(
                    onClick = { model.playEpisode(e, ep) },
                    modifier = Modifier.fillMaxWidth(),
                    selected = resuming,
                    focusRequester = if (i == 0) epFocus else null,
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${ep.number}", color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(36.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ep.title, color = MainText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ep.plot.isNotBlank()) Text(ep.plot, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (resuming) Text("Por aquí ibas", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        else if (ep.duration.isNotBlank()) Text(ep.duration, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        item { StatusLine(model) }
    }
}
